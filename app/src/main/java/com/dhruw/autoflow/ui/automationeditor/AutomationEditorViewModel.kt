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
import com.dhruw.autoflow.automation.model.Action
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
    val isEditing: Boolean = false
) {
    val canSave: Boolean
        get() = name.isNotBlank() && trigger != null && actions.isNotEmpty()
}

class AutomationEditorViewModel(
    private val repository: AutomationRepository,
    private val scheduler: AutomationScheduler,
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
                        isEditing = true
                    )
                }
            }
        }
    }

    fun setName(name: String) = _uiState.update { it.copy(name = name) }

    fun setDescription(description: String) = _uiState.update { it.copy(description = description) }

    fun setEnabled(enabled: Boolean) = _uiState.update { it.copy(enabled = enabled) }

    fun setTrigger(trigger: Trigger?) = _uiState.update { it.copy(trigger = trigger) }

    fun addCondition(condition: Condition) = _uiState.update { state ->
        if (condition in state.conditions) state
        else state.copy(conditions = state.conditions + condition)
    }

    fun removeCondition(index: Int) = _uiState.update { state ->
        state.copy(conditions = state.conditions.filterIndexed { i, _ -> i != index })
    }

    fun addAction(action: Action) = _uiState.update { it.copy(actions = it.actions + action) }

    fun updateAction(index: Int, action: Action) = _uiState.update { state ->
        state.copy(actions = state.actions.mapIndexed { i, a -> if (i == index) action else a })
    }

    fun removeAction(index: Int) = _uiState.update { state ->
        state.copy(actions = state.actions.filterIndexed { i, _ -> i != index })
    }

    fun save() {
        val state = _uiState.value
        val trigger = state.trigger
        if (!state.canSave || trigger == null) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val automation = Automation(
                id = original?.id ?: UUID.randomUUID().toString(),
                name = state.name.trim(),
                description = state.description.trim(),
                enabled = state.enabled,
                trigger = trigger,
                conditions = state.conditions,
                actions = state.actions,
                createdAt = original?.createdAt ?: now,
                updatedAt = now,
                lastRunAt = original?.lastRunAt
            )
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
                    savedStateHandle = createSavedStateHandle()
                )
            }
        }
    }
}
