package com.dhruw.autoflow.automation.engine

/**
 * Read-only snapshot of current device state, consumed by system conditions.
 * The Android implementation lives in services/system; the engine only sees
 * this interface, keeping the domain Android-free. Every value is nullable:
 * null means "unknown/unavailable" and makes the condition fail rather than
 * guess.
 */
interface DeviceStateProvider {

    /** 0–100, or null when unknown. */
    fun batteryLevel(): Int?

    fun isCharging(): Boolean?

    fun isNetworkAvailable(): Boolean?

    fun isWifiConnected(): Boolean?

    /** Current SSID, or null when disconnected or Android hides the name. */
    fun connectedWifiSsid(): String?

    fun isScreenOn(): Boolean?

    /** Blank address = "is any device connected". */
    fun isBluetoothDeviceConnected(address: String): Boolean?

    fun isAnyBluetoothDeviceConnected(): Boolean?
}
