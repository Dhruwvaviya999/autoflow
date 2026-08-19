package com.dhruw.autoflow.services.notification

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Answers "does AutoFlow currently have notification access?" and builds
 * the intent that takes the user to Android's notification-listener
 * settings. Access is never requested automatically — the Permission
 * Center's Grant button is the only entry point.
 */
class NotificationAccessManager(private val context: Context) {

    enum class Status { GRANTED, NOT_GRANTED, UNAVAILABLE }

    fun status(): Status = try {
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(context)
        if (context.packageName in enabled) Status.GRANTED else Status.NOT_GRANTED
    } catch (e: Exception) {
        // Some devices restrict reading the secure setting; report honestly.
        Status.UNAVAILABLE
    }

    fun isAccessGranted(): Boolean = status() == Status.GRANTED

    /**
     * Android 11+ has a per-app detail screen; older versions (and OEMs that
     * don't ship the detail screen) get the full listener list.
     */
    fun settingsIntent(): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(context, AutoFlowNotificationListenerService::class.java)
                        .flattenToString()
                )
            if (detail.resolveActivity(context.packageManager) != null) return detail
        }
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }
}
