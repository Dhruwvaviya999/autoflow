package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.engine.handlers.DelayActionHandler
import com.dhruw.autoflow.automation.engine.handlers.LogActionHandler
import com.dhruw.autoflow.automation.engine.handlers.SetVariableActionHandler
import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.automation.model.Condition
import com.dhruw.autoflow.automation.model.ExecutionStatus
import com.dhruw.autoflow.automation.model.TextMatchMode
import com.dhruw.autoflow.automation.model.Trigger
import com.dhruw.autoflow.automation.model.TriggerPayload
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 8: variables, action outputs, branching, disabled steps, groups. */
class VariableAndBranchTest {

    private val captured = mutableListOf<String>()

    private fun automation(actions: List<Action>) = Automation(
        id = "auto-1",
        name = "Phase 8 automation",
        trigger = Trigger.ManualTrigger,
        conditions = emptyList(),
        actions = actions,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun engine() = AutomationEngine(
        handlers = listOf(
            DelayActionHandler(),
            SetVariableActionHandler(),
            LogActionHandler(sink = { captured += it })
        )
    )

    private fun notification(title: String = "Hiring", text: String = "developer role") =
        TriggerPayload.NotificationEvent(
            packageName = "com.example.chat",
            appName = "Chat",
            title = title,
            text = text,
            subText = "",
            timestamp = 0L,
            notificationKey = "k",
            category = ""
        )

    @Test
    fun `set variable feeds a later action`() = runTest {
        val result = engine().execute(
            automation(
                listOf(
                    Action.SetVariableAction("jobType", "developer"),
                    Action.LogAction("Job type: {{jobType}}")
                )
            )
        )

        assertEquals(ExecutionStatus.SUCCESS, result.status)
        assertTrue(captured.contains("Job type: developer"))
    }

    @Test
    fun `variable value may itself use a payload template`() = runTest {
        engine().execute(
            automation(
                listOf(
                    Action.SetVariableAction("source", "{{notification.appName}}"),
                    Action.LogAction("From {{source}}")
                )
            ),
            payload = notification()
        )

        assertTrue(captured.contains("From Chat"))
    }

    @Test
    fun `automation name is available as a variable`() = runTest {
        engine().execute(automation(listOf(Action.LogAction("Run of {{automation.name}}"))))

        assertTrue(captured.contains("Run of Phase 8 automation"))
    }

    @Test
    fun `unknown variable fails the run with a clear message`() = runTest {
        val result = engine().execute(automation(listOf(Action.LogAction("{{nope}}"))))

        assertEquals(ExecutionStatus.FAILED, result.status)
        assertTrue(result.error!!.contains("nope"))
    }

    @Test
    fun `invalid variable name is rejected at run time`() = runTest {
        val result = engine().execute(
            automation(listOf(Action.SetVariableAction("2bad name", "x")))
        )

        assertEquals(ExecutionStatus.FAILED, result.status)
        assertTrue(result.error!!.contains("not a valid variable name"))
    }

    @Test
    fun `branch runs the then side when the condition passes`() = runTest {
        val result = engine().execute(
            automation(
                listOf(
                    Action.BranchAction(
                        condition = Condition.NotificationTextCondition("developer", TextMatchMode.CONTAINS),
                        thenActions = listOf(Action.LogAction("matched")),
                        elseActions = listOf(Action.LogAction("not matched"))
                    )
                )
            ),
            payload = notification()
        )

        assertEquals(ExecutionStatus.SUCCESS, result.status)
        assertTrue(captured.contains("matched"))
        assertFalse(captured.contains("not matched"))
    }

    @Test
    fun `branch runs the else side when the condition fails`() = runTest {
        engine().execute(
            automation(
                listOf(
                    Action.BranchAction(
                        condition = Condition.NotificationTextCondition("designer", TextMatchMode.CONTAINS),
                        thenActions = listOf(Action.LogAction("matched")),
                        elseActions = listOf(Action.LogAction("not matched"))
                    )
                )
            ),
            payload = notification()
        )

        assertTrue(captured.contains("not matched"))
        assertFalse(captured.contains("matched"))
    }

    @Test
    fun `branch without an else side simply continues`() = runTest {
        val result = engine().execute(
            automation(
                listOf(
                    Action.BranchAction(
                        condition = Condition.NotificationTextCondition("designer"),
                        thenActions = listOf(Action.LogAction("matched"))
                    ),
                    Action.LogAction("after")
                )
            ),
            payload = notification()
        )

        assertEquals(ExecutionStatus.SUCCESS, result.status)
        assertTrue(captured.contains("after"))
        assertFalse(captured.contains("matched"))
    }

    @Test
    fun `variables set inside a branch are visible afterwards`() = runTest {
        engine().execute(
            automation(
                listOf(
                    Action.BranchAction(
                        condition = Condition.AlwaysCondition,
                        thenActions = listOf(Action.SetVariableAction("inner", "value"))
                    ),
                    Action.LogAction("got {{inner}}")
                )
            )
        )

        assertTrue(captured.contains("got value"))
    }

    @Test
    fun `nested branches run to the allowed depth`() = runTest {
        val result = engine().execute(
            automation(
                listOf(
                    Action.BranchAction(
                        condition = Condition.AlwaysCondition,
                        thenActions = listOf(
                            Action.BranchAction(
                                condition = Condition.AlwaysCondition,
                                thenActions = listOf(Action.LogAction("deep"))
                            )
                        )
                    )
                )
            )
        )

        assertEquals(ExecutionStatus.SUCCESS, result.status)
        assertTrue(captured.contains("deep"))
    }

    @Test
    fun `disabled step is skipped and recorded`() = runTest {
        val result = engine().execute(
            automation(
                listOf(
                    Action.DisabledAction(Action.LogAction("should not run")),
                    Action.LogAction("runs")
                )
            )
        )

        assertEquals(ExecutionStatus.SUCCESS, result.status)
        assertFalse(captured.contains("should not run"))
        assertTrue(captured.contains("runs"))
        assertTrue(result.logs.any { it.contains("disabled") })
    }

    @Test
    fun `group marker is logged but executes nothing`() = runTest {
        val result = engine().execute(
            automation(
                listOf(
                    Action.GroupMarker("Preparation"),
                    Action.LogAction("step")
                )
            )
        )

        assertEquals(ExecutionStatus.SUCCESS, result.status)
        assertTrue(result.logs.any { it.contains("Preparation") })
    }
}
