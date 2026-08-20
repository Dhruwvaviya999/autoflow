package com.dhruw.autoflow.services.accessibility

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import com.dhruw.autoflow.automation.engine.UiAutomationHost
import com.dhruw.autoflow.automation.model.UiNode

/**
 * Android implementation of [UiAutomationHost], backed by the currently
 * connected [AutoFlowAccessibilityService]. Owned by one automation run:
 * each [rootNode] call fetches a fresh tree and releases the previous one,
 * so no AccessibilityNodeInfo outlives the step that used it. The session
 * manager calls [releaseTree] once more when the run ends.
 */
class AndroidUiAutomationHost(
    private val context: Context,
    private val confirm: suspend (prompt: String, nextActionLabel: String) -> Boolean
) : UiAutomationHost {

    private var tracker: NodeTracker? = null

    override fun rootNode(): UiNode? {
        val service = AutoFlowAccessibilityService.instance ?: return null
        releaseTree()
        val root = service.activeRoot() ?: return null
        val newTracker = NodeTracker()
        newTracker.track(root)
        tracker = newTracker
        return AccessibilityUiNode(root, newTracker)
    }

    /** Releases every node obtained since the last [rootNode] call. */
    fun releaseTree() {
        tracker?.release()
        tracker = null
    }

    override fun currentPackage(): String? =
        AutoFlowAccessibilityService.instance?.currentForegroundPackage()

    override fun isDeviceLocked(): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    override suspend fun launchApp(packageName: String): Boolean = try {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            false
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }
    } catch (e: Exception) {
        // Not installed, no launchable activity, or launch blocked — a
        // structured failure for the executor, never a crash.
        false
    }

    override suspend fun globalBack(): Boolean = try {
        AutoFlowAccessibilityService.instance
            ?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK) == true
    } catch (e: Exception) {
        false
    }

    override suspend fun requestConfirmation(prompt: String, nextActionLabel: String): Boolean =
        confirm(prompt, nextActionLabel)
}
