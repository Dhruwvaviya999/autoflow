package com.dhruw.autoflow.automation.model

/**
 * A read-only, platform-neutral view of one accessibility node plus the
 * actions it can perform. The Android service wraps AccessibilityNodeInfo in
 * this interface so the finder and executor stay pure and unit-testable; the
 * fake used in tests implements the same surface.
 *
 * Implementations must not be cached long-term — nodes go stale. Callers
 * fetch a fresh tree, act, and drop the reference.
 */
interface UiNode {
    val viewId: String?
    val contentDescription: String?
    val text: String?
    val className: String?
    val packageName: String?
    val isClickable: Boolean
    val isLongClickable: Boolean
    val isEditable: Boolean
    val isScrollable: Boolean
    /** True for password/PIN input types — SetText must refuse these. */
    val isPassword: Boolean
    val children: List<UiNode>
    val parent: UiNode?

    fun performClick(): Boolean
    fun performLongClick(): Boolean
    fun performSetText(text: String): Boolean
    fun performScrollForward(): Boolean
    fun performScrollBackward(): Boolean
}

/**
 * Nearest ancestor-or-self that can be clicked, within [maxDepth] hops up the
 * tree. Returns null instead of clicking an arbitrary far ancestor — the
 * executor turns that into a structured failure.
 */
fun UiNode.nearestClickable(maxDepth: Int = 5): UiNode? {
    var current: UiNode? = this
    var depth = 0
    while (current != null && depth <= maxDepth) {
        if (current.isClickable) return current
        current = current.parent
        depth++
    }
    return null
}

/** Nearest ancestor-or-self that can be long-clicked, within [maxDepth] hops. */
fun UiNode.nearestLongClickable(maxDepth: Int = 5): UiNode? {
    var current: UiNode? = this
    var depth = 0
    while (current != null && depth <= maxDepth) {
        if (current.isLongClickable) return current
        current = current.parent
        depth++
    }
    return null
}
