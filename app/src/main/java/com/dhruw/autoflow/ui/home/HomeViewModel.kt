package com.dhruw.autoflow.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dhruw.autoflow.AutoFlowApplication
import com.dhruw.autoflow.automation.model.Execution
import com.dhruw.autoflow.automation.model.ExecutionStatus
import com.dhruw.autoflow.core.utils.startOfTodayMillis
import com.dhruw.autoflow.data.repository.AutomationRepository
import com.dhruw.autoflow.data.repository.ExecutionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val activeCount: Int = 0,
    val totalCount: Int = 0,
    val runsToday: Int = 0,
    val recentExecutions: List<Execution> = emptyList()
)

class HomeViewModel(
    automationRepository: AutomationRepository,
    executionRepository: ExecutionRepository
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> =
        combine(
            automationRepository.automations,
            executionRepository.executions
        ) { automations, executions ->
            val startOfToday = startOfTodayMillis()
            HomeUiState(
                activeCount = automations.count { it.enabled },
                totalCount = automations.size,
                runsToday = executions.count {
                    it.startedAt >= startOfToday && it.status != ExecutionStatus.RUNNING
                },
                recentExecutions = executions.take(3)
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as AutoFlowApplication).container
                HomeViewModel(container.automationRepository, container.executionRepository)
            }
        }
    }
}
