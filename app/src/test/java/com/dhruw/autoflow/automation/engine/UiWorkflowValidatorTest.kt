package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.UiSelector
import com.dhruw.autoflow.automation.model.UiStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiWorkflowValidatorTest {

    private fun action(
        targetPackage: String = "com.example.app",
        steps: List<UiStep> = listOf(UiStep.ClickElement(UiSelector(text = "Demo"))),
        timeoutMillis: Long = 60_000
    ) = Action.UiAutomationAction(
        targetPackage = targetPackage,
        steps = steps,
        overallTimeoutMillis = timeoutMillis
    )

    private fun assertInvalid(action: Action.UiAutomationAction, contains: String? = null) {
        val result = UiWorkflowValidator.validate(action)
        assertTrue("Expected Invalid, was $result", result is UiWorkflowValidator.Result.Invalid)
        if (contains != null) {
            val message = (result as UiWorkflowValidator.Result.Invalid).message
            assertTrue(
                "Expected \"$contains\" in \"$message\"",
                message.contains(contains, ignoreCase = true)
            )
        }
    }

    @Test
    fun `valid workflow passes`() {
        val result = UiWorkflowValidator.validate(
            action(
                steps = listOf(
                    UiStep.LaunchApp("com.example.app"),
                    UiStep.WaitForElement(UiSelector(text = "Demo")),
                    UiStep.ClickElement(UiSelector(text = "Demo")),
                    UiStep.SetText(UiSelector(viewId = "input"), "Hello {{notification.title}}"),
                    UiStep.RequireUserConfirmation("Send?"),
                    UiStep.GlobalBack
                )
            )
        )
        assertEquals(UiWorkflowValidator.Result.Ok, result)
    }

    @Test
    fun `missing target package is invalid`() {
        assertInvalid(action(targetPackage = ""), contains = "target app")
    }

    @Test
    fun `empty steps are invalid`() {
        assertInvalid(action(steps = emptyList()), contains = "at least one step")
    }

    @Test
    fun `timeout out of range is invalid`() {
        assertInvalid(action(timeoutMillis = 500))
        assertInvalid(action(timeoutMillis = 301_000))
    }

    @Test
    fun `blank selectors are invalid for every element step`() {
        assertInvalid(action(steps = listOf(UiStep.ClickElement(UiSelector()))))
        assertInvalid(action(steps = listOf(UiStep.LongClickElement(UiSelector()))))
        assertInvalid(action(steps = listOf(UiStep.WaitForElement(UiSelector()))))
        assertInvalid(
            action(
                steps = listOf(
                    UiStep.Scroll(
                        UiSelector(),
                        com.dhruw.autoflow.automation.model.ScrollDirection.FORWARD
                    )
                )
            )
        )
        assertInvalid(action(steps = listOf(UiStep.SetText(UiSelector(), "hi"))))
    }

    @Test
    fun `launch step without package is invalid`() {
        assertInvalid(action(steps = listOf(UiStep.LaunchApp(""))))
    }

    @Test
    fun `wait-for-element timeout must be in range`() {
        assertInvalid(
            action(
                steps = listOf(
                    UiStep.WaitForElement(UiSelector(text = "x"), timeoutMillis = 500)
                )
            )
        )
        assertInvalid(
            action(
                steps = listOf(
                    UiStep.WaitForElement(UiSelector(text = "x"), timeoutMillis = 200_000)
                )
            )
        )
    }

    @Test
    fun `set text with unknown template variable is invalid`() {
        assertInvalid(
            action(
                steps = listOf(
                    UiStep.SetText(UiSelector(viewId = "input"), "{{clipboard.content}}")
                )
            ),
            contains = "unknown variable"
        )
    }

    @Test
    fun `set text with empty text is invalid`() {
        assertInvalid(action(steps = listOf(UiStep.SetText(UiSelector(viewId = "input"), ""))))
    }

    @Test
    fun `payment-looking tap without confirmation does not save`() {
        assertInvalid(
            action(steps = listOf(UiStep.ClickElement(UiSelector(text = "Pay now")))),
            contains = "Require confirmation"
        )
        assertInvalid(
            action(steps = listOf(UiStep.ClickElement(UiSelector(text = "Confirm payment"))))
        )
        assertInvalid(
            action(steps = listOf(UiStep.ClickElement(UiSelector(contentDescription = "Buy"))))
        )
    }

    @Test
    fun `payment-looking tap directly after confirmation is allowed`() {
        val result = UiWorkflowValidator.validate(
            action(
                steps = listOf(
                    UiStep.RequireUserConfirmation("Really pay?"),
                    UiStep.ClickElement(UiSelector(text = "Pay now"))
                )
            )
        )
        assertEquals(UiWorkflowValidator.Result.Ok, result)
    }

    @Test
    fun `ordinary words containing pay-like fragments are not flagged`() {
        // "display" contains "pay" but must not trip the word-bounded rule.
        val result = UiWorkflowValidator.validate(
            action(steps = listOf(UiStep.ClickElement(UiSelector(text = "Display settings"))))
        )
        assertEquals(UiWorkflowValidator.Result.Ok, result)
    }

    @Test
    fun `set text into password-looking selector never saves`() {
        assertInvalid(
            action(
                steps = listOf(UiStep.SetText(UiSelector(text = "Password"), "hunter2"))
            ),
            contains = "password"
        )
        assertInvalid(
            action(
                steps = listOf(UiStep.SetText(UiSelector(viewId = "otp_input"), "123456"))
            )
        )
        assertInvalid(
            action(
                steps = listOf(UiStep.SetText(UiSelector(contentDescription = "CVV"), "000"))
            )
        )
    }

    @Test
    fun `wait duration out of range is invalid`() {
        assertInvalid(action(steps = listOf(UiStep.Wait(-1))))
        assertInvalid(action(steps = listOf(UiStep.Wait(500_000))))
    }

    @Test
    fun `warning when final tap after text entry lacks confirmation`() {
        val warnings = UiWorkflowValidator.warnings(
            action(
                steps = listOf(
                    UiStep.SetText(UiSelector(viewId = "message_input"), "Hello"),
                    UiStep.ClickElement(UiSelector(viewId = "submit"))
                )
            )
        )
        assertTrue(warnings.any { it.contains("confirmation", ignoreCase = true) })
    }

    @Test
    fun `no consequential warning when confirmation guards the final tap`() {
        val warnings = UiWorkflowValidator.warnings(
            action(
                steps = listOf(
                    UiStep.SetText(UiSelector(viewId = "message_input"), "Hello"),
                    UiStep.RequireUserConfirmation("Send?"),
                    UiStep.ClickElement(UiSelector(viewId = "submit"))
                )
            )
        )
        assertTrue(warnings.none { it.contains("send or submit", ignoreCase = true) })
    }
}
