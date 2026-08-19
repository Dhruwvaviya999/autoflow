package com.dhruw.autoflow.services.notification

import com.dhruw.autoflow.automation.model.TriggerPayload

/**
 * Converts the fields the listener service pulls out of an Android
 * StatusBarNotification into the domain's immutable
 * [TriggerPayload.NotificationEvent]. Deliberately free of Android types so
 * the mapping rules (null → empty, app-label fallback) are unit-testable on
 * the JVM; the service is the only caller that touches Android objects.
 */
object NotificationMapper {

    fun map(
        packageName: String,
        notificationKey: String,
        postTime: Long,
        title: CharSequence?,
        text: CharSequence?,
        subText: CharSequence?,
        category: String?,
        isGroupSummary: Boolean,
        isOngoing: Boolean,
        resolveAppName: (String) -> String? = { null }
    ): TriggerPayload.NotificationEvent = TriggerPayload.NotificationEvent(
        packageName = packageName,
        appName = resolveAppName(packageName)?.takeIf { it.isNotBlank() } ?: packageName,
        title = title?.toString().orEmpty(),
        text = text?.toString().orEmpty(),
        subText = subText?.toString().orEmpty(),
        timestamp = postTime,
        notificationKey = notificationKey,
        category = category.orEmpty(),
        isGroupSummary = isGroupSummary,
        isOngoing = isOngoing
    )
}
