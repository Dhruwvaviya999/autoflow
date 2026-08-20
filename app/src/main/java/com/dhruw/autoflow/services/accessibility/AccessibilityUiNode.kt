package com.dhruw.autoflow.services.accessibility

import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.dhruw.autoflow.automation.model.UiNode

/**
 * Records every [AccessibilityNodeInfo] obtained while walking one tree so
 * they can be released together once the step that fetched them is done.
 * recycle() is required below API 33 and a no-op from 33 on; either way,
 * releasing marks the tree as finished so stale references are never reused.
 */
class NodeTracker {

    private val obtained = ArrayList<AccessibilityNodeInfo>(64)

    fun track(info: AccessibilityNodeInfo) {
        if (NEEDS_RECYCLE) synchronized(obtained) { obtained.add(info) }
    }

    fun release() {
        if (!NEEDS_RECYCLE) return
        synchronized(obtained) {
            for (info in obtained) {
                try {
                    @Suppress("DEPRECATION")
                    info.recycle()
                } catch (_: IllegalStateException) {
                    // Already recycled — nothing to do.
                }
            }
            obtained.clear()
        }
    }

    private companion object {
        val NEEDS_RECYCLE = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    }
}

/**
 * Adapts one [AccessibilityNodeInfo] to the platform-neutral [UiNode] the
 * finder/executor operate on. Reads are defensive: nodes can go stale at any
 * moment (window closed, view recycled), in which case attributes read as
 * null/false and actions report failure instead of crashing.
 *
 * Privacy: the text of password nodes is never exposed — [text] returns null
 * for them, so no selector can match on (and no log can ever contain) a
 * password's masked or real content.
 */
class AccessibilityUiNode(
    private val info: AccessibilityNodeInfo,
    private val tracker: NodeTracker
) : UiNode {

    override val viewId: String?
        get() = safe { info.viewIdResourceName }

    override val contentDescription: String?
        get() = safe { info.contentDescription?.toString() }

    override val text: String?
        get() = safe { if (info.isPassword) null else info.text?.toString() }

    override val className: String?
        get() = safe { info.className?.toString() }

    override val packageName: String?
        get() = safe { info.packageName?.toString() }

    override val isClickable: Boolean
        get() = safe { info.isClickable } == true

    override val isLongClickable: Boolean
        get() = safe { info.isLongClickable } == true

    override val isEditable: Boolean
        get() = safe { info.isEditable } == true

    override val isScrollable: Boolean
        get() = safe { info.isScrollable } == true

    override val isPassword: Boolean
        get() = safe { info.isPassword } == true

    override val children: List<UiNode>
        get() {
            val count = safe { info.childCount } ?: 0
            if (count == 0) return emptyList()
            val result = ArrayList<UiNode>(count)
            for (i in 0 until count) {
                val child = safe { info.getChild(i) } ?: continue
                tracker.track(child)
                result.add(AccessibilityUiNode(child, tracker))
            }
            return result
        }

    override val parent: UiNode?
        get() = safe { info.parent }?.let { p ->
            tracker.track(p)
            AccessibilityUiNode(p, tracker)
        }

    override fun performClick(): Boolean =
        safe { info.performAction(AccessibilityNodeInfo.ACTION_CLICK) } == true

    override fun performLongClick(): Boolean =
        safe { info.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) } == true

    override fun performSetText(text: String): Boolean = safe {
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }
        info.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    } == true

    override fun performScrollForward(): Boolean =
        safe { info.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) } == true

    override fun performScrollBackward(): Boolean =
        safe { info.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) } == true

    /** Stale/recycled nodes throw IllegalStateException — read as absent. */
    private inline fun <T> safe(block: () -> T): T? = try {
        block()
    } catch (_: IllegalStateException) {
        null
    } catch (_: SecurityException) {
        null
    }
}
