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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/**
 * Runs a UI automation workflow step by step against a [UiAutomationHost].
 * Pure orchestration — no Android imports — so the whole state machine is
 * unit-testable with a fake host and fake tree.
 *
 * Safety invariants enforced here (see Phase 7 spec):
 * - Overall + per-step timeouts; every wait is bounded and cancellable.
 * - Package boundary: after LaunchApp, every element step verifies the
 *   foreground package still matches the target; a drift (system dialog,
 *   another app) fails the run instead of touching the wrong UI.
 * - Screen lock mid-run → fail immediately; never attempts to unlock.
 * - SetText refuses password/sensitive fields.
 * - Ambiguous selectors fail (never a random node).
 * - Consequential steps pause for explicit user confirmation.
 * Nodes are re-fetched fresh per step and never cached.
 */
class UiAutomationExecutor(
    private val host: UiAutomationHost,
    private val finder: AccessibilityNodeFinder = AccessibilityNodeFinder(),
    private val clock: () -> Long = System::currentTimeMillis,
    /** Poll interval for WaitForElement. */
    private val pollIntervalMillis: Long = 200,
    private val maxWaitMillis: Long = 60_000
) {

    /** Thrown internally to stop the run; carried out as a step result. */
    private class StepFailure(val status: UiStepStatus, val detail: String) : Exception(detail)

    data class Outcome(
        val success: Boolean,
        val results: List<UiStepResult>,
        val cancelled: Boolean = false
    )

    /**
     * Execute [steps] targeting [targetPackage] within [overallTimeoutMillis].
     * [payload] feeds SetText templates. [onStep] reports progress live (for
     * the notification / session UI). Never throws for expected failures —
     * they land in the returned results.
     */
    suspend fun run(
        targetPackage: String,
        steps: List<UiStep>,
        payload: TriggerPayload?,
        overallTimeoutMillis: Long,
        onStep: (UiStepResult) -> Unit = {}
    ): Outcome {
        val results = ArrayList<UiStepResult>(steps.size)
        var launched = false

        return try {
            withTimeout(overallTimeoutMillis.coerceIn(1_000, maxWaitMillis + 5_000)) {
                for ((index, step) in steps.withIndex()) {
                    ensureUnlocked()
                    if (launched && step.needsPackageGuard()) ensureOnTarget(targetPackage)

                    val result = try {
                        val detail = execute(step, targetPackage, payload)
                        if (step is UiStep.LaunchApp) launched = true
                        UiStepResult(index, step.displayName, UiStepStatus.SUCCESS, detail)
                    } catch (e: StepFailure) {
                        UiStepResult(index, step.displayName, e.status, e.detail)
                    }
                    results += result
                    onStep(result)
                    if (result.status != UiStepStatus.SUCCESS) {
                        return@withTimeout Outcome(
                            success = false,
                            results = results,
                            cancelled = result.status == UiStepStatus.CANCELLED
                        )
                    }
                }
                Outcome(success = true, results = results)
            }
        } catch (e: TimeoutCancellationException) {
            results += UiStepResult(results.size, "Workflow", UiStepStatus.TIMEOUT, "Overall timeout reached")
            onStep(results.last())
            Outcome(success = false, results = results)
        }
    }

    /** Runs one step; returns a non-sensitive detail string or throws StepFailure. */
    private suspend fun execute(
        step: UiStep,
        targetPackage: String,
        payload: TriggerPayload?
    ): String = when (step) {
        is UiStep.LaunchApp -> {
            val ok = host.launchApp(step.packageName)
            if (!ok) fail(UiStepStatus.FAILED, "Could not launch ${step.appLabel.ifBlank { step.packageName }}")
            // Give the window a moment to come up before the next step polls.
            delay(pollIntervalMillis * 3)
            "Launched ${step.appLabel.ifBlank { step.packageName }}"
        }

        is UiStep.Wait -> {
            delay(step.durationMillis.coerceIn(0, maxWaitMillis))
            "Waited"
        }

        is UiStep.WaitForElement -> {
            val found = waitFor(step.selector, step.timeoutMillis)
                ?: fail(UiStepStatus.TIMEOUT, "Timed out waiting for ${step.selector.summary}")
            "Found ${step.selector.summary}"
        }

        is UiStep.ClickElement -> {
            val node = requireOne(step.selector)
            val target = node.nearestClickable()
                ?: fail(UiStepStatus.FAILED, "No clickable target for ${step.selector.summary}")
            if (!target.performClick()) fail(UiStepStatus.FAILED, "Tap failed on ${step.selector.summary}")
            "Tapped ${step.selector.summary}"
        }

        is UiStep.LongClickElement -> {
            val node = requireOne(step.selector)
            val target = node.nearestLongClickable()
                ?: fail(UiStepStatus.FAILED, "Long press not supported for ${step.selector.summary}")
            if (!target.performLongClick()) fail(UiStepStatus.FAILED, "Long press failed")
            "Long pressed ${step.selector.summary}"
        }

        is UiStep.SetText -> {
            val node = requireOne(step.selector)
            if (node.isPassword) {
                fail(
                    UiStepStatus.FAILED,
                    "Refused: AutoFlow does not enter text into password or sensitive fields"
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
            val node = requireOne(step.selector)
            val ok = when (step.direction) {
                ScrollDirection.FORWARD -> node.performScrollForward()
                ScrollDirection.BACKWARD -> node.performScrollBackward()
            }
            if (!ok) fail(UiStepStatus.FAILED, "Scroll not supported on ${step.selector.summary}")
            "Scrolled ${step.direction.name.lowercase()}"
        }

        is UiStep.GlobalBack -> {
            if (!host.globalBack()) fail(UiStepStatus.FAILED, "Back action failed")
            "Pressed Back"
        }

        is UiStep.RequireUserConfirmation -> {
            val approved = host.requestConfirmation(
                step.prompt.ifBlank { "Continue the automation?" },
                step.nextActionLabel
            )
            if (!approved) fail(UiStepStatus.CANCELLED, "Cancelled by user")
            "Confirmed by user"
        }
    }

    /** Poll the tree until the selector resolves to a node or time runs out. */
    private suspend fun waitFor(selector: UiSelector, timeoutMillis: Long): UiNode? {
        val deadline = clock() + timeoutMillis.coerceIn(0, maxWaitMillis)
        while (clock() <= deadline) {
            when (val r = finder.findOne(host.rootNode(), selector)) {
                is AccessibilityNodeFinder.Result.Found -> return r.node
                is AccessibilityNodeFinder.Result.Ambiguous ->
                    fail(UiStepStatus.FAILED, "${r.count} elements match ${selector.summary} — make it more specific")
                AccessibilityNodeFinder.Result.NotFound -> delay(pollIntervalMillis)
            }
        }
        return null
    }

    /** Resolve a selector to exactly one node now, or fail. */
    private fun requireOne(selector: UiSelector): UiNode =
        when (val r = finder.findOne(host.rootNode(), selector)) {
            is AccessibilityNodeFinder.Result.Found -> r.node
            AccessibilityNodeFinder.Result.NotFound ->
                fail(UiStepStatus.FAILED, "Could not find ${selector.summary}")
            is AccessibilityNodeFinder.Result.Ambiguous ->
                fail(UiStepStatus.FAILED, "${r.count} elements match ${selector.summary} — make it more specific")
        }

    private fun ensureUnlocked() {
        if (host.isDeviceLocked()) {
            fail(UiStepStatus.CANCELLED, "Device locked — automation stopped")
        }
    }

    private fun ensureOnTarget(targetPackage: String) {
        val current = host.currentPackage()
        if (current != null && current != targetPackage) {
            fail(
                UiStepStatus.FAILED,
                "Foreground app changed to $current — stopped to avoid acting on the wrong app"
            )
        }
    }

    private fun UiStep.needsPackageGuard(): Boolean = when (this) {
        is UiStep.ClickElement, is UiStep.LongClickElement, is UiStep.SetText,
        is UiStep.Scroll, is UiStep.WaitForElement -> true
        else -> false
    }

    private fun fail(status: UiStepStatus, detail: String): Nothing = throw StepFailure(status, detail)
}
