package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.UiNode
import com.dhruw.autoflow.automation.model.UiSelector

/**
 * Deterministic, bounded search of a [UiNode] tree for a [UiSelector]. Pure
 * Kotlin — the Android service supplies a real tree, tests supply a fake one.
 *
 * Traversal is pre-order (document order), depth- and count-bounded so a
 * pathological tree can never hang or stack-overflow. Matching is never
 * random: if several nodes match and the selector has no [UiSelector.occurrence]
 * index, [findOne] reports [Result.Ambiguous] instead of guessing.
 */
class AccessibilityNodeFinder(
    private val maxDepth: Int = 60,
    private val maxNodes: Int = 5_000
) {

    sealed interface Result {
        data class Found(val node: UiNode) : Result
        data object NotFound : Result
        data class Ambiguous(val count: Int) : Result
    }

    /** All matches in document order (bounded). */
    fun findAll(root: UiNode?, selector: UiSelector): List<UiNode> {
        if (root == null || selector.isBlank) return emptyList()
        val matches = ArrayList<UiNode>()
        var visited = 0
        fun visit(node: UiNode, depth: Int) {
            if (depth > maxDepth || visited >= maxNodes) return
            visited++
            if (selector.matches(node.viewId, node.contentDescription, node.text, node.className)) {
                matches += node
            }
            for (child in node.children) {
                if (visited >= maxNodes) break
                visit(child, depth + 1)
            }
        }
        visit(root, 0)
        return matches
    }

    /**
     * The single node the selector designates. With no occurrence index:
     * exactly one match → Found, none → NotFound, many → Ambiguous. With an
     * index: that position → Found, out of range → NotFound.
     */
    fun findOne(root: UiNode?, selector: UiSelector): Result {
        val matches = findAll(root, selector)
        val index = selector.occurrence
        return when {
            index != null -> matches.getOrNull(index)?.let { Result.Found(it) } ?: Result.NotFound
            matches.isEmpty() -> Result.NotFound
            matches.size == 1 -> Result.Found(matches.first())
            else -> Result.Ambiguous(matches.size)
        }
    }
}
