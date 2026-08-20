package com.dhruw.autoflow.core

import java.util.ArrayDeque
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One diagnostic line: what happened, not what it contained. */
data class DiagnosticEvent(
    val timestamp: Long,
    val category: String,
    val message: String
)

/**
 * Opt-in, in-memory ring buffer of engine events for Developer Tools.
 *
 * Deliberately limited: recording is off until the user turns it on, the
 * buffer holds a bounded number of entries, nothing is written to disk, and
 * callers pass descriptions rather than payloads — notification text, typed
 * text and screen content never reach this log.
 */
class DiagnosticEventLog(
    private val capacity: Int = 200,
    private val clock: () -> Long = System::currentTimeMillis
) {

    private val buffer = ArrayDeque<DiagnosticEvent>(capacity)

    private val _events = MutableStateFlow<List<DiagnosticEvent>>(emptyList())
    val events: StateFlow<List<DiagnosticEvent>> = _events.asStateFlow()

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    fun setRecording(enabled: Boolean) {
        _recording.value = enabled
        if (!enabled) clear()
    }

    /** No-op unless recording is on. */
    fun record(category: String, message: String) {
        if (!_recording.value) return
        synchronized(buffer) {
            if (buffer.size >= capacity) buffer.removeFirst()
            buffer.addLast(DiagnosticEvent(clock(), category, message))
            _events.value = buffer.toList().asReversed()
        }
    }

    fun clear() {
        synchronized(buffer) {
            buffer.clear()
            _events.value = emptyList()
        }
    }
}
