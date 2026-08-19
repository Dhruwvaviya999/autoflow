package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.Action

/** Thrown by handlers for expected, user-explainable failures. */
class ActionExecutionException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Passed to handlers; [log] lines end up in the execution's history entry.
 * [fileEvent] is set when the run was started by a file trigger,
 * [notificationEvent] when it was started by a notification trigger.
 * [automationId] identifies the automation being run (used by handlers that
 * persist per-automation data).
 */
class ActionContext(
    val log: (String) -> Unit,
    val fileEvent: com.dhruw.autoflow.automation.model.TriggerPayload.FileEvent? = null,
    val notificationEvent: com.dhruw.autoflow.automation.model.TriggerPayload.NotificationEvent? = null,
    val automationId: String? = null
)

/**
 * Executes one kind of [Action]. Platform-dependent handlers (notifications,
 * files, HTTP) live in the services layer; pure handlers live next to the
 * engine. The engine picks the first handler whose [canHandle] returns true.
 */
interface ActionHandler {
    fun canHandle(action: Action): Boolean
    suspend fun execute(action: Action, context: ActionContext)
}
