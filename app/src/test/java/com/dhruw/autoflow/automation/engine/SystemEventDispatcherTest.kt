package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.engine.handlers.LogActionHandler
import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.automation.model.ConnectionEvent
import com.dhruw.autoflow.automation.model.Execution
import com.dhruw.autoflow.automation.model.ExecutionStatus
import com.dhruw.autoflow.automation.model.LevelComparison
import com.dhruw.autoflow.automation.model.SystemEvent
import com.dhruw.autoflow.automation.model.Trigger
import com.dhruw.autoflow.data.repository.AutomationRepository
import com.dhruw.autoflow.data.repository.ExecutionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemEventDispatcherTest {

    private class FakeAutomationRepository(initial: List<Automation>) : AutomationRepository {
        private val items = initial.toMutableList()
        override val automations: StateFlow<List<Automation>> = MutableStateFlow(items.toList())
        override suspend fun getById(id: String) = items.firstOrNull { it.id == id }
        override suspend fun getAll() = items.toList()
        override suspend fun upsert(automation: Automation) = Unit
        override suspend fun delete(id: String) = Unit
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
        fun finished() = byId.values.filter { it.status != ExecutionStatus.RUNNING }
    }

    private fun automation(id: String, trigger: Trigger, enabled: Boolean = true) = Automation(
        id = id,
        name = "Automation $id",
        enabled = enabled,
        trigger = trigger,
        actions = listOf(Action.LogAction("ran $id")),
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun harness(automations: List<Automation>): Pair<AutomationEventDispatcher, FakeExecutionRepository> {
        val automationRepository = FakeAutomationRepository(automations)
        val executionRepository = FakeExecutionRepository()
        val runner = AutomationRunner(
            engine = AutomationEngine(handlers = listOf(LogActionHandler())),
            automationRepository = automationRepository,
            executionRepository = executionRepository
        )
        return AutomationEventDispatcher(automationRepository, runner) to executionRepository
    }

    @Test
    fun `charging event runs matching automation`() = runTest {
        val (dispatcher, executions) = harness(
            listOf(automation("a", Trigger.ChargingStateTrigger(onCharging = true)))
        )

        dispatcher.dispatch(SystemEvent.ChargingChanged(isCharging = true, batteryLevel = 50, timestamp = 1))

        assertEquals(1, executions.finished().size)
        assertEquals(ExecutionStatus.SUCCESS, executions.finished().single().status)
    }

    @Test
    fun `event type routes only to matching trigger types`() = runTest {
        val (dispatcher, executions) = harness(
            listOf(
                automation("charging", Trigger.ChargingStateTrigger(onCharging = true)),
                automation("screen", Trigger.ScreenStateTrigger(onScreenOn = true)),
                automation("manual", Trigger.ManualTrigger),
                automation("boot", Trigger.DeviceBootTrigger)
            )
        )

        dispatcher.dispatch(SystemEvent.ScreenChanged(on = true, timestamp = 1))

        val finished = executions.finished()
        assertEquals(1, finished.size)
        assertEquals("screen", finished.single().automationId)
    }

    @Test
    fun `battery crossing dispatches once with edge semantics`() = runTest {
        val (dispatcher, executions) = harness(
            listOf(automation("a", Trigger.BatteryLevelTrigger(LevelComparison.LESS_OR_EQUAL, 20)))
        )

        dispatcher.dispatch(SystemEvent.BatteryChanged(21, 25, false, 1)) // outside zone
        dispatcher.dispatch(SystemEvent.BatteryChanged(19, 21, false, 2)) // crossing
        dispatcher.dispatch(SystemEvent.BatteryChanged(18, 19, false, 3)) // inside: silent

        assertEquals(1, executions.finished().size)
    }

    @Test
    fun `boot event runs boot automation`() = runTest {
        val (dispatcher, executions) = harness(
            listOf(automation("boot", Trigger.DeviceBootTrigger))
        )

        dispatcher.dispatch(SystemEvent.DeviceBooted(timestamp = 1))

        assertEquals(1, executions.finished().size)
        assertEquals("boot", executions.finished().single().automationId)
    }

    @Test
    fun `disabled automation ignores system events`() = runTest {
        val (dispatcher, executions) = harness(
            listOf(automation("a", Trigger.DeviceBootTrigger, enabled = false))
        )

        dispatcher.dispatch(SystemEvent.DeviceBooted(timestamp = 1))

        assertTrue(executions.finished().isEmpty())
    }

    @Test
    fun `bluetooth event matches address-filtered automation only`() = runTest {
        val (dispatcher, executions) = harness(
            listOf(
                automation(
                    "car",
                    Trigger.BluetoothConnectionTrigger(ConnectionEvent.CONNECTED, "AA:BB", "Car")
                ),
                automation(
                    "buds",
                    Trigger.BluetoothConnectionTrigger(ConnectionEvent.CONNECTED, "CC:DD", "Buds")
                )
            )
        )

        dispatcher.dispatch(SystemEvent.BluetoothChanged(true, "AA:BB", "Car", 1))

        val finished = executions.finished()
        assertEquals(1, finished.size)
        assertEquals("car", finished.single().automationId)
    }
}
