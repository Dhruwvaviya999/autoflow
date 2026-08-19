package com.dhruw.autoflow.services.system

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.dhruw.autoflow.automation.engine.SystemStateTracker
import com.dhruw.autoflow.automation.model.SystemEvent

/**
 * Headset detection via AudioManager's device callbacks — covers wired and
 * Bluetooth audio without treating every Bluetooth device as a headset.
 * Registering the callback immediately reports the current devices; the
 * tracker stores that first report as a baseline and emits nothing for it.
 */
class AudioDeviceMonitor(
    context: Context,
    private val tracker: SystemStateTracker,
    private val dispatch: (SystemEvent) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis
) : SystemEventMonitor {

    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = reportState()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = reportState()
    }

    override fun start() {
        // Null handler = callbacks on the main thread; reportState is cheap.
        audioManager?.registerAudioDeviceCallback(callback, null)
    }

    override fun stop() {
        audioManager?.unregisterAudioDeviceCallback(callback)
    }

    private fun reportState() {
        val am = audioManager ?: return
        val headset = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.type in HEADSET_TYPES }
        tracker.onHeadsetState(
            connected = headset != null,
            deviceName = headset?.productName?.toString().orEmpty(),
            timestamp = clock()
        )?.let(dispatch)
    }

    private companion object {
        // AudioDeviceInfo type constants are compile-time constants, safe on
        // every runtime API level even when introduced later (BLE_HEADSET: 31).
        val HEADSET_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET
        )
    }
}
