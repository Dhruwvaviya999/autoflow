package com.dhruw.autoflow.services.accessibility

import com.dhruw.autoflow.automation.engine.AccessibilityNodeFinder
import com.dhruw.autoflow.automation.engine.UiAutomationExecutor
import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.TriggerPayload
import com.dhruw.autoflow.automation.model.UiAutomationSession
import com.dhruw.autoflow.automation.model.UiSelector
import com.dhruw.autoflow.automation.model.UiSessionStatus
import com.dhruw.autoflow.automation.model.UiStepStatus
import com.dhruw.autoflow.automation.model.summary
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.withTimeoutOrNull
import android.content.Context
import kotlinx.coroutines.Job

/**
 * Owns the one-and-only UI automation session (Phase 7 allows a single
 * active session; concurrent starts get [RunResult.Busy]). Publishes live
 * session state for the in-app banner/dialog, drives the progress and
 * confirmation notifications, and brokers user confirmations between the
 * executor (which suspends) and the notification/dialog (which answers).
 *
 * Runs inside the caller's coroutine — cancellation from the Cancel button
 * cancels the inner scope, which surfaces as a CANCELLED execution through
 * the engine's normal cancellation path.
 */
class UiAutomationSessionManager(
    private val context: Context,
    private val accessManager: AccessibilityAccessManager
) {

    sealed interface RunResult {
        data class Completed(val outcome: UiAutomationExecutor.Outcome) : RunResult
        data object Busy : RunResult
        data object ServiceUnavailable : RunResult
    }

    data class SelectorTestResult(
        val matchCount: Int,
        val detail: String,
        val testedAt: Long
    )

    private val _session = MutableStateFlow<UiAutomationSession?>(null)
    val session: StateFlow<UiAutomationSession?> = _session.asStateFlow()

    /** Last selector test outcome, shown by the selector editor. */
    private val _lastSelectorTest = MutableStateFlow<SelectorTestResult?>(null)
    val lastSelectorTest: StateFlow<SelectorTestResult?> = _lastSelectorTest.asStateFlow()

    private val running = AtomicBoolean(false)
    private val notifications = UiAutomationNotifications(context)

    @Volatile
    private var runJob: Job? = null

    @Volatile
    private var pendingConfirmation: CompletableDeferred<Boolean>? = null

    /**
     * Execute [action] with the accessibility engine. [onLog] receives one
     * non-sensitive line per finished step for the execution history.
     * Expected failures come back in the outcome; coroutine cancellation
     * (Cancel button or caller teardown) propagates after cleanup.
     */
    suspend fun run(
        action: Action.UiAutomationAction,
        payload: TriggerPayload?,
        automationId: String?,
        onLog: (String) -> Unit
    ): RunResult {
        if (!accessManager.isEnabled() || !accessManager.isServiceConnected()) {
            return RunResult.ServiceUnavailable
        }
        if (!running.compareAndSet(false, true)) return RunResult.Busy

        val host = AndroidUiAutomationHost(context, ::awaitConfirmation)
        val totalSteps = action.steps.size
        _session.value = UiAutomationSession(
            sessionId = UUID.randomUUID().toString(),
            automationId = automationId,
            targetPackage = action.targetPackage,
            startedAt = System.currentTimeMillis(),
            totalSteps = totalSteps,
            currentStep = 0,
            status = UiSessionStatus.RUNNING
        )

        try {
            val outcome = coroutineScope {
                runJob = coroutineContext.job
                UiAutomationExecutor(host).run(
                    targetPackage = action.targetPackage,
                    steps = action.steps,
                    payload = payload,
                    overallTimeoutMillis = action.overallTimeoutMillis,
                    onStepStarted = { index, step ->
                        _session.update {
                            it?.copy(currentStep = index + 1, status = UiSessionStatus.RUNNING)
                        }
                        notifications.showProgress(index + 1, totalSteps, step.summary)
                    },
                    onStep = { result ->
                        val mark = if (result.status == UiStepStatus.SUCCESS) "✓" else "✗"
                        onLog("$mark ${result.detail.ifBlank { result.stepName }}")
                    }
                )
            }
            _session.update {
                it?.copy(
                    status = when {
                        outcome.success -> UiSessionStatus.SUCCESS
                        outcome.cancelled -> UiSessionStatus.CANCELLED
                        else -> UiSessionStatus.FAILED
                    }
                )
            }
            return RunResult.Completed(outcome)
        } catch (e: CancellationException) {
            onLog("✗ Cancelled")
            throw e
        } finally {
            runJob = null
            pendingConfirmation?.cancel()
            pendingConfirmation = null
            host.releaseTree()
            notifications.clear()
            running.set(false)
            _session.value = null
        }
    }

    /** Stops the active session: no further steps, resources released. */
    fun cancel() {
        // A pending confirmation is answered "no" (graceful CANCELLED step);
        // otherwise the running scope itself is cancelled.
        val pending = pendingConfirmation
        if (pending != null && !pending.isCompleted) {
            pending.complete(false)
        } else {
            runJob?.cancel()
        }
    }

    /** Called by the confirmation notification actions / in-app dialog. */
    fun respondToConfirmation(approved: Boolean) {
        pendingConfirmation?.complete(approved)
    }

    private suspend fun awaitConfirmation(prompt: String, nextActionLabel: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        pendingConfirmation = deferred
        _session.update {
            it?.copy(
                status = UiSessionStatus.WAITING_FOR_CONFIRMATION,
                confirmationPrompt = prompt,
                nextActionLabel = nextActionLabel.ifBlank { null }
            )
        }
        notifications.showConfirmation(prompt, nextActionLabel)
        return try {
            // Bounded: an unanswered confirmation stops the run safely.
            withTimeoutOrNull(CONFIRMATION_TIMEOUT_MILLIS) { deferred.await() } == true
        } finally {
            pendingConfirmation = null
            _session.update {
                it?.copy(
                    status = UiSessionStatus.RUNNING,
                    confirmationPrompt = null,
                    nextActionLabel = null
                )
            }
        }
    }

    /**
     * Selector tester: brings [targetPackage] to the foreground if needed,
     * waits briefly for its UI, then counts matches. Matching only — no
     * action is ever performed. Result lands in [lastSelectorTest] so the
     * editor can show it when the user returns.
     */
    suspend fun testSelector(targetPackage: String, selector: UiSelector): SelectorTestResult {
        val result = testSelectorInternal(targetPackage, selector)
        _lastSelectorTest.value = result
        return result
    }

    private suspend fun testSelectorInternal(
        targetPackage: String,
        selector: UiSelector
    ): SelectorTestResult {
        fun result(count: Int, detail: String) =
            SelectorTestResult(count, detail, System.currentTimeMillis())

        if (!accessManager.isEnabled() || !accessManager.isServiceConnected()) {
            return result(-1, "Accessibility is not enabled for AutoFlow")
        }
        if (running.get()) return result(-1, "A UI automation is running — try again after it finishes")
        if (selector.isBlank) return result(-1, "Selector is empty")

        val host = AndroidUiAutomationHost(context) { _, _ -> false }
        try {
            val current = host.currentPackage()
            if (current != targetPackage) {
                if (!host.launchApp(targetPackage)) {
                    return result(-1, "Could not open the target app")
                }
                delay(LAUNCH_SETTLE_MILLIS)
            }
            if (host.currentPackage() != targetPackage) {
                return result(-1, "Target app did not come to the foreground")
            }
            val count = AccessibilityNodeFinder().findAll(host.rootNode(), selector).size
            return result(
                count,
                when (count) {
                    0 -> "0 elements found"
                    1 -> "1 matching element found"
                    else -> "$count elements found — selector is ambiguous"
                }
            )
        } finally {
            host.releaseTree()
        }
    }

    private companion object {
        const val CONFIRMATION_TIMEOUT_MILLIS = 120_000L
        const val LAUNCH_SETTLE_MILLIS = 2_500L
    }
}
