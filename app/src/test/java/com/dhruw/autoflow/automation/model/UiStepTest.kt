package com.dhruw.autoflow.automation.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiStepTest {

    @Test
    fun `summaries stay user-friendly and non-sensitive`() {
        assertEquals("Open WhatsApp", UiStep.LaunchApp("com.whatsapp", "WhatsApp").summary)
        assertEquals("Wait 2 s", UiStep.Wait(2_000).summary)
        assertEquals("Press Back", UiStep.GlobalBack.summary)
        // The entered text must not appear in the summary.
        val setText = UiStep.SetText(UiSelector(viewId = "input"), "my private reply")
        assertTrue(!setText.summary.contains("private"))
    }

    @Test
    fun `confirmation step is the consequential one`() {
        assertEquals(UiStepRisk.CONSEQUENTIAL, UiStep.RequireUserConfirmation("Send?").risk)
        assertEquals(UiStepRisk.NORMAL, UiStep.ClickElement(UiSelector(text = "Demo")).risk)
        assertEquals(UiStepRisk.NORMAL, UiStep.Wait(100).risk)
    }

    @Test
    fun `display names avoid accessibility jargon`() {
        assertEquals("Tap element", UiStep.ClickElement(UiSelector(text = "x")).displayName)
        assertEquals("Enter text", UiStep.SetText(UiSelector(text = "x"), "y").displayName)
        assertEquals("Go back", UiStep.GlobalBack.displayName)
    }

    @Test
    fun `nearest clickable walks up but is bounded`() {
        val leaf = FakeNode(clickable = false)
        val mid = FakeNode(clickable = false, child = leaf)
        val top = FakeNode(clickable = true, child = mid)
        assertEquals(top, leaf.nearestClickable())

        // Beyond the depth bound the clickable ancestor is not reached.
        var deepLeaf = FakeNode(clickable = false)
        val bottom = deepLeaf
        repeat(7) { deepLeaf = FakeNode(clickable = false, child = deepLeaf) }
        FakeNode(clickable = true, child = deepLeaf)
        assertEquals(null, bottom.nearestClickable(maxDepth = 5))
    }

    private class FakeNode(
        val clickable: Boolean,
        child: FakeNode? = null
    ) : UiNode {
        override val viewId: String? = null
        override val contentDescription: String? = null
        override val text: String? = null
        override val className: String? = null
        override val packageName: String? = null
        override val isClickable: Boolean = clickable
        override val isLongClickable: Boolean = false
        override val isEditable: Boolean = false
        override val isScrollable: Boolean = false
        override val isPassword: Boolean = false
        override val children: List<UiNode> = listOfNotNull(child)
        override var parent: UiNode? = null

        init {
            child?.parent = this
        }

        override fun performClick() = true
        override fun performLongClick() = true
        override fun performSetText(text: String) = true
        override fun performScrollForward() = true
        override fun performScrollBackward() = true
    }
}
