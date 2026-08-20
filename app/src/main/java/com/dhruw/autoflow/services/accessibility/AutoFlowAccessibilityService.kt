package com.dhruw.autoflow.services.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.dhruw.autoflow.AutoFlowApplication

/**
 * AutoFlow's accessibility service — a thin platform adapter, exactly as the
 * architecture requires: no workflow logic lives here. It exposes the active
 * window's tree and global actions to [AndroidUiAutomationHost], tracks the
 * foreground package for the target-package boundary, and feeds the
 * user-started element inspector. Everything else (step execution, timeouts,
 * confirmation, safety rules) happens in the engine layer.
 *
 * Performance: the config XML subscribes to only two event types, and this
 * handler does constant-time work per event unless Inspect mode is active.
 * The UI tree is never traversed here, never logged, and never persisted.
 *
 * Privacy: no accessibility content leaves the process. The only capture
 * path is the inspector (explicitly user-started, single tapped elements,
 * in-memory, password text never read).
 */
class AutoFlowAccessibilityService : AccessibilityService() {

    @Volatile
    private var lastWindowPackage: String? = null

    private val inspector: UiInspector
        get() = (application as AutoFlowApplication).container.uiInspector

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        Log.i(TAG, "Accessibility service unbound")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {
        // No feedback to interrupt — AutoFlow produces no audible/haptic output.
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                e.packageName?.toString()?.let { lastWindowPackage = it }
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val insp = inspector
                if (insp.isActive()) captureForInspector(e, insp)
            }
        }
    }

    /**
     * Package of the app the user currently sees. Prefers the live active
     * window; falls back to the last window-state event during transitions.
     */
    fun currentForegroundPackage(): String? = try {
        rootInActiveWindow?.packageName?.toString() ?: lastWindowPackage
    } catch (e: Exception) {
        lastWindowPackage
    }

    /** Fresh root of the active window, or null when unavailable. */
    fun activeRoot(): AccessibilityNodeInfo? = try {
        rootInActiveWindow
    } catch (e: Exception) {
        null
    }

    private fun captureForInspector(event: AccessibilityEvent, inspector: UiInspector) {
        val source = try {
            event.source
        } catch (e: Exception) {
            null
        } ?: return
        try {
            val isPassword = source.isPassword
            val packageName = source.packageName?.toString()
                ?: event.packageName?.toString().orEmpty()
            val container = (application as AutoFlowApplication).container
            inspector.record(
                UiInspector.InspectedElement(
                    packageName = packageName,
                    appLabel = container.installedApps.labelFor(packageName) ?: packageName,
                    // Password content is never read — structure only.
                    text = if (isPassword) "" else source.text?.toString().orEmpty(),
                    contentDescription = source.contentDescription?.toString().orEmpty(),
                    viewId = source.viewIdResourceName.orEmpty(),
                    className = source.className?.toString().orEmpty(),
                    isClickable = source.isClickable,
                    isLongClickable = source.isLongClickable,
                    isEditable = source.isEditable,
                    isScrollable = source.isScrollable,
                    isPassword = isPassword,
                    capturedAt = System.currentTimeMillis()
                )
            )
        } catch (e: IllegalStateException) {
            // Node went stale between event and read — skip this capture.
        } finally {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                try {
                    @Suppress("DEPRECATION")
                    source.recycle()
                } catch (_: IllegalStateException) {
                }
            }
        }
    }

    companion object {
        private const val TAG = "AutoFlowAccessibility"

        /**
         * Set while the system has the service bound. The host reads it per
         * call and fails structurally when null — never cached across steps.
         */
        @Volatile
        var instance: AutoFlowAccessibilityService? = null
            private set
    }
}
