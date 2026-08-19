package com.dhruw.autoflow.services.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.dhruw.autoflow.AutoFlowApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Thin adapter between Android's notification stream and AutoFlow. It only
 * extracts fields, maps them to a domain NotificationEvent, and hands the
 * event to the AutomationEventDispatcher on a background coroutine — no
 * automation logic lives here, and nothing heavy runs on the binder thread.
 *
 * Privacy: notification content is processed in-process and never logged;
 * log lines carry lifecycle state only. Everything stays on the device.
 */
class AutoFlowNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // Android controls reconnection; nothing to do beyond noting it.
        Log.i(TAG, "Notification listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val statusBarNotification = sbn ?: return
        // Never react to AutoFlow's own notifications: an automation whose
        // action posts a notification would otherwise trigger itself forever.
        if (statusBarNotification.packageName == packageName) return
        val notification = statusBarNotification.notification ?: return

        val container = (application as AutoFlowApplication).container
        val extras = notification.extras
        val event = NotificationMapper.map(
            packageName = statusBarNotification.packageName,
            notificationKey = statusBarNotification.key
                ?: "${statusBarNotification.packageName}:${statusBarNotification.id}",
            postTime = statusBarNotification.postTime,
            title = extras?.getCharSequence(Notification.EXTRA_TITLE),
            text = extras?.getCharSequence(Notification.EXTRA_TEXT),
            subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT),
            category = notification.category,
            isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
            resolveAppName = container.installedApps::labelFor
        )

        serviceScope.launch {
            try {
                container.eventDispatcher.dispatch(event)
            } catch (e: Exception) {
                // Log the failure class only — never notification content.
                Log.w(TAG, "Dispatch failed: ${e.javaClass.simpleName}")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Deliberately not a trigger in Phase 5.
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "AutoFlowNotifListener"
    }
}
