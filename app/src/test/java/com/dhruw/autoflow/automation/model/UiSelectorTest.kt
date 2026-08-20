package com.dhruw.autoflow.automation.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiSelectorTest {

    private fun UiSelector.matchesNode(
        viewId: String? = null,
        description: String? = null,
        text: String? = null,
        className: String? = null
    ): Boolean = matches(viewId, description, text, className)

    @Test
    fun `text equals matches exact text case-insensitively`() {
        val selector = UiSelector(text = "Send", matchMode = TextMatchMode.EQUALS)
        assertTrue(selector.matchesNode(text = "send"))
        assertFalse(selector.matchesNode(text = "Send message"))
    }

    @Test
    fun `text contains matches substring`() {
        val selector = UiSelector(text = "job", matchMode = TextMatchMode.CONTAINS)
        assertTrue(selector.matchesNode(text = "New job opening"))
        assertFalse(selector.matchesNode(text = "vacancy"))
    }

    @Test
    fun `text starts-with matches prefix only`() {
        val selector = UiSelector(text = "Open", matchMode = TextMatchMode.STARTS_WITH)
        assertTrue(selector.matchesNode(text = "Opening hours"))
        assertFalse(selector.matchesNode(text = "Re-Open"))
    }

    @Test
    fun `content description matches with match mode`() {
        val selector = UiSelector(contentDescription = "Send", matchMode = TextMatchMode.EQUALS)
        assertTrue(selector.matchesNode(description = "Send"))
        assertFalse(selector.matchesNode(description = "Send button"))
        assertFalse(selector.matchesNode(text = "Send")) // wrong attribute
    }

    @Test
    fun `full view id matches exactly`() {
        val selector = UiSelector(viewId = "com.example:id/send_button")
        assertTrue(selector.matchesNode(viewId = "com.example:id/send_button"))
        assertFalse(selector.matchesNode(viewId = "com.example:id/other"))
    }

    @Test
    fun `bare view id matches resource name suffix`() {
        val selector = UiSelector(viewId = "send_button")
        assertTrue(selector.matchesNode(viewId = "com.example:id/send_button"))
        assertTrue(selector.matchesNode(viewId = "send_button"))
        assertFalse(selector.matchesNode(viewId = "com.example:id/send_button_2"))
    }

    @Test
    fun `full selector id never matches a different full node id`() {
        val selector = UiSelector(viewId = "com.a:id/send")
        assertFalse(selector.matchesNode(viewId = "com.b:id/send"))
    }

    @Test
    fun `class name matches ignoring case`() {
        val selector = UiSelector(className = "android.widget.Button")
        assertTrue(selector.matchesNode(className = "android.widget.button"))
        assertFalse(selector.matchesNode(className = "android.widget.TextView"))
    }

    @Test
    fun `multiple criteria are AND-ed`() {
        val selector = UiSelector(text = "Send", className = "android.widget.Button")
        assertTrue(selector.matchesNode(text = "Send", className = "android.widget.Button"))
        assertFalse(selector.matchesNode(text = "Send", className = "android.widget.TextView"))
        assertFalse(selector.matchesNode(text = "Cancel", className = "android.widget.Button"))
    }

    @Test
    fun `null node attributes never match a required field`() {
        assertFalse(UiSelector(text = "Send").matchesNode())
        assertFalse(UiSelector(viewId = "x").matchesNode())
        assertFalse(UiSelector(className = "x").matchesNode())
    }

    @Test
    fun `isBlank reflects all fields empty`() {
        assertTrue(UiSelector().isBlank)
        assertFalse(UiSelector(text = "x").isBlank)
        assertFalse(UiSelector(viewId = "x").isBlank)
        assertFalse(UiSelector(contentDescription = "x").isBlank)
        assertFalse(UiSelector(className = "x").isBlank)
    }

    @Test
    fun `summary prefers stable fields and shows occurrence`() {
        assertEquals("id \"send\"", UiSelector(viewId = "send", text = "Send").summary)
        assertEquals(
            "text is \"Send\" #2",
            UiSelector(text = "Send", matchMode = TextMatchMode.EQUALS, occurrence = 1).summary
        )
    }
}
