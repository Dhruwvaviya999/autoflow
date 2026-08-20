package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.ScrollDirection
import com.dhruw.autoflow.automation.model.TriggerPayload
import com.dhruw.autoflow.automation.model.UiNode
import com.dhruw.autoflow.automation.model.UiSelector
import com.dhruw.autoflow.automation.model.UiStep
import com.dhruw.autoflow.automation.model.UiStepResult
import com.dhruw.autoflow.automation.model.UiStepStatus
import com.dhruw.autoflow.automation.model.displayName
import com.dhruw.autoflow.automation.model.nearestClickable
import com.dhruw.autoflow.automation.model.nearestLongClickable
import kotlinx.coroutines.delay

/**
 * Runs a UI automation workflow step by step against a [UiAutomationHost].
 * Pure orchestration — no Android imports — so the whole state machine is
 * unit-testable with a fake host and fake tree.
 *
 * Safety invariants enforced here (see Phase 7 spec):
 * - Overall + per-step timeouts; every wait is bounded and cancellable. The
 *   overall deadline is tracked with [clock] rather than withTimeout so time
 *   spent waiting for the user's confirmation does NOT count against it —
 *   otherwise a slow human answer would fail an otherwise healthy run.
 * - Package boundary: after LaunchApp, every element step verifies the
 *   foreground package still matches the target; a drift (system dialog,
 *   another app) fails the run instead of touching the wrong UI.
 * - Screen lock mid-run → cancel immediately; never attempts to unlock.
 * - SetText refuses password/sensitive fields.
 * - Ambiguous selectors fail (never a random node).
 * - Consequential steps pause for explicit user confirmation.
 * Nodes are re-fetched fresh per step and never cached. Cancellation (the
 * caller's coroutine being cancelled) propagates out of any delay/step —
 * callers treat it as a CANCELLED run.
 */
