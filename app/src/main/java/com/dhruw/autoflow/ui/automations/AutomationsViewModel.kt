package com.dhruw.autoflow.ui.automations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dhruw.autoflow.AutoFlowApplication
import com.dhruw.autoflow.automation.engine.AutomationRunner
import com.dhruw.autoflow.automation.engine.AutomationScheduler
import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.automation.model.Execution
import com.dhruw.autoflow.automation.model.ExecutionStatus
import com.dhruw.autoflow.data.repository.AutomationRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AutomationsUiState(
    val automations: List<Automation> = emptyList(),
    val runningIds: Set<String> = emptySet()
)

class AutomationsViewModel(
    private val repository: AutomationRepository,
    private val runner: AutomationRunner,
    private val scheduler: AutomationScheduler
) : ViewModel() {

    private val runningIds = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<AutomationsUiState> =
        combine(repository.automations, runningIds) { automations, running ->
            AutomationsUiState(automations = automations, runningIds = running)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AutomationsUiState())

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages

    fun run(automationId: String) {
        viewModelScope.launch {
            val automation = repository.getById(automationId) ?: return@launch
            runningIds.update { it + automationId }
            try {
                val result = runner.run(automation)
                _messages.emit(resultMessage(automation, result))
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
                    runner = container.runner,
                    scheduler = container.scheduler
                )
            }
        }
    }
}
