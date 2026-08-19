package com.dhruw.autoflow.automation.model

/** Risk class of a step; CONSEQUENTIAL steps require confirmation to run. */
enum class UiStepRisk { NORMAL, CONSEQUENTIAL }

enum class ScrollDirection { FORWARD, BACKWARD }

/**
 * One instruction in a UI automation workflow. Steps interact with the
 * foreground app through Android Accessibility, using [UiSelector]s rather
 * than screen coordinates. Text fields ([SetText]) accept a small template
 * language ({{notification.text}} etc.) resolved from the trigger payload.
 *
 * Deliberately NOT included: Home/Recents navigation, coordinate tapping,
 * shell/script execution — see Phase 7 scope.
 */
sealed interface UiStep {

    /** Bring [packageName] to the foreground via its launcher activity. */
    data class LaunchApp(
        val packageName: String,
        val appLabel: String = ""
    ) : UiStep

    /** Block for [durationMillis] (bounded); the run stays cancellable. */
    data class Wait(val durationMillis: Long) : UiStep

    /** Poll until [selector] matches or [timeoutMillis] elapses. */
    data class WaitForElement(
        val selector: UiSelector,
        val timeoutMillis: Long = 10_000
    ) : UiStep

    /** ACTION_CLICK on [selector] (or its nearest clickable ancestor). */
    data class ClickElement(val selector: UiSelector) : UiStep

    /** ACTION_LONG_CLICK on [selector]; fails if unsupported. */
    data class LongClickElement(val selector: UiSelector) : UiStep

    /**
     * ACTION_SET_TEXT into the editable node matched by [selector].
     * [text] is a template; password/OTP/sensitive fields are refused.
     */
    data class SetText(
        val selector: UiSelector,
        val text: String
    ) : UiStep

    /** ACTION_SCROLL_FORWARD/BACKWARD on the scrollable node at [selector]. */
    data class Scroll(
        val selector: UiSelector,
        val direction: ScrollDirection
    ) : UiStep

    /** GLOBAL_ACTION_BACK. */
    data object GlobalBack : UiStep

    /**
     * Pause and wait for explicit user approval before continuing — used
     * ahead of any consequential step (send/submit/confirm). [prompt] and
     * [nextActionLabel] are shown in the confirmation dialog.
     */
    data class RequireUserConfirmation(
        val prompt: String = "",
        val nextActionLabel: String = ""
    ) : UiStep
}

val UiStep.displayName: String
    get() = when (this) {
        is UiStep.LaunchApp -> "Launch app"
        is UiStep.Wait -> "Wait"
        is UiStep.WaitForElement -> "Wait for element"
        is UiStep.ClickElement -> "Tap element"
        is UiStep.LongClickElement -> "Long press"
        is UiStep.SetText -> "Enter text"
        is UiStep.Scroll -> "Scroll"
        is UiStep.GlobalBack -> "Go back"
        is UiStep.RequireUserConfirmation -> "Require confirmation"
    }

val UiStep.summary: String
    get() = when (this) {
        is UiStep.LaunchApp -> "Open ${appLabel.ifBlank { packageName }}"
        is UiStep.Wait -> {
            val s = durationMillis / 1000.0
            if (s == s.toLong().toDouble()) "Wait ${s.toLong()} s" else "Wait $s s"
        }
        is UiStep.WaitForElement -> "Wait for ${selector.summary} (≤${timeoutMillis / 1000}s)"
        is UiStep.ClickElement -> "Tap ${selector.summary}"
        is UiStep.LongClickElement -> "Long press ${selector.summary}"
        is UiStep.SetText -> "Enter text into ${selector.summary}"
        is UiStep.Scroll -> "Scroll ${if (direction == ScrollDirection.FORWARD) "forward" else "backward"} in ${selector.summary}"
        is UiStep.GlobalBack -> "Press Back"
        is UiStep.RequireUserConfirmation -> prompt.ifBlank { "Ask before continuing" }
    }

/**
 * A step is consequential when it can commit an action the user might not be
 * able to undo. Only explicit user-marked steps and the confirmation step are
 * treated as consequential here; SetText/Click are normal but the executor
 * still refuses sensitive fields at run time.
 */
val UiStep.risk: UiStepRisk
    get() = when (this) {
        is UiStep.RequireUserConfirmation -> UiStepRisk.CONSEQUENTIAL
        else -> UiStepRisk.NORMAL
    }
