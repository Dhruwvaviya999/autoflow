package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.engine.handlers.DelayActionHandler
import com.dhruw.autoflow.automation.engine.handlers.LogActionHandler
import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.automation.model.Condition
import com.dhruw.autoflow.automation.model.ExecutionStatus
import com.dhruw.autoflow.automation.model.Trigger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationEngineTest {

    private fun automation(
        actions: List<Action>,
        conditions: List<Condition> = listOf(Condition.AlwaysCondition),
        enabled: Boolean = true
    ) = Automation(
        id = "auto-1",
        name = "Test automation",
        enabled = enabled,
        trigger = Trigger.ManualTrigger,
        conditions = conditions,
        actions = actions,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun engine(vararg extraHandlers: ActionHandler) = AutomationEngine(
        handlers = listOf(DelayActionHandler(), LogActionHandler()) + extraHandlers
    )

    @Test
    fun `manual trigger with always condition and log action succeeds`() = runTest {
        val result = engine().execute(automation(listOf(Action.LogAction("hello"))))

        assertEquals(ExecutionStatus.SUCCESS, result.status)
        assertEquals(1, result.completedActions)
        assertTrue(result.logs.contains("hello"))
        assertNotNull(result.completedAt)
    }

    @Test
    fun `delay action succeeds without blocking`() = runTest {
        val result = engine().execute(
            automation(listOf(Action.DelayAction(durationMillis = 5_000)))
        )

        // runTest virtual time: a real 5 s wait would fail the test's timeout budget
        assertEquals(ExecutionStatus.SUCCESS, result.status)
        assertEquals(1, result.completedActions)
    }

    @Test
    fun `multiple actions execute in order`() = runTest {
        val order = mutableListOf<String>()
        val recordingHandler = object : ActionHandler {
            override fun canHandle(action: Action) = action is Action.LogAction
            override suspend fun execute(action: Action, context: ActionContext) {
                order += (action as Action.LogAction).message
            }
        }
        val engine = AutomationEngine(handlers = listOf(recordingHandler, DelayActionHandler()))

        val result = engine.execute(
            automation(
                listOf(
                    Action.LogAction("first"),
                    Action.DelayAction(100),
                    Action.LogAction("second"),
                    Action.LogAction("third")
                )
            )
        )

        assertEquals(ExecutionStatus.SUCCESS, result.status)
        assertEquals(listOf("first", "second", "third"), order)
        assertEquals(4, result.completedActions)
    }

    @Test
    fun `action failure marks execution failed with error details`() = runTest {
        val failingHandler = object : ActionHandler {
            override fun canHandle(action: Action) = action is Action.LogAction
            override suspend fun execute(action: Action, context: ActionContext) {
                throw ActionExecutionException("boom")
            }
        }
        val engine = AutomationEngine(handlers = listOf(failingHandler))

        val result = engine.execute(automation(listOf(Action.LogAction("x"))))

        assertEquals(ExecutionStatus.FAILED, result.status)
        assertEquals("boom", result.error)
        assertEquals(0, result.completedActions)
        assertTrue(result.message!!.contains("Log"))
    }

    @Test
    fun `failing condition skips actions`() = runTest {
        var executed = false
        val handler = object : ActionHandler {
            override fun canHandle(action: Action) = true
            override suspend fun execute(action: Action, context: ActionContext) {
                executed = true
            }
        }
        val engine = AutomationEngine(
            handlers = listOf(handler),
            conditionEvaluator = FailingConditionEvaluator()
        )

        val result = engine.execute(automation(listOf(Action.LogAction("x"))))

        assertEquals(ExecutionStatus.SKIPPED, result.status)
        assertEquals(false, executed)
        assertEquals(0, result.completedActions)
    }

    @Test
    fun `disabled automation is skipped`() = runTest {
        val result = engine().execute(
            automation(listOf(Action.LogAction("x")), enabled = false)
        )

        assertEquals(ExecutionStatus.SKIPPED, result.status)
        assertEquals("Automation is disabled", result.message)
    }

    @Test
    fun `automation without actions fails with clear error`() = runTest {
        val result = engine().execute(automation(emptyList()))

        assertEquals(ExecutionStatus.FAILED, result.status)
        assertEquals("Automation has no actions", result.error)
    }
}

private class FailingConditionEvaluator : ConditionEvaluator() {
    override fun evaluateAll(
        conditions: List<com.dhruw.autoflow.automation.model.Condition>,
        payload: com.dhruw.autoflow.automation.model.TriggerPayload?
    ) = false
}
