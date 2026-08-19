package com.dhruw.autoflow.services.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.dhruw.autoflow.automation.engine.SystemStateTracker
import com.dhruw.autoflow.automation.model.SystemEvent

/**
 * ConnectivityManager callbacks for general network availability and Wi-Fi
 * connect/disconnect/roam — push-based, no polling.
 *
 * SSID caveats (honest handling, never a crash):
 * - API 31+: the callback is registered with FLAG_INCLUDE_LOCATION_INFO and
 *   the SSID appears only when ACCESS_FINE_LOCATION is granted.
 * - API 29–30: SSID needs fine location + location services on.
 * - When hidden, Android reports "<unknown ssid>" — normalized to null, so
 *   "any network" triggers still work and SSID-filtered ones simply can't
 *   match (the trigger dialog explains this).
 *
 * Known limitation: switching the default network (Wi-Fi ↔ mobile) can emit
 * a brief unavailable→available pair from the default-network callback.
 */
class NetworkMonitor(
    context: Context,
    private val tracker: SystemStateTracker,
    private val dispatch: (SystemEvent) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis
) : SystemEventMonitor {

    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(ConnectivityManager::class.java)

    private val defaultCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            tracker.onNetworkState(true, clock())?.let(dispatch)
        }

        override fun onLost(network: Network) {
            tracker.onNetworkState(false, clock())?.let(dispatch)
        }
    }

    private fun onWifiCapabilities(caps: NetworkCapabilities) {
        tracker.onWifiState(true, ssidFrom(caps), clock())?.let(dispatch)
    }

    private fun onWifiLost() {
        tracker.onWifiState(false, null, clock())?.let(dispatch)
    }

    // FLAG_INCLUDE_LOCATION_INFO exists from API 31; without it Android 12+
    // always redacts the SSID even when location permission is granted.
    private val wifiCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                onWifiCapabilities(caps)

            override fun onLost(network: Network) = onWifiLost()
        }
    } else {
        object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                onWifiCapabilities(caps)

            override fun onLost(network: Network) = onWifiLost()
        }
    }

    override fun start() {
        val cm = connectivityManager ?: return
        seedBaselines(cm)
        cm.registerDefaultNetworkCallback(defaultCallback)
        cm.registerNetworkCallback(
            NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build(),
            wifiCallback
        )
    }

    override fun stop() {
        val cm = connectivityManager ?: return
        runCatching { cm.unregisterNetworkCallback(defaultCallback) }
        runCatching { cm.unregisterNetworkCallback(wifiCallback) }
    }

    /**
     * Callbacks only fire immediately when a network exists; a disconnected
     * start would otherwise leave the tracker unseeded and swallow the first
     * real transition as a baseline. Seed the current state explicitly.
     */
    private fun seedBaselines(cm: ConnectivityManager) {
        val caps = cm.activeNetwork?.let { runCatching { cm.getNetworkCapabilities(it) }.getOrNull() }
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        tracker.onNetworkState(online, clock())
        val wifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        tracker.onWifiState(wifiConnected, if (wifiConnected) caps?.let(::ssidFrom) else null, clock())
    }

    private fun ssidFrom(caps: NetworkCapabilities): String? {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (caps.transportInfo as? WifiInfo)?.ssid
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                appContext.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid
            }.getOrNull()
        }
        return normalizeSsid(raw)
    }

    companion object {
        /** "\"Home\"" → "Home"; "<unknown ssid>" → null. */
        fun normalizeSsid(raw: String?): String? = raw
            ?.removeSurrounding("\"")
            ?.takeUnless { it.isBlank() || it.equals("<unknown ssid>", ignoreCase = true) }
    }
}
