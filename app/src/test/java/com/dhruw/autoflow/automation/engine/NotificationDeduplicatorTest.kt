package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.TriggerPayload
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDeduplicatorTest {

    private var now = 0L

    private fun deduplicator(maxEntries: Int = 128, windowMillis: Long = 60_000) =
        NotificationDeduplicator(maxEntries, windowMillis, clock = { now })

    private fun event(
        key: String = "key-1",
        packageName: String = "com.whatsapp",
        title: String = "John",
        text: String = "Message 1"
    ) = TriggerPayload.NotificationEvent(
        packageName = packageName,
        appName = "WhatsApp",
        title = title,
        text = text,
        subText = "",
        timestamp = now,
        notificationKey = key,
        category = ""
    )

    @Test
    fun `same notification is processed exactly once`() {
        val dedup = deduplicator()
        assertTrue(dedup.shouldProcess(event()))
        assertFalse(dedup.shouldProcess(event()))
        assertFalse(dedup.shouldProcess(event()))
    }

    @Test
    fun `different notifications process normally`() {
        val dedup = deduplicator()
        assertTrue(dedup.shouldProcess(event(key = "key-1")))
        assertTrue(dedup.shouldProcess(event(key = "key-2")))
        assertTrue(dedup.shouldProcess(event(key = "key-3", packageName = "org.telegram.messenger")))
    }

    @Test
    fun `update with new text is a new event`() {
        // WhatsApp-style: same notification key, text updated per message.
        val dedup = deduplicator()
        assertTrue(dedup.shouldProcess(event(text = "Message 1")))
        assertTrue(dedup.shouldProcess(event(text = "Message 2")))
        assertFalse(dedup.shouldProcess(event(text = "Message 2")))
    }

    @Test
    fun `identical notification fires again after the window expires`() {
        val dedup = deduplicator(windowMillis = 60_000)
        assertTrue(dedup.shouldProcess(event()))
        now += 59_000
        assertFalse(dedup.shouldProcess(event()))
        now += 61_000
        assertTrue(dedup.shouldProcess(event()))
    }

    @Test
    fun `cache is bounded and evicts oldest entries`() {
        val dedup = deduplicator(maxEntries = 2)
        assertTrue(dedup.shouldProcess(event(key = "a")))
        assertTrue(dedup.shouldProcess(event(key = "b")))
        assertTrue(dedup.shouldProcess(event(key = "c"))) // evicts "a"
        assertTrue(dedup.shouldProcess(event(key = "a"))) // no longer remembered
        assertFalse(dedup.shouldProcess(event(key = "c"))) // still remembered
    }
}
