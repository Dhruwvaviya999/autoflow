package com.dhruw.autoflow.services.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiInspectorTest {

    private var now = 0L
    private val inspector = UiInspector(clock = { now })

    private fun element(text: String) = UiInspector.InspectedElement(
        packageName = "com.example.app",
        appLabel = "Example",
        text = text,
        contentDescription = "",
        viewId = "",
        className = "android.widget.Button",
        isClickable = true,
        isLongClickable = false,
        isEditable = false,
        isScrollable = false,
        isPassword = false,
        capturedAt = now
    )

    @Test
    fun `disabled by default and records nothing`() {
        assertFalse(inspector.isActive())
        inspector.record(element("ignored"))
        assertTrue(inspector.elements.value.isEmpty())
    }

    @Test
    fun `records newest first while enabled`() {
        inspector.setEnabled(true)
        inspector.record(element("first"))
        inspector.record(element("second"))
        assertEquals(listOf("second", "first"), inspector.elements.value.map { it.text })
    }

    @Test
    fun `capped at max elements`() {
        inspector.setEnabled(true)
        repeat(UiInspector.MAX_ELEMENTS + 10) { inspector.record(element("e$it")) }
        assertEquals(UiInspector.MAX_ELEMENTS, inspector.elements.value.size)
    }

    @Test
    fun `disabling clears captured elements`() {
        inspector.setEnabled(true)
        inspector.record(element("captured"))
        inspector.setEnabled(false)
        assertTrue(inspector.elements.value.isEmpty())
        assertFalse(inspector.enabled.value)
    }

    @Test
    fun `auto-disables after the time window`() {
        inspector.setEnabled(true)
        now += UiInspector.AUTO_DISABLE_MILLIS + 1
        assertFalse(inspector.isActive())
        assertFalse(inspector.enabled.value)
    }

    @Test
    fun `stays active within the time window`() {
        inspector.setEnabled(true)
        now += UiInspector.AUTO_DISABLE_MILLIS - 1
        assertTrue(inspector.isActive())
    }
}
