package com.dhruw.autoflow.services.accessibility

import com.dhruw.autoflow.automation.engine.ActionContext
import com.dhruw.autoflow.automation.engine.ActionExecutionException
import com.dhruw.autoflow.automation.engine.ActionHandler
import com.dhruw.autoflow.automation.model.Action
import kotlin.coroutines.cancellation.CancellationException

/**
 * Executes [Action.UiAutomationAction] through the [UiAutomationSessionManager].
 * Step-by-step progress lands in the execution log (non-sensitive labels
 * only). Failure surfaces as a normal action failure; a user cancellation
 * surfaces as a cancelled execution via the engine's cancellation path.
 */
class UiAutomationActionHandler(
    private val sessionManager: UiAutomationSessionManager
) : ActionHandler {

    override fun canHandle(action: Action): Boolean = action is Action.UiAutomationAction

    override suspend fun execute(action: Action, context: ActionContext) {
        val uiAction = action as Action.UiAutomationAction

        when (val result = sessionManager.run(
            action = uiAction,
            payload = context.payload,
            automationId = context.automationId,
            onLog = context.log
        )) {
            UiAutomationSessionManager.RunResult.Busy -> {
                context.log("Skipped: UI automation already running")
                throw ActionExecutionException(
                    "UI automation already running — only one can run at a time"
                )
            }

            UiAutomationSessionManager.RunResult.ServiceUnavailable ->
                throw ActionExecutionException(
                    "Accessibility is not enabled for AutoFlow. " +
                        "Enable it in Settings → Permission center → Accessibility."
                )

            is UiAutomationSessionManager.RunResult.Completed -> {
                val outcome = result.outcome
                if (outcome.cancelled) {
                    // The engine converts this into a CANCELLED execution.
                    throw CancellationException("UI automation cancelled")
                }
                if (!outcome.success) {
                    val lastResult = outcome.results.lastOrNull()
                    throw ActionExecutionException(
                        lastResult?.detail?.ifBlank { null } ?: "UI automation failed"
                    )
                }
            }
        }
    }
}
