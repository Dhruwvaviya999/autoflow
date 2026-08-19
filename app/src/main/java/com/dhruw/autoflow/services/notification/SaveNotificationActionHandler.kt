package com.dhruw.autoflow.services.notification

import com.dhruw.autoflow.automation.engine.ActionContext
import com.dhruw.autoflow.automation.engine.ActionExecutionException
import com.dhruw.autoflow.automation.engine.ActionHandler
import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.NotificationRecord
import com.dhruw.autoflow.data.repository.NotificationRecordRepository
import java.util.UUID

/**
 * Persists the triggering notification locally — only when the user
 * explicitly added a SaveNotificationAction to the automation. Requires a
 * notification-triggered run; anything else fails with a clear message.
 */
class SaveNotificationActionHandler(
    private val repository: NotificationRecordRepository,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) : ActionHandler {

    override fun canHandle(action: Action): Boolean = action is Action.SaveNotificationAction

    override suspend fun execute(action: Action, context: ActionContext) {
        val event = context.notificationEvent
            ?: throw ActionExecutionException(
                "Save notification only works with a notification trigger"
            )
        repository.save(
            NotificationRecord(
                id = newId(),
                packageName = event.packageName,
                appName = event.appName,
                title = event.title,
                text = event.text,
                timestamp = event.timestamp,
                automationId = context.automationId.orEmpty()
            )
        )
        // Execution log: app name only, no notification content.
        context.log("Saved notification from ${event.appName}")
    }
}
