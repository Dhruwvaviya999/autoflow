package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.UiSelector
import com.dhruw.autoflow.automation.model.UiStep

/**
 * Edit-time checks for a [Action.UiAutomationAction]. Pure — the editor calls
 * [validate] before allowing a save so a broken workflow can never be stored
 * or run. Returns the first problem found, or Ok. [warnings] adds non-blocking
 * advice (e.g. an unconfirmed final tap) the editor shows next to Save.
 *
 * Safety rules (Phase 7 consequence guard):
 * - A tap/long-press whose selector looks like a payment / purchase / OTP /
 *   password confirmation must be immediately preceded by
 *   RequireUserConfirmation, otherwise the workflow does not save.
 * - Entering text into a selector that looks like a password/PIN/OTP/card
 *   field never saves — [UiAutomationExecutor] additionally refuses password
 *   nodes at run time, because selectors cannot be trusted to reveal intent.
 */
object UiWorkflowValidator {

    sealed interface Result {
        data object Ok : Result
        data class Invalid(val message: String) : Result
    }

    private const val MIN_TIMEOUT = 1_000L
    private const val MAX_TIMEOUT = 300_000L
    private const val MAX_WAIT = 120_000L

    /**
     * Word-bounded so "display" or "October" never trip it. Deliberately
     * conservative: this guard exists to force a confirmation pause on the
     * obvious cases, not to understand every app's semantics.
     */
    private val CONSEQUENTIAL_CLICK = Regex(
        "\\b(pay|pay now|buy|purchase|place order|checkout|confirm payment|" +
            "send money|transfer|withdraw|deposit|subscribe|delete account|otp)\\b",
        RegexOption.IGNORE_CASE
    )

    private val SENSITIVE_INPUT = Regex(
        "\\b(password|passcode|pin|otp|cvv|cvc|card number|security answer|2fa|one[- ]time)\\b",
        RegexOption.IGNORE_CASE
    )

    fun validate(action: Action.UiAutomationAction): Result {
        if (action.targetPackage.isBlank()) return invalid("Choose a target app")
        if (action.steps.isEmpty()) return invalid("Add at least one step")
        if (action.overallTimeoutMillis !in MIN_TIMEOUT..MAX_TIMEOUT) {
            return invalid("Overall timeout must be between 1 and 300 seconds")
        }

        action.steps.forEachIndexed { index, step ->
            val where = "Step ${index + 1}"
            when (step) {
                is UiStep.LaunchApp ->
                    if (step.packageName.isBlank()) return invalid("$where: no app selected")

                is UiStep.Wait ->
                    if (step.durationMillis !in 0..MAX_WAIT) return invalid("$where: wait out of range")

                is UiStep.WaitForElement -> {
                    if (step.selector.isBlank) return invalid("$where: empty selector")
                    if (step.timeoutMillis !in MIN_TIMEOUT..MAX_WAIT) {
                        return invalid("$where: wait timeout must be 1–120 seconds")
                    }
                }

                is UiStep.ClickElement -> {
                    if (step.selector.isBlank) return invalid("$where: empty selector")
                    if (looksConsequential(step.selector) && !confirmedBefore(action.steps, index)) {
                        return invalid(
                            "$where: \"${step.selector.summary}\" looks like a payment, purchase or " +
                                "security confirmation. Add a Require confirmation step directly " +
                                "before it — AutoFlow never performs these automatically."
                        )
                    }
                }

                is UiStep.LongClickElement ->
                    if (step.selector.isBlank) return invalid("$where: empty selector")

                is UiStep.Scroll ->
                    if (step.selector.isBlank) return invalid("$where: empty selector")

                is UiStep.SetText -> {
                    if (step.selector.isBlank) return invalid("$where: empty selector")
                    if (step.text.isEmpty()) return invalid("$where: no text to enter")
                    if (looksSensitiveInput(step.selector)) {
                        return invalid(
                            "$where: AutoFlow does not enter text into password, PIN, OTP or " +
                                "payment-card fields."
                        )
                    }
                    when (val t = TemplateResolver.validate(step.text)) {
                        is TemplateResolver.Result.UnknownVariable ->
                            return invalid("$where: unknown variable {{${t.variable}}}")
                        else -> Unit
                    }
                }

                is UiStep.GlobalBack, is UiStep.RequireUserConfirmation -> Unit
            }
        }
        return Result.Ok
    }

    /**
     * Non-blocking advice shown in the editor. Currently: warn when the
     * workflow's final interaction could commit something (a tap after text
     * was entered, or a plain final tap) without a confirmation pause.
     */
    fun warnings(action: Action.UiAutomationAction): List<String> {
        val warnings = mutableListOf<String>()
        val lastInteractive = action.steps.indexOfLast {
            it is UiStep.ClickElement || it is UiStep.LongClickElement || it is UiStep.SetText
        }
        if (lastInteractive >= 0) {
            val step = action.steps[lastInteractive]
            val entersTextEarlier = action.steps.take(lastInteractive).any { it is UiStep.SetText }
            val isFinalTapAfterText =
                (step is UiStep.ClickElement || step is UiStep.LongClickElement) && entersTextEarlier
            if (isFinalTapAfterText && !confirmedBefore(action.steps, lastInteractive)) {
                warnings += "The final tap may send or submit what was typed. Consider adding a " +
                    "Require confirmation step before it so nothing is sent without you."
            }
        }
        if (action.steps.any { it is UiStep.ClickElement && it.selector.usesTextOnly() }) {
            warnings += "Text-based selectors can break when the app changes its language, " +
                "layout or wording. Prefer a view ID or content description when available."
        }
        return warnings
    }

    /** True when a RequireUserConfirmation step directly precedes [index]. */
    private fun confirmedBefore(steps: List<UiStep>, index: Int): Boolean =
        steps.getOrNull(index - 1) is UiStep.RequireUserConfirmation

    private fun looksConsequential(selector: UiSelector): Boolean =
        CONSEQUENTIAL_CLICK.containsMatchIn(selector.text) ||
            CONSEQUENTIAL_CLICK.containsMatchIn(selector.contentDescription)

    private fun looksSensitiveInput(selector: UiSelector): Boolean =
        SENSITIVE_INPUT.containsMatchIn(selector.text) ||
            SENSITIVE_INPUT.containsMatchIn(selector.contentDescription) ||
            SENSITIVE_INPUT.containsMatchIn(selector.viewId.replace('_', ' '))

    private fun UiSelector.usesTextOnly(): Boolean =
        text.isNotBlank() && viewId.isBlank() && contentDescription.isBlank()

    private fun invalid(message: String) = Result.Invalid(message)
}
