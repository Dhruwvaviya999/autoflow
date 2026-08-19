package com.dhruw.autoflow.automation.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTriggerTest {

    private fun event(
        packageName: String = "com.whatsapp",
        title: String = "John",
        text: String = "Hi, there is a job opening in our company."
    ) = TriggerPayload.NotificationEvent(
        packageName = packageName,
        appName = "WhatsApp",
        title = title,
        text = text,
        subText = "",
        timestamp = 1_000L,
        notificationKey = "0|com.whatsapp|1|null|10145",
        category = "msg"
    )

    @Test
    fun `any app trigger matches every package`() {
        val trigger = Trigger.NotificationTrigger()
        assertTrue(trigger.matches(event(packageName = "com.whatsapp")))
        assertTrue(trigger.matches(event(packageName = "com.google.android.gm")))
    }

    @Test
    fun `specific app trigger matches only that package`() {
        val trigger = Trigger.NotificationTrigger(allowedPackages = setOf("com.whatsapp"))
        assertTrue(trigger.matches(event(packageName = "com.whatsapp")))
        assertFalse(trigger.matches(event(packageName = "com.google.android.gm")))
    }

    @Test
    fun `text pattern filters case-insensitively`() {
        val trigger = Trigger.NotificationTrigger(textPattern = "JOB OPENING")
        assertTrue(trigger.matches(event(text = "There is a job opening here")))
        assertFalse(trigger.matches(event(text = "See you tomorrow")))
    }

    @Test
    fun `title pattern filters`() {
        val trigger = Trigger.NotificationTrigger(titlePattern = "interview")
        assertTrue(trigger.matches(event(title = "Interview scheduled")))
        assertFalse(trigger.matches(event(title = "Lunch?")))
    }

    @Test
    fun `package and text must both match`() {
        val trigger = Trigger.NotificationTrigger(
            allowedPackages = setOf("com.whatsapp"),
            textPattern = "job opening"
        )
        assertTrue(trigger.matches(event()))
        assertFalse(trigger.matches(event(packageName = "org.telegram.messenger")))
        assertFalse(trigger.matches(event(text = "unrelated")))
    }

    @Test
    fun `equals and startsWith match modes apply to patterns`() {
        val equals = Trigger.NotificationTrigger(
            titlePattern = "john",
            matchMode = TextMatchMode.EQUALS
        )
        assertTrue(equals.matches(event(title = "John")))
        assertFalse(equals.matches(event(title = "John Doe")))

        val startsWith = Trigger.NotificationTrigger(
            textPattern = "job",
            matchMode = TextMatchMode.STARTS_WITH
        )
        assertTrue(startsWith.matches(event(text = "Job opening available")))
        assertFalse(startsWith.matches(event(text = "New job opening")))
    }

    @Test
    fun `blank patterns match everything`() {
        val trigger = Trigger.NotificationTrigger(titlePattern = "  ", textPattern = "")
        assertTrue(trigger.matches(event(title = "", text = "")))
    }
}
