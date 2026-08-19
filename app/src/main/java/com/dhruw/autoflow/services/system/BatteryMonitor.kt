package com.dhruw.autoflow.services.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.dhruw.autoflow.automation.engine.SystemStateTracker
import com.dhruw.autoflow.automation.model.SystemEvent

/**
 * Listens for ACTION_BATTERY_CHANGED (system-pushed on level/plug changes —
 * no polling). The broadcast is sticky, so registration delivers the
 * current state immediately; the tracker stores that first reading as a
 * baseline and emits nothing for it. Charging is derived from the plugged
 * state, so one receiver covers both level and charging transitions.
 */
class BatteryMonitor(
    context: Context,
    private val tracker: SystemStateTracker,
    private val dispatch: (SystemEvent) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis
) : SystemEventMonitor {

    private val appContext = context.applicationContext

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handle(intent)
        }
    }

    override fun start() {
        // Sticky broadcast: this returns the current battery intent, which
        // seeds the tracker baseline synchronously.
        val sticky = appContext.registerReceiver(
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        sticky?.let(::handle)
    }

    override fun stop() {
        runCatching { appContext.unregisterReceiver(receiver) }
    }

    private fun handle(intent: Intent) {
        val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (rawLevel < 0 || scale <= 0) return
        val level = rawLevel * 100 / scale
        val charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        tracker.onBatteryReading(level, charging, clock()).forEach(dispatch)
    }
}
