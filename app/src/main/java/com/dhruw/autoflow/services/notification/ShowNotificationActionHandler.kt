package com.dhruw.autoflow.services.notification

import com.dhruw.autoflow.automation.engine.ActionContext
import com.dhruw.autoflow.automation.engine.ActionExecutionException
import com.dhruw.autoflow.automation.engine.ActionHandler
import com.dhruw.autoflow.automation.model.Action

class ShowNotificationActionHandler(
    private val notifier: AutomationNotifier
) : ActionHandler {

    override fun canHandle(action: Action): Boolean =
        action is Action.ShowNotificationAction

    override suspend fun execute(action: Action, context: ActionContext) {
        action as Action.ShowNotificationAction
        if (!notifier.canNotify()) {
            throw ActionExecutionException(
                "Notifications are not allowed. Grant the notification permission in Settings."
            )
        }
        val title = context.resolveTemplateOrFail(action.title)
        val message = context.resolveTemplateOrFail(action.message)
        try {
            notifier.notify(title, message)
        } catch (e: SecurityException) {
            throw ActionExecutionException("Notification was blocked by the system", e)
        }
        context.log("Posted notification \"$title\"")
    }
}
