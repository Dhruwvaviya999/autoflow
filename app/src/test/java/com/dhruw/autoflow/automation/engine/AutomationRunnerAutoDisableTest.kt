package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.automation.model.Execution
import com.dhruw.autoflow.automation.model.ExecutionStatus
import com.dhruw.autoflow.automation.model.Trigger
import com.dhruw.autoflow.data.repository.AutomationRepository
import com.dhruw.autoflow.data.repository.ExecutionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The auto-disable policy against a history flow that lags behind the write,
 * which is how the real Room-backed repository behaves: `record` writes the
 * row, but the observing flow has usually not re-emitted by the time the
 * runner asks for the count. The policy must still count the run that just
 * failed, otherwise "after 3 failed runs" only fires on the fourth.
 */
class AutomationRunnerAutoDisableTest {

    /** Publishes recorded executions only when [flush] is called. */
    private class LaggingExecutionRepository : ExecutionRepository {
        private val written = linkedMapOf<String, Execution>()
        private val state = MutableStateFlow<List<Execution>>(emptyList())
        override val executions: StateFlow<List<Execution>> = state

        override suspend fun record(execution: Execution) {
            written[execution.id] = execution
        }

        override suspend fun clear() {
            written.clear()
            state.value = emptyList()
        }

        /** Mimics the database flow catching up, newest first. */
        fun flush() {
            state.value = written.values.sortedByDescending { it.startedAt }
        }
    }

    private class RecordingAutomationRepository(
        initial: List<Automation>
    ) : AutomationRepository {
        private val items = initial.toMutableList()
        override val automations: StateFlow<List<Automation>> = MutableStateFlow(items.toList())
        var disabledId: String? = null
            private set

        override suspend fun getById(id: String): Automation? = items.firstOrNull { it.id == id }
        override suspend fun getAll(): List<Automation> = items.toList()
        override suspend fun upsert(automation: Automation) {
            items.removeAll { it.id == automation.id }
            items += automation
        }

        override suspend fun delete(id: String) {
            items.removeAll { it.id == id }
        }

        override suspend fun setEnabled(id: String, enabled: Boolean) {
            if (!enabled) disabledId = id
        }

        override suspend fun markRun(id: String, timestamp: Long) = Unit
    }

    /** Every action fails: no handler is registered for it. */
    private fun failingAutomation(threshold: Int?) = Automation(
        id = "a",
        name = "Always fails",
        trigger = Trigger.ManualTrigger,
        actions = listOf(Action.LogAction("never runs")),
        createdAt = 0L,
        updatedAt = 0L,
        disableAfterFailures = threshold
    )

    private fun harness(
        threshold: Int?
    ): Triple<AutomationRunner, RecordingAutomationRepository, LaggingExecutionRepository> {
        val automation = failingAutomation(threshold)
        val automations = RecordingAutomationRepository(listOf(automation))
        val executions = LaggingExecutionRepository()
        val runner = AutomationRunner(
            engine = AutomationEngine(handlers = emptyList()),
            automationRepository = automations,
            executionRepository = executions
        )
        return Triple(runner, automations, executions)
    }

    @Test
    fun `the threshold counts the run that just failed`() = runTest {
        val (runner, automations, executions) = harness(threshold = 3)
        val automation = automations.getAll().single()

        val statuses = mutableListOf<ExecutionStatus>()
        repeat(2) {
            statuses += runner.run(automation).status
            executions.flush()
        }
        assertTrue(statuses.all { it == ExecutionStatus.FAILED })
        assertNull("disabled too early", automations.disabledId)

        assertEquals(ExecutionStatus.FAILED, runner.run(automation).status)
        assertEquals("a", automations.disabledId)
    }

    @Test
    fun `no threshold never switches the automation off`() = runTest {
        val (runner, automations, executions) = harness(threshold = null)
        val automation = automations.getAll().single()

        repeat(5) {
            runner.run(automation)
            executions.flush()
        }

        assertNull(automations.disabledId)
    }
}
