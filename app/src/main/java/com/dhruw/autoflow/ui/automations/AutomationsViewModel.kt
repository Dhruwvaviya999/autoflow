package com.dhruw.autoflow.ui.automations

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dhruw.autoflow.AutoFlowApplication
import com.dhruw.autoflow.automation.engine.AutomationHealth
import com.dhruw.autoflow.automation.engine.AutomationHealthCalculator
import com.dhruw.autoflow.automation.engine.AutomationRunner
import com.dhruw.autoflow.automation.engine.AutomationScheduler
import com.dhruw.autoflow.automation.engine.HealthStatus
import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.automation.model.Execution
import com.dhruw.autoflow.automation.model.ExecutionStatus
import com.dhruw.autoflow.data.repository.AutomationRepository
import com.dhruw.autoflow.data.repository.ExecutionRepository
import com.dhruw.autoflow.data.transfer.WorkflowTransferService
import com.dhruw.autoflow.services.CapabilityStatusProvider
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which automations the list shows. */
enum class AutomationFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    DISABLED("Disabled"),
    NEEDS_ATTENTION("Needs attention")
}

/** One row: the automation plus its computed health. */
data class AutomationListItem(
    val automation: Automation,
    val health: AutomationHealth
)

data class AutomationsUiState(
    val items: List<AutomationListItem> = emptyList(),
    val runningIds: Set<String> = emptySet(),
    val query: String = "",
    val filter: AutomationFilter = AutomationFilter.ALL,
    val totalCount: Int = 0
) {
    val activeCount: Int get() = items.count { it.automation.enabled }
    val needsAttentionCount: Int
        get() = items.count {
            it.health.status == HealthStatus.NEEDS_PERMISSION ||
                it.health.status == HealthStatus.CONFIGURATION_ISSUE ||
                it.health.status == HealthStatus.FAILING
        }
    val isFiltered: Boolean get() = query.isNotBlank() || filter != AutomationFilter.ALL
}

