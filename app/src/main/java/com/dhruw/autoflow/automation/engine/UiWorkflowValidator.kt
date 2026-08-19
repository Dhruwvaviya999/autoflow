package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.UiStep

/**
 * Edit-time checks for a [Action.UiAutomationAction]. Pure — the editor calls
 * [validate] before allowing a save so a broken workflow can never be stored
 * or run. Returns the first problem found, or Ok.
 */
object UiWorkflowValidator {

    sealed interface Result {
        data object Ok : Result
        data class Invalid(val message: String) : Result
    }

    private const val MIN_TIMEOUT = 1_000L
    private const val MAX_TIMEOUT = 300_000L
    private const val MAX_WAIT = 120_000L

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

                is UiStep.ClickElement ->
                    if (step.selector.isBlank) return invalid("$where: empty selector")

                is UiStep.LongClickElement ->
                    if (step.selector.isBlank) return invalid("$where: empty selector")

                is UiStep.Scroll ->
                    if (step.selector.isBlank) return invalid("$where: empty selector")

                is UiStep.SetText -> {
                    if (step.selector.isBlank) return invalid("$where: empty selector")
                    if (step.text.isEmpty()) return invalid("$where: no text to enter")
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

    private fun invalid(message: String) = Result.Invalid(message)
}
