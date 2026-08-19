package com.dhruw.autoflow.automation.model

/**
 * A notification stored locally by an explicit [Action.SaveNotificationAction].
 * Nothing is saved unless the user configured that action; the store keeps
 * only the newest records (see RoomNotificationRecordRepository).
 */
data class NotificationRecord(
    val id: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val automationId: String
)
