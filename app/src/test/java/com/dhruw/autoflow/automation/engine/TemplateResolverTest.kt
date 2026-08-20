package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.SystemEvent
import com.dhruw.autoflow.automation.model.TriggerPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateResolverTest {

    private val notification = TriggerPayload.NotificationEvent(
        packageName = "com.messenger.app",
        appName = "Messenger",
        title = "New job opening",
        text = "We are hiring an Android engineer",
        subText = "",
        timestamp = 1L,
        notificationKey = "key",
        category = ""
    )

    private val file = TriggerPayload.FileEvent(
        uri = "content://files/report.pdf",
        name = "report.pdf",
        extension = "pdf",
        sizeBytes = 100,
        detectedAt = 1L
    )

    @Test
    fun `static text passes through unchanged`() {
        val result = TemplateResolver.resolve("Hi, thanks for letting me know.", notification)
        assertEquals(
            "Hi, thanks for letting me know.",
            (result as TemplateResolver.Result.Ok).text
        )
    }

    @Test
    fun `notification title resolves`() {
        val result = TemplateResolver.resolve("Re: {{notification.title}}", notification)
        assertEquals("Re: New job opening", (result as TemplateResolver.Result.Ok).text)
    }

    @Test
    fun `notification text and app name resolve together`() {
        val result = TemplateResolver.resolve(
            "{{notification.appName}} says: {{notification.text}}",
            notification
        )
        assertEquals(
            "Messenger says: We are hiring an Android engineer",
            (result as TemplateResolver.Result.Ok).text
        )
    }

    @Test
    fun `file name resolves`() {
        val result = TemplateResolver.resolve("Got {{file.name}}", file)
        assertEquals("Got report.pdf", (result as TemplateResolver.Result.Ok).text)
    }

    @Test
    fun `battery level resolves from battery payload`() {
        val payload = SystemEvent.BatteryChanged(
            level = 42,
            previousLevel = 43,
            isCharging = true,
            timestamp = 1L
        )
        val result = TemplateResolver.resolve("Battery {{battery.level}}%", payload)
        assertEquals("Battery 42%", (result as TemplateResolver.Result.Ok).text)
    }

    @Test
    fun `unknown variable is rejected`() {
        val result = TemplateResolver.resolve("{{clipboard.text}}", notification)
        assertTrue(result is TemplateResolver.Result.UnknownVariable)
        assertEquals(
            "clipboard.text",
            (result as TemplateResolver.Result.UnknownVariable).variable
        )
    }

    @Test
    fun `known variable missing from payload is unavailable`() {
        val result = TemplateResolver.resolve("{{notification.title}}", file)
        assertTrue(result is TemplateResolver.Result.Unavailable)
    }

    @Test
    fun `null payload makes every variable unavailable`() {
        val result = TemplateResolver.resolve("{{notification.title}}", null)
        assertTrue(result is TemplateResolver.Result.Unavailable)
    }

    @Test
    fun `whitespace inside braces is tolerated`() {
        val result = TemplateResolver.resolve("{{ notification.title }}", notification)
        assertEquals("New job opening", (result as TemplateResolver.Result.Ok).text)
    }

    @Test
    fun `validate accepts known variables without a payload`() {
        assertTrue(
            TemplateResolver.validate("Hello {{notification.title}}")
                is TemplateResolver.Result.Ok
        )
    }

    @Test
    fun `validate flags unknown variables`() {
        val result = TemplateResolver.validate("{{secret.password}}")
        assertTrue(result is TemplateResolver.Result.UnknownVariable)
    }

    @Test
    fun `no code evaluation - unmatched braces stay literal`() {
        val result = TemplateResolver.resolve("{{not closed", notification)
        assertEquals("{{not closed", (result as TemplateResolver.Result.Ok).text)
    }
}
