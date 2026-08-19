package com.dhruw.autoflow.services.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import com.dhruw.autoflow.automation.engine.SystemStateTracker
import com.dhruw.autoflow.automation.model.SystemEvent

/**
 * SCREEN_ON/SCREEN_OFF broadcasts (context-registered — Android does not
 * allow them in the manifest). The current interactive state is seeded
 * silently at start so a transition that happens right after process start
 * is neither missed nor duplicated.
 */
class ScreenMonitor(
    context: Context,
    private val tracker: SystemStateTracker,
    private val dispatch: (SystemEvent) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis
) : SystemEventMonitor {

    private val appContext = context.applicationContext

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val on = when (intent.action) {
                Intent.ACTION_SCREEN_ON -> true
                Intent.ACTION_SCREEN_OFF -> false
                else -> return
            }
            tracker.onScreenState(on, clock())?.let(dispatch)
        }
    }

    override fun start() {
        appContext.getSystemService(PowerManager::class.java)?.let { pm ->
            tracker.seedScreenState(pm.isInteractive)
        }
        appContext.registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
        )
    }

    override fun stop() {
        runCatching { appContext.unregisterReceiver(receiver) }
    }
}
