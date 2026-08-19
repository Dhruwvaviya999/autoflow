package com.dhruw.autoflow.automation.model

/**
 * Data the trigger hands to a run. Conditions and actions read it through
 * the engine; triggers without extra data (manual, time) pass null.
 */
sealed interface TriggerPayload {

    /**
     * A file detected by a FileTrigger. [uri] is an opaque storage URI
     * (Android content URI) — never assumed to be a filesystem path.
     */
    data class FileEvent(
        val uri: String,
        val name: String,
        val extension: String,
        val sizeBytes: Long,
        val detectedAt: Long
    ) : TriggerPayload

    /**
     * A notification observed by the notification listener. Contains only
     * what Android exposes through NotificationListenerService — apps can
     * hide, shorten, or group their notification content, so any field except
     * [packageName] and [notificationKey] may be empty. [appName] falls back
     * to the package name when the label cannot be resolved. [category] is
     * Android's Notification.category or empty. [isGroupSummary] marks group
     * summary notifications, whose text often repeats already-delivered
     * children. [isOngoing] marks ongoing status notifications (timers,
     * progress, media playback), which update continuously rather than
     * signalling discrete events.
     */
    data class NotificationEvent(
        val packageName: String,
        val appName: String,
        val title: String,
        val text: String,
        val subText: String,
        val timestamp: Long,
        val notificationKey: String,
        val category: String,
        val isGroupSummary: Boolean = false,
        val isOngoing: Boolean = false
    ) : TriggerPayload
}
