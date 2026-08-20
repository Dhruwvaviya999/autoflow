package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.automation.model.Execution
import com.dhruw.autoflow.automation.model.ExecutionStatus
import com.dhruw.autoflow.automation.model.TriggerPayload
import com.dhruw.autoflow.automation.model.displayName
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
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

    private val maxBranchDepth = EngineLimits.MAX_BRANCH_DEPTH

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
            fileEvent = payload as? TriggerPayload.FileEvent,
            notificationEvent = payload as? TriggerPayload.NotificationEvent,
            automationId = automation.id,
            payload = payload,
            automationName = automation.name
        )

        /**
         * Runs one action, recursing into branches. Group markers and
         * disabled steps are recorded and skipped; everything else goes
         * through the handler registry. [depth] guards against pathological
         * nesting beyond what the validator allows.
         */
        suspend fun runAction(action: Action, label: String, depth: Int) {
            if (depth > maxBranchDepth) {
                throw ActionExecutionException("Branch nesting exceeds $maxBranchDepth levels")
            }
            when (action) {
                is Action.GroupMarker -> {
                    logs += "— ${action.label.trim().ifBlank { "Group" }} —"
                }
                is Action.DisabledAction -> {
                    logs += "Skipped $label (disabled)"
                }
                is Action.BranchAction -> {
                    logs += "Started $label"
                    val passed = conditionEvaluator.evaluate(action.condition, payload)
                    val chosen = if (passed) action.thenActions else action.elseActions
                    logs += if (passed) "Condition passed — running THEN" else {
                        if (action.elseActions.isEmpty()) "Condition not met — nothing to run"
                        else "Condition not met — running ELSE"
                    }
                    chosen.forEachIndexed { i, inner ->
                        runAction(inner, "$label.${i + 1} ${inner.displayName}", depth + 1)
                    }
                    logs += "Completed $label"
                }
                else -> {
                    logs += "Started $label"
                    val handler = handlers.firstOrNull { it.canHandle(action) }
                        ?: throw ActionExecutionException("No handler registered for ${action.displayName}")
                    executeWithRetries(handler, action, context) { attempt, message ->
                        logs += "Attempt $attempt failed: $message — retrying"
                    }
                    logs += "Completed $label"
                }
            }
        }

        val runDeadline = clock() + EngineLimits.MAX_RUN_MILLIS

        try {
            automation.actions.forEachIndexed { index, action ->
                if (clock() >= runDeadline) {
                    throw ActionExecutionException(
                        "This run exceeded the ${EngineLimits.MAX_RUN_MILLIS / 60_000}-minute limit and was stopped"
                    )
                }
                val label = "${index + 1}. ${action.displayName}"
                execution = execution.copy(currentAction = label, logs = logs.toList())
                onUpdate(execution)

                runAction(action, label, depth = 0)

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

    /**
     * Runs a handler, retrying only steps [RetryPolicy] considers safe to
     * repeat. Cancellation is never retried — it means the user stopped the
     * run. The final failure propagates unchanged so the execution records
     * the real reason.
     */
    private suspend fun executeWithRetries(
        handler: ActionHandler,
        action: Action,
        context: ActionContext,
        onRetry: (attempt: Int, message: String) -> Unit
    ) {
        val retries = RetryPolicy.retriesFor(action)
        var attempt = 0
        while (true) {
            try {
                handler.execute(action, context)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt >= retries) throw e
                attempt++
                onRetry(attempt, e.message ?: e.javaClass.simpleName)
                delay(EngineLimits.RETRY_DELAY_MILLIS)
            }
        }
    }
}
