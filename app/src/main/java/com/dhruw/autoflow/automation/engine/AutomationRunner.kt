package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.automation.model.Execution
import com.dhruw.autoflow.automation.model.ExecutionStatus
import com.dhruw.autoflow.data.repository.AutomationRepository
import com.dhruw.autoflow.data.repository.ExecutionRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Use case tying the engine to storage: runs the automation, persists every
 * execution update (including the cancelled/final state), stamps lastRun.
 */
class AutomationRunner(
    private val engine: AutomationEngine,
    private val automationRepository: AutomationRepository,
    private val executionRepository: ExecutionRepository
) {

    suspend fun run(
        automation: Automation,
        payload: com.dhruw.autoflow.automation.model.TriggerPayload? = null
    ): Execution {
        val result = engine.execute(automation, payload) { update ->
            withContext(NonCancellable) {
                executionRepository.record(update)
            }
        }
        if (result.status == ExecutionStatus.SUCCESS || result.status == ExecutionStatus.FAILED) {
            automationRepository.markRun(automation.id, result.startedAt)
        }
        return result
    }
}
