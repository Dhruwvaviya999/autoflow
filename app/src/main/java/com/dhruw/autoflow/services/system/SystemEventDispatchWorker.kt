package com.dhruw.autoflow.services.system

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dhruw.autoflow.AutoFlowApplication
import com.dhruw.autoflow.automation.model.SystemEvent

/**
 * Bridges manifest-registered receivers (boot, Bluetooth ACL) to the
 * dispatcher. Receivers must stay lightweight and automations can run for
 * longer than a receiver is allowed to live, so the receiver only enqueues
 * this worker and WorkManager provides the execution window.
 */
class SystemEventDispatchWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as AutoFlowApplication).container
        val now = System.currentTimeMillis()
        when (inputData.getString(KEY_KIND)) {
            KIND_BOOT -> container.eventDispatcher.dispatch(SystemEvent.DeviceBooted(now))
            KIND_BLUETOOTH -> {
                val event = container.systemStateTracker.onBluetoothEvent(
                    connected = inputData.getBoolean(KEY_CONNECTED, false),
                    address = inputData.getString(KEY_ADDRESS).orEmpty(),
                    name = inputData.getString(KEY_NAME).orEmpty(),
                    timestamp = now
                )
                event?.let { container.eventDispatcher.dispatch(it) }
            }
            else -> return Result.failure()
        }
        return Result.success()
    }

    companion object {
        const val KEY_KIND = "kind"
        const val KIND_BOOT = "boot"
        const val KIND_BLUETOOTH = "bluetooth"
        const val KEY_CONNECTED = "connected"
        const val KEY_ADDRESS = "address"
        const val KEY_NAME = "name"
    }
}
