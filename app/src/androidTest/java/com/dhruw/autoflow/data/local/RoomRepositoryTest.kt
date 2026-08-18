package com.dhruw.autoflow.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.automation.model.Condition
import com.dhruw.autoflow.automation.model.Execution
import com.dhruw.autoflow.automation.model.ExecutionStatus
import com.dhruw.autoflow.automation.model.Trigger
import com.dhruw.autoflow.data.local.entity.AutomationEntity
import com.dhruw.autoflow.data.repository.RoomAutomationRepository
import com.dhruw.autoflow.data.repository.RoomExecutionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomRepositoryTest {

    private lateinit var database: AutoFlowDatabase
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AutoFlowDatabase::class.java
        ).build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
    }

    private fun automation(
        id: String = "auto-1",
        enabled: Boolean = true,
        trigger: Trigger = Trigger.TimeTrigger(18, 0, Trigger.TimeTrigger.Repeat.ONCE)
    ) = Automation(
        id = id,
        name = "Test Notification",
        description = "desc",
        enabled = enabled,
        trigger = trigger,
        conditions = listOf(Condition.AlwaysCondition),
        actions = listOf(
            Action.ShowNotificationAction("Hello", "World"),
            Action.DelayAction(1_000),
            Action.LogAction("done")
        ),
        createdAt = 100L,
        updatedAt = 200L,
        lastRunAt = null
    )

    private fun execution(id: String = "exec-1", startedAt: Long = 1_000L) = Execution(
        id = id,
        automationId = "auto-1",
        automationName = "Test Notification",
        startedAt = startedAt,
        completedAt = startedAt + 500,
        status = ExecutionStatus.SUCCESS,
        totalActions = 3,
        completedActions = 3,
        currentAction = null,
        message = "3 of 3 actions completed",
        error = null,
        logs = listOf("Started", "Completed")
    )

    // --- Automations ---

    @Test
    fun automationPersistsAndRoundTripsThroughRepository() = runBlocking {
        val repo = RoomAutomationRepository(database.automationDao(), scope)
        val original = automation()

        repo.upsert(original)

        assertEquals(original, repo.getById("auto-1"))
    }

    @Test
    fun updatePersistsChanges() = runBlocking {
        val repo = RoomAutomationRepository(database.automationDao(), scope)
        repo.upsert(automation())

        val updated = automation().copy(
            name = "Renamed",
            trigger = Trigger.TimeTrigger(8, 30, Trigger.TimeTrigger.Repeat.DAILY),
            updatedAt = 300L
        )
        repo.upsert(updated)

        assertEquals(updated, repo.getById("auto-1"))
        assertEquals(1, database.automationDao().observeAll().first().size)
    }

    @Test
    fun deleteRemovesAutomation() = runBlocking {
        val repo = RoomAutomationRepository(database.automationDao(), scope)
        repo.upsert(automation())

        repo.delete("auto-1")

        assertNull(repo.getById("auto-1"))
        assertTrue(database.automationDao().observeAll().first().isEmpty())
    }

    @Test
    fun setEnabledAndMarkRunPersist() = runBlocking {
        val repo = RoomAutomationRepository(database.automationDao(), scope)
        repo.upsert(automation())

        repo.setEnabled("auto-1", false)
        repo.markRun("auto-1", 999L)

        val loaded = repo.getById("auto-1")!!
        assertEquals(false, loaded.enabled)
        assertEquals(999L, loaded.lastRunAt)
    }

    @Test
    fun malformedRowIsDroppedInsteadOfCrashing() = runBlocking {
        val repo = RoomAutomationRepository(database.automationDao(), scope)
        repo.upsert(automation(id = "good"))
        database.automationDao().upsert(
            AutomationEntity(
                id = "bad",
                name = "Corrupted",
                description = "",
                enabled = true,
                triggerJson = """{"type":"from_the_future"}""",
                conditionsJson = "[]",
                actionsJson = "[]",
                createdAt = 0L,
                updatedAt = 0L,
                lastRunAt = null
            )
        )

        assertNull(repo.getById("bad"))
        val visible = database.automationDao().observeAll().first()
        assertEquals(2, visible.size) // both rows stored...
        assertEquals("good", repo.getById("good")?.id) // ...only the good one decodes
    }

    // --- Executions ---

    @Test
    fun executionPersistsAndRoundTrips() = runBlocking {
        val repo = RoomExecutionRepository(database.executionDao(), scope)
        val original = execution()

        repo.record(original)

        val loaded = database.executionDao().observeAll().first()
        assertEquals(1, loaded.size)
        assertEquals(original.id, loaded.first().id)
        assertEquals(original.status.name, loaded.first().status)
    }

    @Test
    fun recordingSameIdUpdatesInsteadOfDuplicating() = runBlocking {
        val repo = RoomExecutionRepository(database.executionDao(), scope)
        repo.record(execution().copy(status = ExecutionStatus.RUNNING, completedAt = null))
        repo.record(execution())

        val rows = database.executionDao().observeAll().first()
        assertEquals(1, rows.size)
        assertEquals(ExecutionStatus.SUCCESS.name, rows.first().status)
    }

    @Test
    fun historyIsCappedAtMaxEntriesNewestFirst() = runBlocking {
        val repo = RoomExecutionRepository(database.executionDao(), scope, maxEntries = 5)
        repeat(8) { i -> repo.record(execution(id = "exec-$i", startedAt = i * 1_000L)) }

        val rows = database.executionDao().observeAll().first()
        assertEquals(5, rows.size)
        assertEquals("exec-7", rows.first().id) // newest kept, oldest pruned
        assertEquals("exec-3", rows.last().id)
    }

    @Test
    fun clearRemovesAllHistory() = runBlocking {
        val repo = RoomExecutionRepository(database.executionDao(), scope)
        repo.record(execution())

        repo.clear()

        assertTrue(database.executionDao().observeAll().first().isEmpty())
    }
}
