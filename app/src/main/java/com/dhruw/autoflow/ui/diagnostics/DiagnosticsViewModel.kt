package com.dhruw.autoflow.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dhruw.autoflow.AutoFlowApplication
import com.dhruw.autoflow.core.DiagnosticEvent
import com.dhruw.autoflow.core.DiagnosticEventLog
import com.dhruw.autoflow.data.repository.AutomationRepository
import com.dhruw.autoflow.data.repository.ExecutionRepository
import com.dhruw.autoflow.data.repository.NotificationRecordRepository
import com.dhruw.autoflow.services.background.ScheduledWorkInfo
import com.dhruw.autoflow.services.background.SchedulerDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DiagnosticsUiState(
    val scheduled: List<ScheduledWorkInfo> = emptyList(),
    val automationCount: Int = 0,
    val executionCount: Int = 0,
    val notificationRecordCount: Int = 0,
    val orphanedExecutions: Int = 0,
    val schemaVersion: Int = 0
)

class DiagnosticsViewModel(
    private val automationRepository: AutomationRepository,
    private val executionRepository: ExecutionRepository,
    private val notificationRecordRepository: NotificationRecordRepository,
    private val schedulerDiagnostics: SchedulerDiagnostics,
    private val eventLog: DiagnosticEventLog,
    private val schemaVersion: Int
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosticsUiState(schemaVersion = schemaVersion))
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    val events: StateFlow<List<DiagnosticEvent>> = eventLog.events
    val recording: StateFlow<Boolean> = eventLog.recording

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val automations = automationRepository.getAll()
            val executions = executionRepository.executions.value
            val automationIds = automations.map { it.id }.toSet()

            _uiState.value = DiagnosticsUiState(
                scheduled = schedulerDiagnostics.inspect(automations),
                automationCount = automations.size,
                executionCount = executions.size,
                notificationRecordCount = notificationRecordRepository.getAll().size,
                // History is kept when an automation is deleted, by design.
                orphanedExecutions = executions.count { it.automationId !in automationIds },
                schemaVersion = schemaVersion
            )
        }
    }

    fun setRecording(enabled: Boolean) = eventLog.setRecording(enabled)

    fun clearEvents() = eventLog.clear()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as AutoFlowApplication).container
                DiagnosticsViewModel(
                    automationRepository = container.automationRepository,
                    executionRepository = container.executionRepository,
                    notificationRecordRepository = container.notificationRecordRepository,
                    schedulerDiagnostics = container.schedulerDiagnostics,
                    eventLog = container.diagnosticEventLog,
                    schemaVersion = container.databaseSchemaVersion
                )
            }
        }
    }
}