class UiAutomationExecutor(
    private val host: UiAutomationHost,
    private val finder: AccessibilityNodeFinder = AccessibilityNodeFinder(),
    private val clock: () -> Long = System::currentTimeMillis,
    /** Poll interval for WaitForElement and deadline checks. */
    private val pollIntervalMillis: Long = 200,
    private val maxWaitMillis: Long = 120_000
) {

    /** Thrown internally to stop the run; carried out as a step result. */
    private class StepFailure(val status: UiStepStatus, val detail: String) : Exception(detail)

    data class Outcome(
        val success: Boolean,
        val results: List<UiStepResult>,
        val cancelled: Boolean = false
    )

    /** Deadline for the whole run; confirmation waits push it back. */
    private class Deadline(var at: Long) {
        fun extend(byMillis: Long) {
            at += byMillis
        }
    }

    /**
     * Execute [steps] targeting [targetPackage] within [overallTimeoutMillis].
     * [payload] feeds SetText templates. [onStep] reports progress live (for
     * the notification / session UI); it is also called with the upcoming
     * step index via [onStepStarted] so progress UI can show "Step N of M".
     * Never throws for expected failures — they land in the returned results.
     * Coroutine cancellation propagates (caller marks the run CANCELLED).
     */
    suspend fun run(
        targetPackage: String,
        steps: List<UiStep>,
        payload: TriggerPayload?,
        overallTimeoutMillis: Long,
        onStepStarted: (index: Int, step: UiStep) -> Unit = { _, _ -> },
        onStep: (UiStepResult) -> Unit = {}
    ): Outcome {
        val results = ArrayList<UiStepResult>(steps.size)
        val deadline = Deadline(clock() + overallTimeoutMillis.coerceIn(1_000, 600_000))
        var launched = false

        for ((index, step) in steps.withIndex()) {
            val result = try {
                ensureUnlocked()
                ensureWithinDeadline(deadline)
                if (launched && step.needsPackageGuard()) ensureOnTarget(targetPackage, deadline)
                onStepStarted(index, step)

                val detail = execute(step, payload, deadline)
                if (step is UiStep.LaunchApp) launched = true
                UiStepResult(index, step.displayName, UiStepStatus.SUCCESS, detail)
            } catch (e: StepFailure) {
                UiStepResult(index, step.displayName, e.status, e.detail)
            }
            results += result
            onStep(result)
            if (result.status != UiStepStatus.SUCCESS) {
                return Outcome(
                    success = false,
                    results = results,
                    cancelled = result.status == UiStepStatus.CANCELLED
                )
            }
        }
        return Outcome(success = true, results = results)
    }

    /** Runs one step; returns a non-sensitive detail string or throws StepFailure. */
    private suspend fun execute(
        step: UiStep,
        payload: TriggerPayload?,
        deadline: Deadline
    ): String = when (step) {
        is UiStep.LaunchApp -> {
            val ok = host.launchApp(step.packageName)
            if (!ok) fail(UiStepStatus.FAILED, "Could not launch ${step.appLabel.ifBlank { step.packageName }}")
            // Give the window a moment to come up before the next step polls.
            boundedDelay(pollIntervalMillis * 3, deadline)
            "Launched ${step.appLabel.ifBlank { step.packageName }}"
        }

        is UiStep.Wait -> {
            boundedDelay(step.durationMillis.coerceIn(0, maxWaitMillis), deadline)
            "Waited"
        }

        is UiStep.WaitForElement -> {
            waitFor(step.selector, step.timeoutMillis, deadline)
                ?: fail(UiStepStatus.TIMEOUT, "Timed out waiting for ${step.selector.summary}")
            "Found ${step.selector.summary}"
        }

        is UiStep.ClickElement -> {
            val node = requireOne(step.selector, deadline)
            val target = node.nearestClickable()
                ?: fail(UiStepStatus.FAILED, "No clickable target for ${step.selector.summary}")
            if (!target.performClick()) fail(UiStepStatus.FAILED, "Tap failed on ${step.selector.summary}")
            "Tapped ${step.selector.summary}"
        }

        is UiStep.LongClickElement -> {
            val node = requireOne(step.selector, deadline)
            val target = node.nearestLongClickable()
                ?: fail(UiStepStatus.FAILED, "Long press not supported for ${step.selector.summary}")
            if (!target.performLongClick()) fail(UiStepStatus.FAILED, "Long press failed")
            "Long pressed ${step.selector.summary}"
        }

        is UiStep.SetText -> {
            val node = requireOne(step.selector, deadline)
            if (node.isPassword) {
                fail(
                    UiStepStatus.FAILED,
                    "Refused: AutoFlow does not enter text into password or sensitive authentication fields"
                )
            }
            if (!node.isEditable) fail(UiStepStatus.FAILED, "${step.selector.summary} is not a text field")
            val text = when (val r = TemplateResolver.resolve(step.text, payload)) {
                is TemplateResolver.Result.Ok -> r.text
                is TemplateResolver.Result.UnknownVariable ->
                    fail(UiStepStatus.FAILED, "Unknown variable {{${r.variable}}}")
                is TemplateResolver.Result.Unavailable ->
                    fail(UiStepStatus.FAILED, "Variable {{${r.variable}}} not available for this trigger")
            }
            if (!node.performSetText(text)) fail(UiStepStatus.FAILED, "Could not enter text")
            "Entered text" // never the value itself
        }

        is UiStep.Scroll -> {
            // Scroll actions are dispatched asynchronously by the framework —
            // right after a previous scroll the node can still report the old
            // scroll position and reject the action. Retry briefly on a fresh
            // node before concluding the element genuinely cannot scroll.
            val retryUntil = clock() + pollIntervalMillis * 6
            var ok: Boolean
            while (true) {
                val node = requireOne(step.selector, deadline)
                ok = when (step.direction) {
                    ScrollDirection.FORWARD -> node.performScrollForward()
                    ScrollDirection.BACKWARD -> node.performScrollBackward()
                }
                if (ok || clock() >= retryUntil) break
                boundedDelay(pollIntervalMillis, deadline)
            }
            if (!ok) fail(UiStepStatus.FAILED, "Scroll not supported on ${step.selector.summary}")
            // Let the scroll settle so the next step reads a current tree.
            boundedDelay(pollIntervalMillis, deadline)
            "Scrolled ${step.direction.name.lowercase()}"
        }

        is UiStep.GlobalBack -> {
            if (!host.globalBack()) fail(UiStepStatus.FAILED, "Back action failed")
            "Pressed Back"
        }

        is UiStep.RequireUserConfirmation -> {
            // Human thinking time must not eat the workflow budget.
            val askedAt = clock()
            val approved = host.requestConfirmation(
                step.prompt.ifBlank { "Continue the automation?" },
                step.nextActionLabel
            )
            deadline.extend(clock() - askedAt)
            if (!approved) fail(UiStepStatus.CANCELLED, "Not confirmed — automation stopped")
            // The shade or dialog that carried the answer may still be
            // collapsing — let the target window become readable again
            // before the next step queries the tree.
            boundedDelay(pollIntervalMillis * 3, deadline)
            "Confirmed by user"
        }
    }

    /** Poll the tree until the selector resolves to a node or time runs out. */
    private suspend fun waitFor(
        selector: UiSelector,
        timeoutMillis: Long,
        deadline: Deadline
    ): UiNode? {
        val stepDeadline = clock() + timeoutMillis.coerceIn(0, maxWaitMillis)
        while (true) {
            // A null root (window transition, protected screen) is treated as
            // "not there yet" and retried until the step times out.
            when (val r = finder.findOne(host.rootNode(), selector)) {
                is AccessibilityNodeFinder.Result.Found -> return r.node
                is AccessibilityNodeFinder.Result.Ambiguous ->
                    fail(UiStepStatus.FAILED, "${r.count} elements match ${selector.summary} — make the selector more specific")
                AccessibilityNodeFinder.Result.NotFound -> Unit
            }
            val now = clock()
            if (now >= stepDeadline) return null
            if (now >= deadline.at) fail(UiStepStatus.TIMEOUT, "Workflow timeout reached")
            delay(pollIntervalMillis)
        }
    }

    /**
     * Resolve a selector to exactly one node now, or fail. A null root is
     * retried briefly: window transitions (a collapsing notification shade,
     * a closing dialog) leave the screen unreadable for a moment without the
     * service being disconnected.
     */
    private suspend fun requireOne(selector: UiSelector, deadline: Deadline): UiNode {
        var root = host.rootNode()
        val graceUntil = clock() + pollIntervalMillis * 10
        while (root == null) {
            if (clock() >= graceUntil) {
                fail(
                    UiStepStatus.FAILED,
                    "Screen not readable — the accessibility service is not connected or the current screen is protected"
                )
            }
            boundedDelay(pollIntervalMillis, deadline)
            root = host.rootNode()
        }
        return when (val r = finder.findOne(root, selector)) {
            is AccessibilityNodeFinder.Result.Found -> r.node
            AccessibilityNodeFinder.Result.NotFound ->
                fail(UiStepStatus.FAILED, "Could not find ${selector.summary}")
            is AccessibilityNodeFinder.Result.Ambiguous ->
                fail(UiStepStatus.FAILED, "${r.count} elements match ${selector.summary} — make the selector more specific")
        }
    }

    /** Sleep in short slices so cancellation and the overall deadline stay live. */
    private suspend fun boundedDelay(durationMillis: Long, deadline: Deadline) {
        val until = clock() + durationMillis
        while (true) {
            val now = clock()
            if (now >= until) return
            if (now >= deadline.at) fail(UiStepStatus.TIMEOUT, "Workflow timeout reached")
            delay((until - now).coerceAtMost(pollIntervalMillis))
        }
    }

    private fun ensureWithinDeadline(deadline: Deadline) {
        if (clock() >= deadline.at) fail(UiStepStatus.TIMEOUT, "Workflow timeout reached")
    }

    private fun ensureUnlocked() {
        if (host.isDeviceLocked()) {
            fail(UiStepStatus.CANCELLED, "Device locked — automation stopped")
        }
    }

    /**
     * Foreground guard with two tolerances:
     * - The system UI overlay (notification shade — e.g. the one that carried
     *   a confirmation answer — volume panel, recents) covers the target app
     *   without replacing it. The run WAITS for it to clear, bounded only by
     *   the workflow deadline; it never acts while the overlay is up.
     * - Anything else gets a short transition grace (window animations),
     *   then fails: another app or a persistent dialog took over.
     */
    private suspend fun ensureOnTarget(targetPackage: String, deadline: Deadline) {
        val graceUntil = clock() + pollIntervalMillis * 10
        while (true) {
            val current = host.currentPackage()
            if (current == null || current == targetPackage) return
            if (current != SYSTEM_UI_PACKAGE && clock() >= graceUntil) {
                fail(
                    UiStepStatus.FAILED,
                    "Foreground app changed to $current — stopped to avoid acting on the wrong app"
                )
            }
            boundedDelay(pollIntervalMillis, deadline)
        }
    }

    private fun UiStep.needsPackageGuard(): Boolean = when (this) {
        is UiStep.ClickElement, is UiStep.LongClickElement, is UiStep.SetText,
        is UiStep.Scroll, is UiStep.WaitForElement -> true
        else -> false
    }

    private fun fail(status: UiStepStatus, detail: String): Nothing = throw StepFailure(status, detail)

    private companion object {
        /** The status bar / notification shade / volume UI — an overlay, never a target. */
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }
}
