package com.dhruw.autoflow.services.notification

import com.dhruw.autoflow.automation.engine.ActionContext
import com.dhruw.autoflow.automation.engine.ActionExecutionException
import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.NotificationRecord
import com.dhruw.autoflow.automation.model.TriggerPayload
import com.dhruw.autoflow.data.repository.NotificationRecordRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveNotificationActionHandlerTest {

    private class FakeRecordRepository : NotificationRecordRepository {
        val saved = mutableListOf<NotificationRecord>()
        override suspend fun save(record: NotificationRecord) {
            saved += record
        }
        override suspend fun getAll(): List<NotificationRecord> = saved.reversed()
        override suspend fun clear() = saved.clear()
    }

    private val event = TriggerPayload.NotificationEvent(
        packageName = "com.whatsapp",
        appName = "WhatsApp",
        title = "John",
        text = "Test: job opening available",
        subText = "",
        timestamp = 42L,
        notificationKey = "key-1",
        category = "msg"
    )

    @Test
    fun `handles only SaveNotificationAction`() {
        val handler = SaveNotificationActionHandler(FakeRecordRepository())
        assertTrue(handler.canHandle(Action.SaveNotificationAction))
        assertFalse(handler.canHandle(Action.LogAction("x")))
    }

    @Test
    fun `saves the triggering notification locally`() = runTest {
        val repository = FakeRecordRepository()
        val handler = SaveNotificationActionHandler(repository, newId = { "id-1" })
        val logs = mutableListOf<String>()

        handler.execute(
            Action.SaveNotificationAction,
            ActionContext(
                log = { logs += it },
                notificationEvent = event,
                automationId = "auto-1"
            )
        )

        val record = repository.saved.single()
        assertEquals("id-1", record.id)
        assertEquals("com.whatsapp", record.packageName)
        assertEquals("WhatsApp", record.appName)
        assertEquals("John", record.title)
        assertEquals("Test: job opening available", record.text)
        assertEquals(42L, record.timestamp)
        assertEquals("auto-1", record.automationId)
        // The execution log must not leak notification content.
        assertTrue(logs.none { it.contains("job opening") })
    }

    @Test
    fun `fails clearly without a notification payload`() = runTest {
        val repository = FakeRecordRepository()
        val handler = SaveNotificationActionHandler(repository)

        assertThrows(ActionExecutionException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.execute(Action.SaveNotificationAction, ActionContext(log = {}))
            }
        }
        assertTrue(repository.saved.isEmpty())
    }
}
