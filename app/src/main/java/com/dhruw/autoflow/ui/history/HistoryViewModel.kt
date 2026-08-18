package com.dhruw.autoflow.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dhruw.autoflow.AutoFlowApplication
import com.dhruw.autoflow.automation.model.Execution
import com.dhruw.autoflow.data.repository.ExecutionRepository
import kotlinx.coroutines.flow.StateFlow

class HistoryViewModel(
    executionRepository: ExecutionRepository
) : ViewModel() {

    val executions: StateFlow<List<Execution>> = executionRepository.executions

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as AutoFlowApplication).container
                HistoryViewModel(container.executionRepository)
            }
        }
    }
}
