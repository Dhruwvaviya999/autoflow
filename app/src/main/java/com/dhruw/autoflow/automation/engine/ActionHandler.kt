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
    val automationId: String? = null,
    /** The run's raw trigger payload (any kind) — feeds templates. */
    val payload: com.dhruw.autoflow.automation.model.TriggerPayload? = null,
    /** Display name of the automation, for {{automation.name}}. */
    val automationName: String? = null,
    /**
     * Run-scoped variables: workflow-locals written by Set variable and
     * `result.*` outputs written by handlers (e.g. result.count,
     * result.fileName). String-valued, local to this run, never persisted
     * beyond the execution record's variable snapshot.
     */
    val variables: MutableMap<String, String> = mutableMapOf()
) {
    /** Resolve a template against this run (payload + variables + name). */
    fun resolveTemplate(template: String): TemplateResolver.Result =
        TemplateResolver.resolve(template, payload, variables, automationName)

    /** Resolve a template or throw the standard user-explainable failure. */
    fun resolveTemplateOrFail(template: String): String =
        when (val r = resolveTemplate(template)) {
            is TemplateResolver.Result.Ok -> r.text
            is TemplateResolver.Result.UnknownVariable ->
                throw ActionExecutionException("Unknown variable {{${r.variable}}}")
            is TemplateResolver.Result.Unavailable ->
                throw ActionExecutionException("Variable {{${r.variable}}} not available for this trigger")
        }
}

/**
 * Executes one kind of [Action]. Platform-dependent handlers (notifications,
 * files, HTTP) live in the services layer; pure handlers live next to the
 * engine. The engine picks the first handler whose [canHandle] returns true.
 */
interface ActionHandler {
    fun canHandle(action: Action): Boolean
    suspend fun execute(action: Action, context: ActionContext)
}
