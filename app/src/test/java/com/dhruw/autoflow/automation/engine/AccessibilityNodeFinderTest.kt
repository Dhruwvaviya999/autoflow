package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.TextMatchMode
import com.dhruw.autoflow.automation.model.UiSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityNodeFinderTest {

    private val finder = AccessibilityNodeFinder()

    private fun tree(): FakeUiNode = FakeUiNode(
        className = "android.widget.FrameLayout",
        childNodes = listOf(
            FakeUiNode(
                className = "android.widget.LinearLayout",
                childNodes = listOf(
                    FakeUiNode(
                        viewId = "com.example:id/send_button",
                        text = "Send",
                        className = "android.widget.Button",
                        isClickable = true
                    ),
                    FakeUiNode(text = "Send", className = "android.widget.TextView"),
                    FakeUiNode(
                        contentDescription = "Attach file",
                        className = "android.widget.ImageButton",
                        isClickable = true
                    )
                )
            ),
            FakeUiNode(text = "Cancel", className = "android.widget.Button", isClickable = true)
        )
    )

    @Test
    fun `finds single node by view id`() {
        val result = finder.findOne(tree(), UiSelector(viewId = "send_button"))
        assertTrue(result is AccessibilityNodeFinder.Result.Found)
        assertEquals(
            "com.example:id/send_button",
            (result as AccessibilityNodeFinder.Result.Found).node.viewId
        )
    }

    @Test
    fun `finds single node by content description`() {
        val result = finder.findOne(tree(), UiSelector(contentDescription = "Attach file"))
        assertTrue(result is AccessibilityNodeFinder.Result.Found)
    }

    @Test
    fun `ambiguous text match is reported not guessed`() {
        val result = finder.findOne(
            tree(),
            UiSelector(text = "Send", matchMode = TextMatchMode.EQUALS)
        )
        assertTrue(result is AccessibilityNodeFinder.Result.Ambiguous)
        assertEquals(2, (result as AccessibilityNodeFinder.Result.Ambiguous).count)
    }

    @Test
    fun `class constraint disambiguates text match`() {
        val result = finder.findOne(
            tree(),
            UiSelector(text = "Send", className = "android.widget.Button")
        )
        assertTrue(result is AccessibilityNodeFinder.Result.Found)
        assertEquals(
            "android.widget.Button",
            (result as AccessibilityNodeFinder.Result.Found).node.className
        )
    }

    @Test
    fun `occurrence index selects nth match in document order`() {
        val first = finder.findOne(tree(), UiSelector(text = "Send", occurrence = 0))
        val second = finder.findOne(tree(), UiSelector(text = "Send", occurrence = 1))
        assertTrue(first is AccessibilityNodeFinder.Result.Found)
        assertTrue(second is AccessibilityNodeFinder.Result.Found)
        assertEquals(
            "android.widget.Button",
            (first as AccessibilityNodeFinder.Result.Found).node.className
        )
        assertEquals(
            "android.widget.TextView",
            (second as AccessibilityNodeFinder.Result.Found).node.className
        )
    }

    @Test
    fun `occurrence out of range is not found`() {
        val result = finder.findOne(tree(), UiSelector(text = "Send", occurrence = 5))
        assertEquals(AccessibilityNodeFinder.Result.NotFound, result)
    }

    @Test
    fun `no match reports not found`() {
        val result = finder.findOne(tree(), UiSelector(text = "Delete"))
        assertEquals(AccessibilityNodeFinder.Result.NotFound, result)
    }

    @Test
    fun `null root reports not found`() {
        assertEquals(
            AccessibilityNodeFinder.Result.NotFound,
            finder.findOne(null, UiSelector(text = "Send"))
        )
    }

    @Test
    fun `blank selector matches nothing`() {
        assertEquals(
            AccessibilityNodeFinder.Result.NotFound,
            finder.findOne(tree(), UiSelector())
        )
        assertTrue(finder.findAll(tree(), UiSelector()).isEmpty())
    }

    @Test
    fun `traversal respects node budget`() {
        val wide = FakeUiNode(
            childNodes = (1..50).map { FakeUiNode(text = "Item") }
        )
        val bounded = AccessibilityNodeFinder(maxNodes = 10)
        val matches = bounded.findAll(wide, UiSelector(text = "Item"))
        assertTrue("Expected at most 10 visits, got ${matches.size}", matches.size <= 10)
    }

    @Test
    fun `traversal respects depth bound`() {
        // Chain of depth 30 with the match at the bottom; a finder capped at
        // depth 5 must not reach it (and must not blow the stack either).
        var leaf = FakeUiNode(text = "Deep")
        repeat(30) { leaf = FakeUiNode(childNodes = listOf(leaf)) }
        val bounded = AccessibilityNodeFinder(maxDepth = 5)
        assertEquals(
            AccessibilityNodeFinder.Result.NotFound,
            bounded.findOne(leaf, UiSelector(text = "Deep"))
        )
    }

    @Test
    fun `findAll returns matches in document order`() {
        val matches = finder.findAll(tree(), UiSelector(text = "Send"))
        assertEquals(2, matches.size)
        assertEquals("android.widget.Button", matches[0].className)
        assertEquals("android.widget.TextView", matches[1].className)
    }
}
