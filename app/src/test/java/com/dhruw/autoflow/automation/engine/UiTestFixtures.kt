package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.UiNode

/**
 * In-memory [UiNode] tree for finder/executor tests. Mirrors the real
 * adapter's surface; action calls are recorded and their results are
 * configurable.
 */
class FakeUiNode(
    override val viewId: String? = null,
    override val contentDescription: String? = null,
    override val text: String? = null,
    override val className: String? = null,
    override val packageName: String? = "com.example.app",
    override val isClickable: Boolean = false,
    override val isLongClickable: Boolean = false,
    override val isEditable: Boolean = false,
    override val isScrollable: Boolean = false,
    override val isPassword: Boolean = false,
    private val clickResult: Boolean = true,
    private val longClickResult: Boolean = true,
    private val setTextResult: Boolean = true,
    private val scrollResult: Boolean = true,
    childNodes: List<FakeUiNode> = emptyList()
) : UiNode {

    override var parent: UiNode? = null
    override val children: List<UiNode> = childNodes

    var clickCount = 0
        private set
    var longClickCount = 0
        private set
    var enteredText: String? = null
        private set
    var scrolledForward = false
        private set
    var scrolledBackward = false
        private set

    init {
        childNodes.forEach { it.parent = this }
    }

    override fun performClick(): Boolean {
        clickCount++
        return clickResult
    }

    override fun performLongClick(): Boolean {
        longClickCount++
        return longClickResult
    }

    override fun performSetText(text: String): Boolean {
        if (setTextResult) enteredText = text
        return setTextResult
    }

    override fun performScrollForward(): Boolean {
        if (scrollResult) scrolledForward = true
        return scrollResult
    }

    override fun performScrollBackward(): Boolean {
        if (scrollResult) scrolledBackward = true
        return scrollResult
    }
}

/** Configurable [UiAutomationHost] for executor tests. */
class FakeUiAutomationHost(
    var root: UiNode? = null,
    var currentPkg: String? = null,
    var locked: Boolean = false,
    var launchResult: Boolean = true,
    var backResult: Boolean = true,
    var confirmationAnswer: Boolean = true
) : UiAutomationHost {

    /** When set, wins over [root] — lets tests change the tree over time. */
    var rootProvider: (() -> UiNode?)? = null

    /** When set, launching switches the foreground to this package. */
    var launchSetsPackageTo: String? = null

    /** When set, replaces the default instant confirmation answer. */
    var confirmationHandler: (suspend (String, String) -> Boolean)? = null

    val launchedPackages = mutableListOf<String>()
    val confirmationPrompts = mutableListOf<Pair<String, String>>()
    var backCount = 0
        private set

    override fun rootNode(): UiNode? = rootProvider?.invoke() ?: root

    override fun currentPackage(): String? = currentPkg

    override fun isDeviceLocked(): Boolean = locked

    override suspend fun launchApp(packageName: String): Boolean {
        launchedPackages += packageName
        if (launchResult) currentPkg = launchSetsPackageTo ?: packageName
        return launchResult
    }

    override suspend fun globalBack(): Boolean {
        backCount++
        return backResult
    }

    override suspend fun requestConfirmation(prompt: String, nextActionLabel: String): Boolean {
        confirmationPrompts += prompt to nextActionLabel
        confirmationHandler?.let { return it(prompt, nextActionLabel) }
        return confirmationAnswer
    }
}