class AutomationsViewModel(
    private val repository: AutomationRepository,
    private val executionRepository: ExecutionRepository,
    private val runner: AutomationRunner,
    private val scheduler: AutomationScheduler,
    private val healthCalculator: AutomationHealthCalculator,
    private val capabilities: CapabilityStatusProvider,
    private val transferService: WorkflowTransferService
) : ViewModel() {

    private val runningIds = MutableStateFlow<Set<String>>(emptySet())
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(AutomationFilter.ALL)

    /**
     * Health is recomputed whenever automations or history change. Capability
     * state is read at that moment rather than cached, so revoking a
     * permission is reflected the next time the list recomposes.
     */
    val uiState: StateFlow<AutomationsUiState> =
        combine(
            repository.automations,
            executionRepository.executions,
            runningIds,
            query,
            filter
        ) { automations, executions, running, currentQuery, currentFilter ->
            val granted = capabilities.granted()
            val items = automations.map { automation ->
                AutomationListItem(
                    automation = automation,
                    health = healthCalculator.calculate(
                        automation = automation,
                        recentExecutions = executions.filter { it.automationId == automation.id },
                        grantedCapabilities = granted
                    )
                )
            }
            AutomationsUiState(
                items = items.filter { it.matches(currentQuery, currentFilter) },
                runningIds = running,
                query = currentQuery,
                filter = currentFilter,
                totalCount = automations.size
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AutomationsUiState())

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages

    /** Share intents the screen turns into a system chooser. */
    private val _shareRequests = MutableSharedFlow<Intent>()
    val shareRequests: SharedFlow<Intent> = _shareRequests

    fun setQuery(value: String) {
        query.value = value
    }

    fun setFilter(value: AutomationFilter) {
        filter.value = value
    }

    fun run(automationId: String) {
        viewModelScope.launch {
            val automation = repository.getById(automationId) ?: return@launch
            runningIds.update { it + automationId }
            try {
                val result = runner.run(automation)
                _messages.emit(resultMessage(automation, result))
            } catch (e: CancellationException) {
                // A user-cancelled UI automation surfaces as cancellation of
                // the run only — the ViewModel scope itself stays alive.
                currentCoroutineContext().ensureActive()
                _messages.emit("\"${automation.name}\" cancelled")
            } finally {
                runningIds.update { it - automationId }
            }
        }
    }

    fun setEnabled(automationId: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(automationId, enabled)
            val automation = repository.getById(automationId) ?: return@launch
            if (enabled) scheduler.schedule(automation) else scheduler.cancel(automationId)
        }
    }

    fun delete(automationId: String) {
        viewModelScope.launch {
            repository.delete(automationId)
            scheduler.cancel(automationId)
        }
    }

    /**
     * Copies an automation with a fresh id, a "(Copy)" name and no run
     * history. The copy always starts switched off so a duplicated
     * notification or file automation cannot fire before it is reviewed.
     */
    fun duplicate(automationId: String) {
        viewModelScope.launch {
            val original = repository.getById(automationId) ?: return@launch
            val now = System.currentTimeMillis()
            val existingNames = repository.getAll().map { it.name }.toSet()
            val copy = original.copy(
                id = UUID.randomUUID().toString(),
                name = copyName(original.name, existingNames),
                enabled = false,
                createdAt = now,
                updatedAt = now,
                lastRunAt = null
            )
            repository.upsert(copy)
            _messages.emit("Copied to \"${copy.name}\"")
        }
    }

    /** Writes one automation to a file the user picked. */
    fun export(automationId: String, uri: Uri) {
        viewModelScope.launch {
            val automation = repository.getById(automationId) ?: return@launch
            transferService.export(uri, listOf(automation))
                .onSuccess { _messages.emit("Exported \"${automation.name}\"") }
                .onFailure { _messages.emit(it.message ?: "Could not export the automation") }
        }
    }

    /**
     * Prepares a workflow file and emits a share intent for it. The caller
     * starts the chooser — AutoFlow never picks a destination itself.
     */
    fun share(automationId: String) {
        viewModelScope.launch {
            val automation = repository.getById(automationId) ?: return@launch
            transferService.shareIntent(listOf(automation))
                .onSuccess { _shareRequests.emit(it) }
                .onFailure { _messages.emit(it.message ?: "Could not share the automation") }
        }
    }

    /** Writes every automation to a file the user picked. */
    fun exportAll(uri: Uri) {
        viewModelScope.launch {
            val all = repository.getAll()
            if (all.isEmpty()) {
                _messages.emit("There are no automations to export")
                return@launch
            }
            transferService.export(uri, all)
                .onSuccess { count -> _messages.emit("Exported $count automations") }
                .onFailure { _messages.emit(it.message ?: "Could not export the automations") }
        }
    }

    private fun copyName(name: String, existing: Set<String>): String {
        val base = "$name (Copy)"
        if (base !in existing) return base
        var index = 2
        while ("$name (Copy $index)" in existing) index++
        return "$name (Copy $index)"
    }

    private fun resultMessage(automation: Automation, result: Execution): String =
        when (result.status) {
            ExecutionStatus.SUCCESS -> "\"${automation.name}\" completed"
            ExecutionStatus.FAILED -> "\"${automation.name}\" failed — see History"
            ExecutionStatus.SKIPPED -> "\"${automation.name}\" skipped: ${result.message}"
            ExecutionStatus.CANCELLED -> "\"${automation.name}\" cancelled"
            ExecutionStatus.RUNNING -> "\"${automation.name}\" is running"
        }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as AutoFlowApplication).container
                AutomationsViewModel(
                    repository = container.automationRepository,
                    executionRepository = container.executionRepository,
                    runner = container.runner,
                    scheduler = container.scheduler,
                    healthCalculator = container.healthCalculator,
                    capabilities = container.capabilityStatusProvider,
                    transferService = container.workflowTransferService
                )
            }
        }
    }
}

private fun AutomationListItem.matches(query: String, filter: AutomationFilter): Boolean {
    val matchesQuery = query.isBlank() ||
        automation.name.contains(query.trim(), ignoreCase = true) ||
        automation.description.contains(query.trim(), ignoreCase = true)
    if (!matchesQuery) return false
    return when (filter) {
        AutomationFilter.ALL -> true
        AutomationFilter.ACTIVE -> automation.enabled
        AutomationFilter.DISABLED -> !automation.enabled
        AutomationFilter.NEEDS_ATTENTION -> health.status == HealthStatus.NEEDS_PERMISSION ||
            health.status == HealthStatus.CONFIGURATION_ISSUE ||
            health.status == HealthStatus.FAILING
    }
}
