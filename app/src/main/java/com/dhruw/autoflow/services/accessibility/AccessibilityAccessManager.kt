package com.dhruw.autoflow.services.accessibility

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Answers "is AutoFlow's accessibility service enabled?" and builds the
 * intent that takes the user to Android's Accessibility settings. Following
 * the Permission Center rules, access is never requested automatically, the
 * settings screen is only opened from an explicit user tap, and the user is
 * never prompted repeatedly.
 */
class AccessibilityAccessManager(private val context: Context) {

    enum class Status { ENABLED, DISABLED, UNAVAILABLE }

    /**
     * Reads the system's enabled-services setting. The service being listed
     * there is the source of truth; [isServiceConnected] additionally tells
     * whether the system has actually bound it right now.
     */
    fun status(): Status = try {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val component = ComponentName(context, AutoFlowAccessibilityService::class.java)
        val listed = enabled.split(':').any { entry ->
            val parsed = ComponentName.unflattenFromString(entry.trim())
            parsed == component
        }
        if (listed) Status.ENABLED else Status.DISABLED
    } catch (e: Exception) {
        // Some devices restrict reading the secure setting; report honestly.
        Status.UNAVAILABLE
    }

    fun isEnabled(): Boolean = status() == Status.ENABLED

    /** True when the system currently has the service bound and connected. */
    fun isServiceConnected(): Boolean = AutoFlowAccessibilityService.instance != null

    fun settingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
}
