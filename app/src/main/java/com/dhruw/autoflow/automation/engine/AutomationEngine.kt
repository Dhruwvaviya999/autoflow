package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.automation.model.Execution
import com.dhruw.autoflow.automation.model.ExecutionStatus
import com.dhruw.autoflow.automation.model.TriggerPayload
import com.dhruw.autoflow.automation.model.displayName
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Executes one automation: validate → evaluate conditions → run actions
 * sequentially. Pure Kotlin — no Android, no storage. Progress (including
 * the final state) is reported through [onUpdate]; the final [Execution]
 * is also returned.
 */
class AutomationEngine(
    private val handlers: List<ActionHandler>,
    private val conditionEvaluator: ConditionEvaluator = ConditionEvaluator(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {

    suspend fun execute(
        automation: Automation,
        payload: TriggerPayload? = null,
        onUpdate: suspend (Execution) -> Unit = {}
    ): Execution {
        val logs = mutableListOf<String>()
        var execution = Execution(
            id = newId(),
            automationId = automation.id,
            automationName = automation.name,
            startedAt = clock(),
            status = ExecutionStatus.RUNNING,
            totalActions = automation.actions.size
        )
        onUpdate(execution)

        suspend fun finish(
            status: ExecutionStatus,
            message: String? = null,
            error: String? = null
        ): Execution {
            execution = execution.copy(
                status = status,
                completedAt = clock(),
                currentAction = null,
                message = message,
                error = error,
                logs = logs.toList()
            )
            onUpdate(execution)
            return execution
        }

        if (!automation.enabled) {
            return finish(ExecutionStatus.SKIPPED, message = "Automation is disabled")
        }
        if (automation.actions.isEmpty()) {
            return finish(
                ExecutionStatus.FAILED,
                message = "Nothing to run",
                error = "Automation has no actions"
            )
        }
        if (!conditionEvaluator.evaluateAll(automation.conditions, payload)) {
            return finish(ExecutionStatus.SKIPPED, message = "Conditions not met")
        }

        val context = ActionContext(
            log = { line -> logs += line },
            fileEvent = payload as? TriggerPayload.FileEvent
        )

        try {
            automation.actions.forEachIndexed { index, action ->
                val label = "${index + 1}. ${action.displayName}"
                execution = execution.copy(currentAction = label, logs = logs.toList())
                onUpdate(execution)
                logs += "Started $label"

                val handler = handlers.firstOrNull { it.canHandle(action) }
                    ?: throw ActionExecutionException("No handler registered for ${action.displayName}")
                handler.execute(action, context)

                logs += "Completed $label"
                execution = execution.copy(completedActions = index + 1)
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                finish(ExecutionStatus.CANCELLED, message = "Cancelled")
            }
            throw e
        } catch (e: Exception) {
            val failedAt = execution.currentAction ?: "unknown action"
            logs += "Failed at $failedAt: ${e.message}"
            return finish(
                ExecutionStatus.FAILED,
                message = "Failed at $failedAt",
                error = e.message ?: e.javaClass.simpleName
            )
        }

        return finish(
            ExecutionStatus.SUCCESS,
            message = "${execution.completedActions} of ${execution.totalActions} actions completed"
        )
    }
}
