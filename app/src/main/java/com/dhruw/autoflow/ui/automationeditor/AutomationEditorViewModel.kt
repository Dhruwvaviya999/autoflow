package com.dhruw.autoflow.ui.automationeditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dhruw.autoflow.AutoFlowApplication
import com.dhruw.autoflow.automation.engine.AutomationScheduler
import com.dhruw.autoflow.automation.engine.WorkflowValidator
import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.requiredCapabilities
import com.dhruw.autoflow.services.CapabilityStatusProvider
import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.automation.model.Condition
import com.dhruw.autoflow.automation.model.Trigger
import com.dhruw.autoflow.data.repository.AutomationRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorUiState(
    val name: String = "",
    val description: String = "",
    val trigger: Trigger? = null,
    val conditions: List<Condition> = emptyList(),
    val actions: List<Action> = emptyList(),
    val enabled: Boolean = true,
    val isEditing: Boolean = false,
    val disableAfterFailures: Int? = null,
    /** Validation problems that block saving, refreshed as the user edits. */
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    /** Non-null while the Test automation result is on screen. */
    val testReport: TestReport? = null
) {
    val canSave: Boolean
        get() = name.isNotBlank() && trigger != null && actions.isNotEmpty() && errors.isEmpty()
}

class AutomationEditorViewModel(
    private val repository: AutomationRepository,
    private val scheduler: AutomationScheduler,
    private val validator: WorkflowValidator,
    private val capabilities: CapabilityStatusProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val editingId: String? = savedStateHandle["automationId"]
    private var original: Automation? = null

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    /** Emits once after a successful save; the screen navigates back. */
    private val _saved = MutableSharedFlow<Unit>()
    val saved: SharedFlow<Unit> = _saved

    init {
        if (editingId != null) {
            viewModelScope.launch {
                repository.getById(editingId)?.let { automation ->
                    original = automation
                    _uiState.value = EditorUiState(
                        name = automation.name,
                        description = automation.description,
                        trigger = automation.trigger,
                        conditions = automation.conditions,
                        actions = automation.actions,
                        enabled = automation.enabled,
                        isEditing = true,
                        disableAfterFailures = automation.disableAfterFailures
                    ).withValidation()
                }
            }
        }
    }

    fun setName(name: String) = _uiState.update { it.copy(name = name).withValidation() }

    fun setDescription(description: String) = _uiState.update { it.copy(description = description) }

    fun setEnabled(enabled: Boolean) = _uiState.update { it.copy(enabled = enabled) }

    fun setTrigger(trigger: Trigger?) = _uiState.update { it.copy(trigger = trigger).withValidation() }

    fun setDisableAfterFailures(value: Int?) = _uiState.update {
        it.copy(disableAfterFailures = value)
    }

    fun addCondition(condition: Condition) = _uiState.update { state ->
        if (condition in state.conditions) state
        else state.copy(conditions = state.conditions + condition).withValidation()
    }

    fun removeCondition(index: Int) = _uiState.update { state ->
        state.copy(conditions = state.conditions.filterIndexed { i, _ -> i != index })
            .withValidation()
    }

    fun addAction(action: Action) = _uiState.update {
        it.copy(actions = it.actions + action).withValidation()
    }

    fun updateAction(index: Int, action: Action) = _uiState.update { state ->
        state.copy(actions = state.actions.mapIndexed { i, a -> if (i == index) action else a })
            .withValidation()
    }

    fun removeAction(index: Int) = _uiState.update { state ->
        state.copy(actions = state.actions.filterIndexed { i, _ -> i != index }).withValidation()
    }

    fun moveAction(index: Int, delta: Int) = _uiState.update { state ->
        val target = index + delta
        if (index !in state.actions.indices || target !in state.actions.indices) {
            state
        } else {
            state.copy(
                actions = state.actions.toMutableList().apply {
                    add(target, removeAt(index))
                }
            )
        }
    }

    /**
     * Switches a step off (wrapping it) or back on (unwrapping). Group
     * markers are not executable, so the screen does not offer the toggle
     * for them; a wrapped group would still be skipped harmlessly.
     */
    fun toggleActionEnabled(index: Int) = _uiState.update { state ->
        state.copy(
            actions = state.actions.mapIndexed { i, action ->
                if (i != index) {
                    action
                } else {
                    when (action) {
                        is Action.DisabledAction -> action.wrapped
                        else -> Action.DisabledAction(action)
                    }
                }
            }
        ).withValidation()
    }

    /**
     * Dry run: validates the workflow and checks the permissions it needs
     * against what Android currently grants. Executes nothing.
     */
    fun test() {
        val state = _uiState.value
        val automation = state.toAutomation(id = original?.id ?: "draft", now = 0L) ?: return
        val report = validator.validate(automation)
        val granted = capabilities.granted()
        _uiState.update {
            it.copy(
                testReport = TestReport(
                    issues = report.issues,
                    missingCapabilities = automation.requiredCapabilities - granted,
                    stepCount = state.actions.size
                )
            )
        }
    }

    fun dismissTestReport() = _uiState.update { it.copy(testReport = null) }

    private fun EditorUiState.withValidation(): EditorUiState {
        val automation = toAutomation(id = original?.id ?: "draft", now = 0L)
            ?: return copy(errors = emptyList(), warnings = emptyList())
        val report = validator.validate(automation)
        return copy(
            errors = report.errors.map { it.message },
            warnings = report.warnings.map { it.message }
        )
    }

    private fun EditorUiState.toAutomation(id: String, now: Long): Automation? {
        val currentTrigger = trigger ?: return null
        return Automation(
            id = id,
            name = name.trim(),
            description = description.trim(),
            enabled = enabled,
            trigger = currentTrigger,
            conditions = conditions,
            actions = actions,
            createdAt = original?.createdAt ?: now,
            updatedAt = now,
            lastRunAt = original?.lastRunAt,
            disableAfterFailures = disableAfterFailures
        )
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val automation = state.toAutomation(
                id = original?.id ?: UUID.randomUUID().toString(),
                now = now
            ) ?: return@launch
            repository.upsert(automation)
            if (automation.enabled) scheduler.schedule(automation) else scheduler.cancel(automation.id)
            _saved.emit(Unit)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as AutoFlowApplication).container
                AutomationEditorViewModel(
                    repository = container.automationRepository,
                    scheduler = container.scheduler,
                    validator = container.workflowValidator,
                    capabilities = container.capabilityStatusProvider,
                    savedStateHandle = createSavedStateHandle()
                )
            }
        }
    }
}
