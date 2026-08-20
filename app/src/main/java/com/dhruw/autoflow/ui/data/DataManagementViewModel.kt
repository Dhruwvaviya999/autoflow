package com.dhruw.autoflow.ui.data

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dhruw.autoflow.AutoFlowApplication
import com.dhruw.autoflow.automation.engine.AutomationScheduler
import com.dhruw.autoflow.data.repository.AutomationRepository
import com.dhruw.autoflow.data.repository.ExecutionRepository
import com.dhruw.autoflow.data.repository.NotificationRecordRepository
import com.dhruw.autoflow.data.transfer.ImportPreview
import com.dhruw.autoflow.data.transfer.TransferKind
import com.dhruw.autoflow.data.transfer.WorkflowTransferService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DataUiState(
    val automationCount: Int = 0,
    val executionCount: Int = 0,
    val notificationRecordCount: Int = 0,
    /** Non-null while the import review sheet is open. */
    val pendingImport: ImportPreview? = null,
    val busy: Boolean = false
)

/**
 * Backs the Data screen: local backup, restore, import and the destructive
 * clear operations. Every write goes through the same transfer service the
 * automations list uses, so import always validates and always lands
 * switched off.
 */
class DataManagementViewModel(
    private val automationRepository: AutomationRepository,
    private val executionRepository: ExecutionRepository,
    private val notificationRecordRepository: NotificationRecordRepository,
    private val transferService: WorkflowTransferService,
    private val scheduler: AutomationScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataUiState())
    val uiState: StateFlow<DataUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages

    init {
        viewModelScope.launch {
            automationRepository.automations.collect { automations ->
                _uiState.value = _uiState.value.copy(automationCount = automations.size)
            }
        }
        viewModelScope.launch {
            executionRepository.executions.collect { executions ->
                _uiState.value = _uiState.value.copy(executionCount = executions.size)
            }
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                notificationRecordCount = notificationRecordRepository.getAll().size
            )
        }
    }

    fun backup(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true)
            val automations = automationRepository.getAll()
            if (automations.isEmpty()) {
                _messages.emit("There is nothing to back up yet")
            } else {
                transferService.export(uri, automations, TransferKind.BACKUP)
                    .onSuccess { count -> _messages.emit("Backed up $count automations") }
                    .onFailure { _messages.emit(it.message ?: "Could not write the backup") }
            }
            _uiState.value = _uiState.value.copy(busy = false)
        }
    }

    fun openFile(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true)
            transferService.preview(uri)
                .onSuccess { preview ->
                    _uiState.value = _uiState.value.copy(pendingImport = preview)
                }
                .onFailure { _messages.emit(it.message ?: "Could not read that file") }
            _uiState.value = _uiState.value.copy(busy = false)
        }
    }

    fun confirmImport() {
        val preview = _uiState.value.pendingImport ?: return
        viewModelScope.launch {
            val stored = transferService.import(preview.candidates)
            _uiState.value = _uiState.value.copy(pendingImport = null)
            _messages.emit(
                if (stored == 0) {
                    "Nothing could be imported from that file"
                } else {
                    "Imported $stored workflows — review and switch them on"
                }
            )
        }
    }

    fun cancelImport() {
        _uiState.value = _uiState.value.copy(pendingImport = null)
    }

    fun clearHistory() {
        viewModelScope.launch {
            executionRepository.clear()
            _messages.emit("Execution history cleared")
        }
    }

    fun clearNotificationRecords() {
        viewModelScope.launch {
            notificationRecordRepository.clear()
            _uiState.value = _uiState.value.copy(notificationRecordCount = 0)
            _messages.emit("Saved notifications cleared")
        }
    }

    /** Removes every automation and cancels their scheduled work. */
    fun resetAutomations() {
        viewModelScope.launch {
            automationRepository.getAll().forEach { automation ->
                scheduler.cancel(automation.id)
                automationRepository.delete(automation.id)
            }
            _messages.emit("All automations deleted")
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as AutoFlowApplication).container
                DataManagementViewModel(
                    automationRepository = container.automationRepository,
                    executionRepository = container.executionRepository,
                    notificationRecordRepository = container.notificationRecordRepository,
                    transferService = container.workflowTransferService,
                    scheduler = container.scheduler
                )
            }
        }
    }
}
