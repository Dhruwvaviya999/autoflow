package com.dhruw.autoflow.services.system

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.PowerManager
import com.dhruw.autoflow.automation.engine.DeviceStateProvider
import com.dhruw.autoflow.automation.engine.SystemStateTracker

/**
 * Android-backed [DeviceStateProvider] for system conditions. Every read is
 * a cheap on-demand query (sticky broadcast, system service getter) — no
 * registration, no polling. Unknown/unreadable values become null, which
 * makes the condition fail instead of guessing. All data stays on-device.
 */
class AndroidDeviceStateProvider(
    context: Context,
    private val tracker: SystemStateTracker
) : DeviceStateProvider {

    private val appContext = context.applicationContext

    override fun batteryLevel(): Int? {
        val intent = stickyBattery() ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return level * 100 / scale
    }

    override fun isCharging(): Boolean? {
        val intent = stickyBattery() ?: return null
        return intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            .takeIf { it >= 0 }
            ?.let { it != 0 }
    }

    override fun isNetworkAvailable(): Boolean? = runCatching {
        activeCapabilities()?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrNull()

    override fun isWifiConnected(): Boolean? = runCatching {
        activeCapabilities()?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }.getOrNull()

    override fun connectedWifiSsid(): String? {
        if (isWifiConnected() != true) return null
        @Suppress("DEPRECATION")
        return runCatching {
            appContext.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid
        }.getOrNull().let(NetworkMonitor::normalizeSsid)
    }

    override fun isScreenOn(): Boolean? =
        appContext.getSystemService(PowerManager::class.java)?.isInteractive

    override fun isBluetoothDeviceConnected(address: String): Boolean =
        tracker.isBluetoothConnected(address)

    override fun isAnyBluetoothDeviceConnected(): Boolean =
        tracker.isAnyBluetoothConnected()

    private fun stickyBattery(): Intent? =
        appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    private fun activeCapabilities(): NetworkCapabilities? {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = cm.activeNetwork ?: return null
        return cm.getNetworkCapabilities(network)
    }
}
