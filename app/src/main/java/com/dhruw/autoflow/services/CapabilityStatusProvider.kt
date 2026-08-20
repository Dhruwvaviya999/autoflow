package com.dhruw.autoflow.services

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.dhruw.autoflow.automation.model.Capability
import com.dhruw.autoflow.services.accessibility.AccessibilityAccessManager
import com.dhruw.autoflow.services.notification.NotificationAccessManager

/**
 * Answers "which capabilities does AutoFlow actually have right now?" by
 * asking Android, never by remembering what was granted earlier. Permissions
 * can be revoked at any time (and ColorOS unbinds the accessibility service
 * on app updates), so every caller re-reads through this.
 *
 * Folder access is deliberately absent from the granted set: a persisted
 * folder permission belongs to one specific automation's chosen folder, so
 * it is checked when that automation runs rather than globally.
 */
class CapabilityStatusProvider(
    private val context: Context,
    private val notificationAccessManager: NotificationAccessManager,
    private val accessibilityAccessManager: AccessibilityAccessManager
) {

    fun granted(): Set<Capability> = buildSet {
        if (notificationAccessManager.isAccessGranted()) add(Capability.NOTIFICATION_ACCESS)
        if (accessibilityAccessManager.isEnabled()) add(Capability.ACCESSIBILITY)
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            add(Capability.POST_NOTIFICATIONS)
        }
        // Folder access is per-automation; treated as available so the health
        // indicator does not flag every file automation as broken.
        add(Capability.FILE_ACCESS)
    }

    fun isGranted(capability: Capability): Boolean = capability in granted()
}
