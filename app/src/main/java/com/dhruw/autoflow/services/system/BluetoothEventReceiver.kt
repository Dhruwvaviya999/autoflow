package com.dhruw.autoflow.services.system

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

/**
 * Bluetooth ACL connect/disconnect — one of the few implicit broadcasts
 * Android still delivers to manifest receivers, so it wakes AutoFlow even
 * when the process is dead. Delivery requires the user-granted
 * BLUETOOTH_CONNECT permission on Android 12+; without it the system simply
 * never calls this receiver. Heavy work is delegated to the worker.
 *
 * The device NAME can be permission-restricted independently; a
 * SecurityException falls back to an empty name — matching uses the stable
 * address anyway.
 */
class BluetoothEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val connected = when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> true
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> false
            else -> return
        }
        val device = IntentCompat.getParcelableExtra(
            intent,
            BluetoothDevice.EXTRA_DEVICE,
            BluetoothDevice::class.java
        ) ?: return
        val address = device.address ?: return
        val name = try {
            device.name.orEmpty()
        } catch (e: SecurityException) {
            ""
        }

        WorkManager.getInstance(context.applicationContext).enqueue(
            OneTimeWorkRequestBuilder<SystemEventDispatchWorker>()
                .setInputData(
                    workDataOf(
                        SystemEventDispatchWorker.KEY_KIND to SystemEventDispatchWorker.KIND_BLUETOOTH,
                        SystemEventDispatchWorker.KEY_CONNECTED to connected,
                        SystemEventDispatchWorker.KEY_ADDRESS to address,
                        SystemEventDispatchWorker.KEY_NAME to name
                    )
                )
                .build()
        )
    }
}
