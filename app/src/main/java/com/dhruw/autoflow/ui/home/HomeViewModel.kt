package com.dhruw.autoflow.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dhruw.autoflow.AutoFlowApplication
import com.dhruw.autoflow.automation.engine.AutomationHealthCalculator
import com.dhruw.autoflow.automation.engine.HealthStatus
import com.dhruw.autoflow.automation.model.Execution
import com.dhruw.autoflow.automation.model.ExecutionStatus
import com.dhruw.autoflow.core.utils.startOfTodayMillis
import com.dhruw.autoflow.data.repository.AutomationRepository
import com.dhruw.autoflow.data.repository.ExecutionRepository
import com.dhruw.autoflow.services.CapabilityStatusProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val activeCount: Int = 0,
    val totalCount: Int = 0,
    val runsToday: Int = 0,
    val healthyCount: Int = 0,
    val needsAttentionCount: Int = 0,
    val disabledCount: Int = 0,
    val recentExecutions: List<Execution> = emptyList()
)

class HomeViewModel(
    automationRepository: AutomationRepository,
    executionRepository: ExecutionRepository,
    private val healthCalculator: AutomationHealthCalculator,
    private val capabilities: CapabilityStatusProvider
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> =
        combine(
            automationRepository.automations,
            executionRepository.executions
        ) { automations, executions ->
            val startOfToday = startOfTodayMillis()
            val granted = capabilities.granted()
            val statuses = automations.map { automation ->
                healthCalculator.calculate(
                    automation = automation,
                    recentExecutions = executions.filter { it.automationId == automation.id },
                    grantedCapabilities = granted
                ).status
            }
            HomeUiState(
                activeCount = automations.count { it.enabled },
                totalCount = automations.size,
                runsToday = executions.count {
                    it.startedAt >= startOfToday && it.status != ExecutionStatus.RUNNING
                },
                healthyCount = statuses.count { it == HealthStatus.HEALTHY },
                needsAttentionCount = statuses.count {
                    it == HealthStatus.NEEDS_PERMISSION ||
                        it == HealthStatus.CONFIGURATION_ISSUE ||
                        it == HealthStatus.FAILING
                },
                disabledCount = statuses.count { it == HealthStatus.DISABLED },
                recentExecutions = executions.take(3)
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as AutoFlowApplication).container
                HomeViewModel(
                    automationRepository = container.automationRepository,
                    executionRepository = container.executionRepository,
                    healthCalculator = container.healthCalculator,
                    capabilities = container.capabilityStatusProvider
                )
            }
        }
    }
}
