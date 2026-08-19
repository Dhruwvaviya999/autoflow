package com.dhruw.autoflow.services.system

import android.util.Log

/**
 * One Android-callback-based watcher (battery, network, screen, audio).
 * Implementations register receivers/callbacks in [start] and unregister in
 * [stop] — no polling, no services. They convert Android callbacks into
 * SystemEvents via the SystemStateTracker (which enforces edge semantics)
 * and hand real transitions to the dispatcher.
 */
interface SystemEventMonitor {
    fun start()
    fun stop()
}

/**
 * Owns all in-process monitors. Started once from Application.onCreate —
 * receiver registration is cheap and passive; events only cost work when an
 * actual state transition happens. Manifest-registered receivers (boot,
 * Bluetooth ACL) live outside the hub because they must work without a
 * running process.
 */
class SystemMonitorHub(private val monitors: List<SystemEventMonitor>) {

    private var started = false

    @Synchronized
    fun start() {
        if (started) return
        started = true
        monitors.forEach { monitor ->
            try {
                monitor.start()
            } catch (e: Exception) {
                // One broken monitor must not take down the rest.
                Log.w(TAG, "Failed to start ${monitor.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    @Synchronized
    fun stop() {
        if (!started) return
        started = false
        monitors.forEach { monitor ->
            try {
                monitor.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop ${monitor.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    private companion object {
        const val TAG = "AutoFlowSystemHub"
    }
}
