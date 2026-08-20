package com.dhruw.autoflow.automation.model

import java.util.Locale

/**
 * How AutoFlow locates an on-screen element. All fields are optional and
 * combined with AND — a node matches only when every non-blank field matches.
 * Prefer stable fields (view id, content description) over visible [text],
 * which breaks when the app's language or wording changes.
 *
 * [occurrence] disambiguates when several nodes match: null means "there must
 * be exactly one match, otherwise fail" (deterministic, never a random pick);
 * a 0-based index explicitly selects the Nth match in document order.
 */
data class UiSelector(
    val viewId: String = "",
    val contentDescription: String = "",
    val text: String = "",
    val className: String = "",
    val matchMode: TextMatchMode = TextMatchMode.EQUALS,
    val occurrence: Int? = null
) {
    val isBlank: Boolean
        get() = viewId.isBlank() && contentDescription.isBlank() &&
            text.isBlank() && className.isBlank()

    /**
     * True when this node's attributes satisfy every constraint. viewId and
     * className compare exactly (case-insensitive); text and contentDescription
     * use [matchMode]. A viewId without a package prefix ("send_button")
     * also matches the id part of a full Android resource name
     * ("com.example:id/send_button") so users can copy either form from the
     * inspector.
     */
    fun matches(
        nodeViewId: String?,
        nodeContentDescription: String?,
        nodeText: String?,
        nodeClassName: String?
    ): Boolean {
        if (viewId.isNotBlank() && !viewIdMatches(nodeViewId)) return false
        if (className.isNotBlank() && !equalsIgnoreCase(nodeClassName, className)) return false
        if (contentDescription.isNotBlank() &&
            !matchMode.matches(nodeContentDescription.orEmpty(), contentDescription)
        ) return false
        if (text.isNotBlank() && !matchMode.matches(nodeText.orEmpty(), text)) return false
        return true
    }

    private fun equalsIgnoreCase(value: String?, expected: String): Boolean =
        value != null && value.trim().equals(expected.trim(), ignoreCase = true)

    private fun viewIdMatches(nodeViewId: String?): Boolean {
        if (nodeViewId == null) return false
        if (equalsIgnoreCase(nodeViewId, viewId)) return true
        // Bare id ("send_button") against a full resource name — compare the
        // part after "id/". Never the other way round: a full selector id
        // must match the full node id.
        if (!viewId.contains(':') && !viewId.contains('/')) {
            return equalsIgnoreCase(nodeViewId.trim().substringAfterLast('/'), viewId)
        }
        return false
    }

    val summary: String
        get() {
            val part = when {
                viewId.isNotBlank() -> "id \"${viewId.trim()}\""
                contentDescription.isNotBlank() -> "description ${matchMode.verb} \"${contentDescription.trim()}\""
                text.isNotBlank() -> "text ${matchMode.verb} \"${text.trim()}\""
                className.isNotBlank() -> "class \"${shortClass(className)}\""
                else -> "any element"
            }
            val extra = when {
                className.isNotBlank() && (viewId.isNotBlank() || text.isNotBlank() || contentDescription.isNotBlank()) ->
                    " (${shortClass(className)})"
                else -> ""
            }
            val idx = occurrence?.let { " #${it + 1}" } ?: ""
            return part + extra + idx
        }

    private fun shortClass(name: String): String = name.substringAfterLast('.')
}

private val TextMatchMode.verb: String
    get() = when (this) {
        TextMatchMode.CONTAINS -> "contains"
        TextMatchMode.EQUALS -> "is"
        TextMatchMode.STARTS_WITH -> "starts with"
    }
