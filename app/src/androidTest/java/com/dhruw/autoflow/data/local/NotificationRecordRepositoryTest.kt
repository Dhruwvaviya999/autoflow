package com.dhruw.autoflow.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dhruw.autoflow.automation.model.NotificationRecord
import com.dhruw.autoflow.data.repository.RoomNotificationRecordRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationRecordRepositoryTest {

    private lateinit var database: AutoFlowDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AutoFlowDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun record(id: String, timestamp: Long) = NotificationRecord(
        id = id,
        packageName = "com.whatsapp",
        appName = "WhatsApp",
        title = "John",
        text = "Test: job opening available",
        timestamp = timestamp,
        automationId = "auto-1"
    )

    @Test
    fun savedRecordRoundTrips() = runBlocking {
        val repository = RoomNotificationRecordRepository(database.notificationRecordDao())

        repository.save(record("r1", timestamp = 100L))

        val loaded = repository.getAll().single()
        assertEquals("r1", loaded.id)
        assertEquals("com.whatsapp", loaded.packageName)
        assertEquals("WhatsApp", loaded.appName)
        assertEquals("John", loaded.title)
        assertEquals("Test: job opening available", loaded.text)
        assertEquals(100L, loaded.timestamp)
        assertEquals("auto-1", loaded.automationId)
    }

    @Test
    fun retentionKeepsOnlyNewestRecords() = runBlocking {
        val repository = RoomNotificationRecordRepository(
            database.notificationRecordDao(),
            maxEntries = 5
        )

        repeat(8) { index ->
            repository.save(record("r$index", timestamp = index.toLong()))
        }

        val remaining = repository.getAll()
        assertEquals(5, remaining.size)
        // Newest first, oldest three pruned.
        assertEquals(listOf("r7", "r6", "r5", "r4", "r3"), remaining.map { it.id })
    }

    @Test
    fun clearRemovesEverything() = runBlocking {
        val repository = RoomNotificationRecordRepository(database.notificationRecordDao())
        repository.save(record("r1", 1L))
        repository.save(record("r2", 2L))

        repository.clear()

        assertTrue(repository.getAll().isEmpty())
    }
}
