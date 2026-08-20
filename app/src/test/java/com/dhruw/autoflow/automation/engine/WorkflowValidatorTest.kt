package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.automation.model.Capability
import com.dhruw.autoflow.automation.model.Condition
import com.dhruw.autoflow.automation.model.Trigger
import com.dhruw.autoflow.automation.model.UiSelector
import com.dhruw.autoflow.automation.model.UiStep
import com.dhruw.autoflow.automation.model.requiredCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowValidatorTest {

    private val validator = WorkflowValidator()

    private fun automation(
        name: String = "Test",
        trigger: Trigger = Trigger.ManualTrigger,
        conditions: List<Condition> = emptyList(),
        actions: List<Action> = listOf(Action.LogAction("hi"))
    ) = Automation(
        id = "a",
        name = name,
        trigger = trigger,
        conditions = conditions,
        actions = actions,
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun `a simple workflow is valid`() {
        assertTrue(validator.validate(automation()).isValid)
    }

    @Test
    fun `a workflow without actions is an error`() {
        val report = validator.validate(automation(actions = emptyList()))

        assertFalse(report.isValid)
        assertTrue(report.errors.any { it.message.contains("at least one action") })
    }

    @Test
    fun `a workflow without a name is an error`() {
        assertFalse(validator.validate(automation(name = " ")).isValid)
    }

    @Test
    fun `only group labels is an error`() {
        val report = validator.validate(automation(actions = listOf(Action.GroupMarker("Setup"))))

        assertFalse(report.isValid)
    }

    @Test
    fun `all steps disabled is a warning not an error`() {
        val report = validator.validate(
            automation(actions = listOf(Action.DisabledAction(Action.LogAction("x"))))
        )

        assertTrue(report.isValid)
        assertTrue(report.warnings.any { it.message.contains("disabled") })
    }

    @Test
    fun `using a variable before it is set is an error`() {
        val report = validator.validate(
            automation(actions = listOf(Action.LogAction("{{jobType}}")))
        )

        assertFalse(report.isValid)
        assertTrue(report.errors.any { it.message.contains("jobType") })
    }

    @Test
    fun `using a variable after it is set is valid`() {
        val report = validator.validate(
            automation(
                actions = listOf(
                    Action.SetVariableAction("jobType", "developer"),
                    Action.LogAction("{{jobType}}")
                )
            )
        )

        assertTrue(report.isValid)
    }

    @Test
    fun `payload variables need no declaration`() {
        val report = validator.validate(
            automation(actions = listOf(Action.LogAction("{{notification.title}}")))
        )

        assertTrue(report.isValid)
    }

    @Test
    fun `a variable set only inside a branch is not exported to later steps`() {
        val report = validator.validate(
            automation(
                actions = listOf(
                    Action.BranchAction(
                        condition = Condition.AlwaysCondition,
                        thenActions = listOf(Action.SetVariableAction("inner", "v"))
                    ),
                    Action.LogAction("{{inner}}")
                )
            )
        )

        assertFalse(report.isValid)
    }

    @Test
    fun `an empty any-of group can never pass`() {
        val report = validator.validate(
            automation(conditions = listOf(Condition.OrCondition(emptyList())))
        )

        assertFalse(report.isValid)
    }

    @Test
    fun `condition nesting beyond the limit is an error`() {
        var condition: Condition = Condition.AlwaysCondition
        repeat(EngineLimits.MAX_CONDITION_DEPTH + 2) {
            condition = Condition.AndCondition(listOf(condition))
        }

        assertFalse(validator.validate(automation(conditions = listOf(condition))).isValid)
    }

    @Test
    fun `too many actions is an error`() {
        val many = List(EngineLimits.MAX_ACTIONS + 1) { Action.LogAction("x") }

        assertFalse(validator.validate(automation(actions = many)).isValid)
    }

    @Test
    fun `an unconfigured file trigger is an error`() {
        val report = validator.validate(
            automation(trigger = Trigger.FileTrigger(folderUri = "", folderLabel = ""))
        )

        assertFalse(report.isValid)
    }

    @Test
    fun `a catch-all notification trigger is a warning`() {
        val report = validator.validate(automation(trigger = Trigger.NotificationTrigger()))

        assertTrue(report.isValid)
        assertTrue(report.warnings.isNotEmpty())
    }

    @Test
    fun `required capabilities are reported as info`() {
        val report = validator.validate(
            automation(actions = listOf(Action.ShowNotificationAction("Hi", "There")))
        )

        assertTrue(report.infos.any { it.message.contains("post notifications") })
    }

    @Test
    fun `ui automation capabilities include accessibility`() {
        val automation = automation(
            actions = listOf(
                Action.UiAutomationAction(
                    targetPackage = "com.example",
                    steps = listOf(UiStep.ClickElement(UiSelector(text = "OK")))
                )
            )
        )

        assertTrue(automation.requiredCapabilities.contains(Capability.ACCESSIBILITY))
    }

    @Test
    fun `capabilities are collected from inside branches`() {
        val automation = automation(
            actions = listOf(
                Action.BranchAction(
                    condition = Condition.AlwaysCondition,
                    thenActions = listOf(Action.ShowNotificationAction("t", "m"))
                )
            )
        )

        assertEquals(setOf(Capability.POST_NOTIFICATIONS), automation.requiredCapabilities)
    }

    @Test
    fun `notification conditions require notification access`() {
        val automation = automation(
            conditions = listOf(
                Condition.AndCondition(listOf(Condition.NotificationTextCondition("job")))
            )
        )

        assertTrue(automation.requiredCapabilities.contains(Capability.NOTIFICATION_ACCESS))
    }
}
