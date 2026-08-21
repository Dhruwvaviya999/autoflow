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
 * execution update (including the cancelled/final state), stamps lastRun, and
 * applies the per-automation auto-disable policy.
 */
class AutomationRunner(
    private val engine: AutomationEngine,
    private val automationRepository: AutomationRepository,
    private val executionRepository: ExecutionRepository,
    private val healthCalculator: AutomationHealthCalculator = AutomationHealthCalculator(),
    /**
     * Told when an automation switches itself off, so the app layer can post
     * a local notification. Always local — nothing leaves the device.
     */
    private val onAutoDisabled: suspend (automation: Automation, failures: Int) -> Unit = { _, _ -> },
    /**
     * Diagnostic hook for Developer Tools. Receives event kinds and outcomes
     * only — never trigger payloads, notification text or screen content.
     */
    private val onDiagnostic: (category: String, message: String) -> Unit = { _, _ -> }
) {

    suspend fun run(
        automation: Automation,
        payload: com.dhruw.autoflow.automation.model.TriggerPayload? = null
    ): Execution {
        onDiagnostic("run", "\"${automation.name}\" started")
        val result = engine.execute(automation, payload) { update ->
            withContext(NonCancellable) {
                executionRepository.record(update)
            }
        }
        onDiagnostic("run", "\"${automation.name}\" ${result.status.name.lowercase()}")
        if (result.status == ExecutionStatus.SUCCESS || result.status == ExecutionStatus.FAILED) {
            automationRepository.markRun(automation.id, result.startedAt)
        }
        if (result.status == ExecutionStatus.FAILED) {
            withContext(NonCancellable) { applyAutoDisablePolicy(automation, result) }
        }
        return result
    }

    /**
     * Switches the automation off once it has failed [Automation.disableAfterFailures]
     * times in a row. Counting uses the same rule as the health indicator, so
     * what the user sees on the card matches what triggers the policy.
     *
     * [latest] is the run that just failed. It is prepended explicitly instead
     * of being read back from [ExecutionRepository.executions]: that flow is
     * fed by the database and has usually not emitted the new row yet when we
     * get here, which would make a threshold of 3 only fire on the 4th failure.
     */
    private suspend fun applyAutoDisablePolicy(automation: Automation, latest: Execution) {
        val threshold = automation.disableAfterFailures ?: return
        if (threshold <= 0) return

        val earlier = executionRepository.executions.value
            .filter { it.automationId == automation.id && it.id != latest.id }
        // executions is newest-first and `latest` is newer than all of them.
        val failures = healthCalculator.countConsecutiveFailures(listOf(latest) + earlier)
        if (failures < threshold) return

        automationRepository.setEnabled(automation.id, false)
        onDiagnostic("policy", "\"${automation.name}\" auto-disabled after $failures failures")
        onAutoDisabled(automation, failures)
    }
}
