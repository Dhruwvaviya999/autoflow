package com.dhruw.autoflow.services.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationMapperTest {

    @Test
    fun `maps all provided fields`() {
        val event = NotificationMapper.map(
            packageName = "com.whatsapp",
            notificationKey = "0|com.whatsapp|1|null|10145",
            postTime = 1_234L,
            title = "John",
            text = "Hi, there is a job opening in our company.",
            subText = "Work chat",
            category = "msg",
            isGroupSummary = false,
            isOngoing = false,
            resolveAppName = { "WhatsApp" }
        )

        assertEquals("com.whatsapp", event.packageName)
        assertEquals("WhatsApp", event.appName)
        assertEquals("John", event.title)
        assertEquals("Hi, there is a job opening in our company.", event.text)
        assertEquals("Work chat", event.subText)
        assertEquals(1_234L, event.timestamp)
        assertEquals("0|com.whatsapp|1|null|10145", event.notificationKey)
        assertEquals("msg", event.category)
        assertFalse(event.isGroupSummary)
    }

    @Test
    fun `null title, text, subText and category become empty strings`() {
        val event = NotificationMapper.map(
            packageName = "com.example",
            notificationKey = "key",
            postTime = 0L,
            title = null,
            text = null,
            subText = null,
            category = null,
            isGroupSummary = false,
            isOngoing = false
        )

        assertEquals("", event.title)
        assertEquals("", event.text)
        assertEquals("", event.subText)
        assertEquals("", event.category)
    }

    @Test
    fun `app name falls back to package name when unresolvable`() {
        val unresolved = NotificationMapper.map(
            packageName = "com.example",
            notificationKey = "key",
            postTime = 0L,
            title = null,
            text = null,
            subText = null,
            category = null,
            isGroupSummary = false,
            isOngoing = false,
            resolveAppName = { null }
        )
        assertEquals("com.example", unresolved.appName)

        val blank = NotificationMapper.map(
            packageName = "com.example",
            notificationKey = "key",
            postTime = 0L,
            title = null,
            text = null,
            subText = null,
            category = null,
            isGroupSummary = false,
            isOngoing = false,
            resolveAppName = { "  " }
        )
        assertEquals("com.example", blank.appName)
    }

    @Test
    fun `group summary flag is preserved`() {
        val event = NotificationMapper.map(
            packageName = "com.whatsapp",
            notificationKey = "key",
            postTime = 0L,
            title = "3 new messages",
            text = null,
            subText = null,
            category = null,
            isGroupSummary = true,
            isOngoing = false
        )
        assertTrue(event.isGroupSummary)
    }

    @Test
    fun `ongoing flag is preserved`() {
        val event = NotificationMapper.map(
            packageName = "com.example.clock",
            notificationKey = "key",
            postTime = 0L,
            title = "Timer",
            text = "04:59 remaining",
            subText = null,
            category = null,
            isGroupSummary = false,
            isOngoing = true
        )
        assertTrue(event.isOngoing)
    }

    @Test
    fun `charSequence content converts to plain string`() {
        val event = NotificationMapper.map(
            packageName = "com.example",
            notificationKey = "key",
            postTime = 0L,
            title = StringBuilder("Built title"),
            text = StringBuilder("Built text"),
            subText = null,
            category = null,
            isGroupSummary = false,
            isOngoing = false
        )
        assertEquals("Built title", event.title)
        assertEquals("Built text", event.text)
    }
}
