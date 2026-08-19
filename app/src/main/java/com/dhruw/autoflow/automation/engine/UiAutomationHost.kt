package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.UiNode

/**
 * Everything the [UiAutomationExecutor] needs from the platform, behind one
 * interface so the executor stays pure and unit-testable. The Android
 * implementation is backed by AutoFlowAccessibilityService; tests use a fake.
 *
 * [rootNode] must return a FRESH tree each call — the executor never caches
 * nodes across steps (they go stale).
 */
interface UiAutomationHost {

    /** Root of the active window's accessibility tree, or null if unavailable. */
    fun rootNode(): UiNode?

    /** Package of the foreground app, or null if unknown. */
    fun currentPackage(): String?

    /** True when the device is locked (keyguard). */
    fun isDeviceLocked(): Boolean

    /** Bring [packageName] to the foreground. False if not installed/launchable. */
    suspend fun launchApp(packageName: String): Boolean

    /** GLOBAL_ACTION_BACK. */
    suspend fun globalBack(): Boolean

    /**
     * Ask the user to approve the next (consequential) step. Suspends until
     * they answer; returns true to continue, false to cancel. Implementations
     * must surface [prompt]/[nextActionLabel] in a clear UI.
     */
    suspend fun requestConfirmation(prompt: String, nextActionLabel: String): Boolean
}
