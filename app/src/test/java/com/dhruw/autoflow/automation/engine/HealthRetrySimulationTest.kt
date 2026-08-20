package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.automation.model.Capability
import com.dhruw.autoflow.automation.model.Condition
import com.dhruw.autoflow.automation.model.Execution
import com.dhruw.autoflow.automation.model.ExecutionStatus
import com.dhruw.autoflow.automation.model.LevelComparison
import com.dhruw.autoflow.automation.model.TextMatchMode
import com.dhruw.autoflow.automation.model.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthRetrySimulationTest {

    private val calculator = AutomationHealthCalculator()

    private fun automation(
        enabled: Boolean = true,
        trigger: Trigger = Trigger.ManualTrigger,
        actions: List<Action> = listOf(Action.LogAction("hi"))
    ) = Automation(
        id = "a",
        name = "Test",
        enabled = enabled,
        trigger = trigger,
        actions = actions,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun execution(status: ExecutionStatus) = Execution(
        id = "e",
        automationId = "a",
        automationName = "Test",
        startedAt = 0L,
        status = status,
        totalActions = 1
    )

    // --- Health ---

    @Test
    fun `a valid enabled automation with no failures is healthy`() {
        val health = calculator.calculate(automation(), emptyList(), emptySet())

        assertEquals(HealthStatus.HEALTHY, health.status)
    }

    @Test
    fun `a disabled automation reports disabled`() {
        val health = calculator.calculate(automation(enabled = false), emptyList(), emptySet())

        assertEquals(HealthStatus.DISABLED, health.status)
    }

    @Test
    fun `a missing capability is reported before failures`() {
        val health = calculator.calculate(
            automation(actions = listOf(Action.ShowNotificationAction("t", "m"))),
            listOf(execution(ExecutionStatus.FAILED), execution(ExecutionStatus.FAILED)),
            grantedCapabilities = emptySet()
        )

        assertEquals(HealthStatus.NEEDS_PERMISSION, health.status)
        assertTrue(health.missingCapabilities.contains(Capability.POST_NOTIFICATIONS))
    }

    @Test
    fun `a broken workflow reports a configuration issue`() {
        val health = calculator.calculate(
            automation(actions = emptyList()),
            emptyList(),
            emptySet()
        )

        assertEquals(HealthStatus.CONFIGURATION_ISSUE, health.status)
    }

    @Test
    fun `three consecutive failures mark an automation as failing`() {
        val health = calculator.calculate(
            automation(),
            List(3) { execution(ExecutionStatus.FAILED) },
            emptySet()
        )

        assertEquals(HealthStatus.FAILING, health.status)
        assertEquals(3, health.consecutiveFailures)
    }

    @Test
    fun `a later success resets the failure streak`() {
        val health = calculator.calculate(
            automation(),
            listOf(
                execution(ExecutionStatus.FAILED),
                execution(ExecutionStatus.SUCCESS),
                execution(ExecutionStatus.FAILED),
                execution(ExecutionStatus.FAILED)
            ),
            emptySet()
        )

        assertEquals(HealthStatus.HEALTHY, health.status)
        assertEquals(1, health.consecutiveFailures)
    }

    @Test
    fun `cancelled runs do not count as failures`() {
        assertEquals(
            0,
            calculator.countConsecutiveFailures(
                listOf(execution(ExecutionStatus.CANCELLED), execution(ExecutionStatus.SUCCESS))
            )
        )
    }

    // --- Retry policy ---

    @Test
    fun `idempotent actions are retryable`() {
        assertTrue(RetryPolicy.isRetryable(Action.LogAction("x")))
        assertTrue(RetryPolicy.isRetryable(Action.ShowNotificationAction("t", "m")))
        assertTrue(RetryPolicy.isRetryable(Action.SaveNotificationAction))
    }

    @Test
    fun `consequential actions are never retried`() {
        assertFalse(RetryPolicy.isRetryable(Action.MoveFileAction("uri", "label")))
        assertFalse(RetryPolicy.isRetryable(Action.RenameFileAction("new")))
        assertFalse(RetryPolicy.isRetryable(Action.CopyFileAction("uri", "label")))
        assertFalse(RetryPolicy.isRetryable(Action.UiAutomationAction(targetPackage = "com.x")))
    }

    @Test
    fun `a disabled wrapper does not make an action retryable`() {
        assertFalse(RetryPolicy.isRetryable(Action.DisabledAction(Action.MoveFileAction("u", "l"))))
    }

    @Test
    fun `retry count comes from the limits`() {
        assertEquals(EngineLimits.MAX_RETRIES, RetryPolicy.retriesFor(Action.LogAction("x")))
        assertEquals(0, RetryPolicy.retriesFor(Action.RenameFileAction("n")))
    }

    // --- Simulation ---

    private val simulator = SimulationEngine()

    @Test
    fun `a matching notification would run the automation`() {
        val result = simulator.simulate(
            automation(
                trigger = Trigger.NotificationTrigger(allowedPackages = setOf("com.example.chat"))
            ).copy(
                conditions = listOf(Condition.NotificationTextCondition("job", TextMatchMode.CONTAINS))
            ),
            SimulationPayloads.notification(
                packageName = "com.example.chat",
                appName = "Chat",
                title = "Hiring",
                text = "there is a job opening",
                timestamp = 0L
            )
        )

        assertTrue(result.wouldRun)
        assertTrue(result.steps.all { it.passed })
        assertEquals(1, result.plannedActions.size)
    }

    @Test
    fun `a non-matching condition stops the simulation`() {
        val result = simulator.simulate(
            automation(trigger = Trigger.NotificationTrigger()).copy(
                conditions = listOf(Condition.NotificationTextCondition("vacancy"))
            ),
            SimulationPayloads.notification("com.x", "X", "Hi", "unrelated text", 0L)
        )

        assertFalse(result.wouldRun)
        assertTrue(result.plannedActions.isEmpty())
    }

    @Test
    fun `a wrong app does not match the trigger`() {
        val result = simulator.simulate(
            automation(trigger = Trigger.NotificationTrigger(allowedPackages = setOf("com.wanted"))),
            SimulationPayloads.notification("com.other", "Other", "t", "x", 0L)
        )

        assertFalse(result.wouldRun)
        assertFalse(result.steps.first().passed)
    }

    @Test
    fun `battery simulation respects the crossing rule`() {
        val automation = automation(
            trigger = Trigger.BatteryLevelTrigger(LevelComparison.LESS_OR_EQUAL, 20)
        )

        val crossing = simulator.simulate(
            automation,
            SimulationPayloads.battery(level = 19, previousLevel = 21, charging = false, timestamp = 0L)
        )
        val alreadyBelow = simulator.simulate(
            automation,
            SimulationPayloads.battery(level = 18, previousLevel = 19, charging = false, timestamp = 0L)
        )

        assertTrue(crossing.wouldRun)
        assertFalse(alreadyBelow.wouldRun)
    }

    @Test
    fun `simulation shows which branch would run`() {
        val result = simulator.simulate(
            automation(
                trigger = Trigger.NotificationTrigger(),
                actions = listOf(
                    Action.BranchAction(
                        condition = Condition.NotificationTextCondition("job"),
                        thenActions = listOf(Action.LogAction("hit")),
                        elseActions = listOf(Action.LogAction("miss"))
                    )
                )
            ),
            SimulationPayloads.notification("com.x", "X", "t", "a job offer", 0L)
        )

        assertTrue(result.plannedActions.any { it.contains("THEN") })
        assertTrue(result.plannedActions.any { it.contains("hit") })
    }

    @Test
    fun `simulation marks disabled steps`() {
        val result = simulator.simulate(
            automation(actions = listOf(Action.DisabledAction(Action.LogAction("x")))),
            payload = null
        )

        assertTrue(result.plannedActions.single().contains("disabled"))
    }

    // --- Failure diagnostics ---

    @Test
    fun `a selector failure explains what to try`() {
        val explanation = FailureDiagnostics.explain("Could not find text is \"Send\"")

        assertTrue(explanation.headline.contains("could not be found"))
        assertTrue(explanation.possibleReasons.isNotEmpty())
        assertTrue(explanation.suggestedAction!!.contains("Test selector"))
    }

    @Test
    fun `an unknown failure still produces an honest explanation`() {
        val explanation = FailureDiagnostics.explain("something unexpected")

        assertEquals("The run failed", explanation.headline)
        assertEquals("something unexpected", explanation.detail)
    }

    @Test
    fun `a blank failure message is handled`() {
        assertTrue(FailureDiagnostics.explain(null).detail.isNotBlank())
    }
}
