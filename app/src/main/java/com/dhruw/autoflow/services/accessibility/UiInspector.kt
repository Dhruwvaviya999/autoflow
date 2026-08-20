package com.dhruw.autoflow.services.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * State for the user-assisted element inspector: while the user has
 * explicitly enabled Inspect mode, the accessibility service records the
 * attributes of elements the user taps (in any app) so they can build
 * selectors from real values.
 *
 * Privacy/scope guarantees, enforced here:
 * - Off by default; only the user turns it on, and it auto-disables after
 *   [AUTO_DISABLE_MILLIS] so it can never be left running by accident.
 * - Holds at most [MAX_ELEMENTS] recent elements, in memory only — nothing
 *   is persisted or transmitted, and disabling clears everything.
 * - Password fields: the service never reads their text; [InspectedElement]
 *   carries structure (class, id, flags) but no protected content.
 * - Captures single tapped elements only — never whole screen trees.
 */
class UiInspector(private val clock: () -> Long = System::currentTimeMillis) {

    /** Attributes of one user-tapped element; all values are optional. */
    data class InspectedElement(
        val packageName: String,
        val appLabel: String,
        val text: String,
        val contentDescription: String,
        val viewId: String,
        val className: String,
        val isClickable: Boolean,
        val isLongClickable: Boolean,
        val isEditable: Boolean,
        val isScrollable: Boolean,
        val isPassword: Boolean,
        val capturedAt: Long
    )

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _elements = MutableStateFlow<List<InspectedElement>>(emptyList())
    val elements: StateFlow<List<InspectedElement>> = _elements.asStateFlow()

    @Volatile
    private var enabledAt: Long = 0

    fun setEnabled(on: Boolean) {
        if (on) {
            enabledAt = clock()
            _enabled.value = true
        } else {
            _enabled.value = false
            _elements.value = emptyList()
        }
    }

    /**
     * True while inspect mode is on and within its time window. Called by
     * the service on every click event, so it also performs the auto-disable.
     */
    fun isActive(): Boolean {
        if (!_enabled.value) return false
        if (clock() - enabledAt > AUTO_DISABLE_MILLIS) {
            setEnabled(false)
            return false
        }
        return true
    }

    fun record(element: InspectedElement) {
        if (!isActive()) return
        _elements.update { current -> (listOf(element) + current).take(MAX_ELEMENTS) }
    }

    fun clear() {
        _elements.value = emptyList()
    }

    companion object {
        const val MAX_ELEMENTS = 25
        const val AUTO_DISABLE_MILLIS = 10 * 60 * 1000L
    }
}
