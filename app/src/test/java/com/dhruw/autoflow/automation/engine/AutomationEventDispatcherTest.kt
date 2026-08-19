package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.engine.handlers.LogActionHandler
import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.automation.model.Condition
import com.dhruw.autoflow.automation.model.Execution
import com.dhruw.autoflow.automation.model.ExecutionStatus
import com.dhruw.autoflow.automation.model.Trigger
import com.dhruw.autoflow.automation.model.TriggerPayload
import com.dhruw.autoflow.data.repository.AutomationRepository
import com.dhruw.autoflow.data.repository.ExecutionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationEventDispatcherTest {

    private class FakeAutomationRepository(
        initial: List<Automation>
    ) : AutomationRepository {
        private val items = initial.toMutableList()
        override val automations: StateFlow<List<Automation>> = MutableStateFlow(items.toList())
        override suspend fun getById(id: String): Automation? = items.firstOrNull { it.id == id }
        override suspend fun getAll(): List<Automation> = items.toList()
        override suspend fun upsert(automation: Automation) {
            items.removeAll { it.id == automation.id }
            items += automation
        }
        override suspend fun delete(id: String) {
            items.removeAll { it.id == id }
        }
        override suspend fun setEnabled(id: String, enabled: Boolean) = Unit
        override suspend fun markRun(id: String, timestamp: Long) = Unit
    }

    private class FakeExecutionRepository : ExecutionRepository {
        val byId = linkedMapOf<String, Execution>()
        override val executions: StateFlow<List<Execution>> = MutableStateFlow(emptyList())
        override suspend fun record(execution: Execution) {
            byId[execution.id] = execution
        }
        override suspend fun clear() = byId.clear()
        fun finished(): List<Execution> =
            byId.values.filter { it.status != ExecutionStatus.RUNNING }
    }

    private fun automation(
        id: String,
        trigger: Trigger,
        conditions: List<Condition> = emptyList(),
        enabled: Boolean = true
    ) = Automation(
        id = id,
        name = "Automation $id",
        enabled = enabled,
        trigger = trigger,
        conditions = conditions,
        actions = listOf(Action.LogAction("ran $id")),
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun event(
        packageName: String = "com.whatsapp",
        title: String = "John",
        text: String = "Test: job opening available",
        key: String = "key-1",
        isGroupSummary: Boolean = false,
        isOngoing: Boolean = false
    ) = TriggerPayload.NotificationEvent(
        packageName = packageName,
        appName = "WhatsApp",
        title = title,
        text = text,
        subText = "",
        timestamp = 1_000L,
        notificationKey = key,
        category = "msg",
        isGroupSummary = isGroupSummary,
        isOngoing = isOngoing
    )

    private fun harness(
        automations: List<Automation>
    ): Triple<AutomationEventDispatcher, FakeExecutionRepository, FakeAutomationRepository> {
        val automationRepository = FakeAutomationRepository(automations)
        val executionRepository = FakeExecutionRepository()
        val runner = AutomationRunner(
            engine = AutomationEngine(handlers = listOf(LogActionHandler())),
            automationRepository = automationRepository,
            executionRepository = executionRepository
        )
        return Triple(
            AutomationEventDispatcher(automationRepository, runner),
            executionRepository,
            automationRepository
        )
    }

    @Test
    fun `matching automation executes through the engine`() = runTest {
        val (dispatcher, executions, _) = harness(
            listOf(
                automation(
                    "a",
                    Trigger.NotificationTrigger(allowedPackages = setOf("com.whatsapp"))
                )
            )
        )

        dispatcher.dispatch(event())

        assertEquals(1, executions.finished().size)
        assertEquals(ExecutionStatus.SUCCESS, executions.finished().single().status)
    }

    @Test
    fun `non-matching automation does not execute`() = runTest {
        val (dispatcher, executions, _) = harness(
            listOf(
                automation(
                    "a",
                    Trigger.NotificationTrigger(allowedPackages = setOf("com.google.android.gm"))
                )
            )
        )

        dispatcher.dispatch(event(packageName = "com.whatsapp"))

        assertTrue(executions.finished().isEmpty())
    }

    @Test
    fun `disabled automation is ignored`() = runTest {
        val (dispatcher, executions, _) = harness(
            listOf(automation("a", Trigger.NotificationTrigger(), enabled = false))
        )

        dispatcher.dispatch(event())

        assertTrue(executions.finished().isEmpty())
    }

    @Test
    fun `non-notification triggers are ignored`() = runTest {
        val (dispatcher, executions, _) = harness(
            listOf(
                automation("a", Trigger.ManualTrigger),
                automation("b", Trigger.TimeTrigger(8, 0, Trigger.TimeTrigger.Repeat.DAILY))
            )
        )

        dispatcher.dispatch(event())

        assertTrue(executions.finished().isEmpty())
    }

    @Test
    fun `only the automation whose filters match runs`() = runTest {
        // Test D analog: WhatsApp+"job" vs Gmail+"interview".
        val (dispatcher, executions, _) = harness(
            listOf(
                automation(
                    "whatsapp-job",
                    Trigger.NotificationTrigger(
                        allowedPackages = setOf("com.whatsapp"),
                        textPattern = "job"
                    )
                ),
                automation(
                    "gmail-interview",
                    Trigger.NotificationTrigger(
                        allowedPackages = setOf("com.google.android.gm"),
                        textPattern = "interview"
                    )
                )
            )
        )

        dispatcher.dispatch(event(packageName = "com.whatsapp", text = "New job for you"))

        val finished = executions.finished()
        assertEquals(1, finished.size)
        assertEquals("whatsapp-job", finished.single().automationId)
    }

    @Test
    fun `conditions are evaluated by the engine`() = runTest {
        val (dispatcher, executions, _) = harness(
            listOf(
                automation(
                    "a",
                    Trigger.NotificationTrigger(),
                    conditions = listOf(Condition.NotificationTextCondition("job opening"))
                )
            )
        )

        dispatcher.dispatch(event(text = "nothing relevant", key = "k1"))
        dispatcher.dispatch(event(text = "Test: job opening available", key = "k2"))

        val finished = executions.finished()
        assertEquals(2, finished.size)
        assertEquals(
            setOf(ExecutionStatus.SKIPPED, ExecutionStatus.SUCCESS),
            finished.map { it.status }.toSet()
        )
    }

    @Test
    fun `duplicate notification event executes only once`() = runTest {
        val (dispatcher, executions, _) = harness(
            listOf(automation("a", Trigger.NotificationTrigger()))
        )

        dispatcher.dispatch(event())
        dispatcher.dispatch(event())
        dispatcher.dispatch(event())

        assertEquals(1, executions.finished().size)
    }

    @Test
    fun `group summary notifications are not dispatched`() = runTest {
        val (dispatcher, executions, _) = harness(
            listOf(automation("a", Trigger.NotificationTrigger()))
        )

        dispatcher.dispatch(event(isGroupSummary = true))

        assertTrue(executions.finished().isEmpty())
    }

    @Test
    fun `ongoing notifications are not dispatched`() = runTest {
        // Timers/downloads/media re-post continuously with changing text —
        // acting on each update floods executions (seen on a real device).
        val (dispatcher, executions, _) = harness(
            listOf(automation("a", Trigger.NotificationTrigger()))
        )

        dispatcher.dispatch(event(text = "04:59 remaining", key = "t", isOngoing = true))
        dispatcher.dispatch(event(text = "04:58 remaining", key = "t", isOngoing = true))

        assertTrue(executions.finished().isEmpty())
    }

    @Test
    fun `file events are not handled by the dispatcher`() = runTest {
        val (dispatcher, executions, _) = harness(
            listOf(automation("a", Trigger.NotificationTrigger()))
        )

        dispatcher.dispatch(TriggerPayload.FileEvent("uri", "a.zip", "zip", 1L, 0L))

        assertTrue(executions.finished().isEmpty())
    }
}
