package com.dhruw.autoflow.services.accessibility

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dhruw.autoflow.AutoFlowApplication

/**
 * Receives Cancel/Continue taps from the UI-automation notifications and
 * forwards them to the session manager. Not exported — only AutoFlow's own
 * PendingIntents can trigger it, so this is not a remote-control surface.
 */
class UiAutomationControlReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val manager = (context.applicationContext as AutoFlowApplication)
            .container.uiAutomationSessionManager
        when (intent.action) {
            ACTION_CANCEL -> manager.cancel()
            ACTION_CONTINUE -> manager.respondToConfirmation(true)
            ACTION_DECLINE -> manager.respondToConfirmation(false)
        }
    }

    companion object {
        const val ACTION_CANCEL = "com.dhruw.autoflow.UI_AUTOMATION_CANCEL"
        const val ACTION_CONTINUE = "com.dhruw.autoflow.UI_AUTOMATION_CONTINUE"
        const val ACTION_DECLINE = "com.dhruw.autoflow.UI_AUTOMATION_DECLINE"
    }
}
