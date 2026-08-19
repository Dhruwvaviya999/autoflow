package com.dhruw.autoflow.services.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

/**
 * BOOT_COMPLETED entry point. Deliberately does nothing but enqueue work:
 * the DeviceBooted dispatch (and any automations it runs) happens in
 * SystemEventDispatchWorker under WorkManager's execution rules. Starting
 * the process also brings up the SystemMonitorHub via Application.onCreate,
 * which re-arms all in-process monitors after a reboot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.i(TAG, "Boot completed — scheduling boot dispatch")
        WorkManager.getInstance(context.applicationContext).enqueue(
            OneTimeWorkRequestBuilder<SystemEventDispatchWorker>()
                .setInputData(
                    workDataOf(
                        SystemEventDispatchWorker.KEY_KIND to SystemEventDispatchWorker.KIND_BOOT
                    )
                )
                .build()
        )
    }

    private companion object {
        const val TAG = "AutoFlowBoot"
    }
}
