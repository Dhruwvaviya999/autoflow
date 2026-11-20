package com.dhruw.autoflow.ui.automationeditor

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeakPreviewTextTest {

    @Test
    fun `plain text is spoken as written`() {
        assertEquals("Charging started", speakPreviewText("Charging started"))
    }

    @Test
    fun `a placeholder is replaced by its spoken name`() {
        assertEquals("Battery battery level percent", speakPreviewText("Battery {{battery.level}} percent"))
    }

    @Test
    fun `padded placeholders are matched too`() {
        assertEquals("automation name", speakPreviewText("{{  automation.name  }}"))
    }

    @Test
    fun `every placeholder in the text is replaced`() {
        assertEquals(
            "notification title from notification appName",
            speakPreviewText("{{notification.title}} from {{notification.appName}}")
        )
    }

    @Test
    fun `braces that are not a placeholder are left alone`() {
        assertEquals("{{ not a token }}", speakPreviewText("{{ not a token }}"))
    }
}
