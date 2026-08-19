package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.SystemEvent

/**
 * Turns raw system callbacks into edge-triggered [SystemEvent]s. Android
 * APIs happily repeat the same state ("Wi-Fi connected" × 3); this class
 * remembers the last observed state per signal and emits an event only on
 * an actual transition.
 *
 * Process recreation policy (state is in-memory by design):
 * - State-reported signals (battery, charging, Wi-Fi, network, headset)
 *   deliver a current-state callback on registration. The FIRST observation
 *   is stored as a baseline and emits nothing — a phone that is already
 *   charging when the process starts did not "just start charging".
 * - Event-reported signals (screen on/off broadcasts, Bluetooth ACL events)
 *   only fire on real transitions, so the first observation IS a transition
 *   and is emitted; identical repeats are still swallowed.
 * Transitions that happen while the process is dead are missed; nothing is
 * persisted, so a restart can never fabricate a false transition.
 *
 * Bluetooth state is tracked per device address, bounded to the newest
 * [maxBluetoothDevices] entries.
 */
class SystemStateTracker(
    private val maxBluetoothDevices: Int = 32
) {

    private var batteryLevel: Int? = null
    private var charging: Boolean? = null
    private var wifi: Pair<Boolean, String?>? = null // connected to ssid
    private var networkAvailable: Boolean? = null
    private var screenOn: Boolean? = null
    private var headsetConnected: Boolean? = null
    private val bluetooth = LinkedHashMap<String, Boolean>() // address -> connected

    /**
     * One battery reading can yield up to two events: a level change and a
     * charging transition. First reading is a baseline and yields none.
     */
    @Synchronized
    fun onBatteryReading(level: Int, isCharging: Boolean, timestamp: Long): List<SystemEvent> {
        val previousLevel = batteryLevel
        val previousCharging = charging
        batteryLevel = level
        charging = isCharging

        if (previousLevel == null || previousCharging == null) return emptyList()

        val events = mutableListOf<SystemEvent>()
        if (level != previousLevel) {
            events += SystemEvent.BatteryChanged(level, previousLevel, isCharging, timestamp)
        }
        if (isCharging != previousCharging) {
            events += SystemEvent.ChargingChanged(isCharging, level, timestamp)
        }
        return events
    }

    /** Connect, disconnect, or roam to a different SSID. Baseline on first call. */
    @Synchronized
    fun onWifiState(connected: Boolean, ssid: String?, timestamp: Long): SystemEvent.WiFiChanged? {
        val previous = wifi
        val current = connected to (if (connected) ssid else null)
        wifi = current
        if (previous == null) return null
        if (previous == current) return null
        // A disconnect carries the SSID of the network that was lost, when known.
        val eventSsid = if (connected) ssid else previous.second
        return SystemEvent.WiFiChanged(connected, eventSsid, timestamp)
    }

    /** Baseline on first call. */
    @Synchronized
    fun onNetworkState(available: Boolean, timestamp: Long): SystemEvent.NetworkChanged? {
        val previous = networkAvailable
        networkAvailable = available
        if (previous == null || previous == available) return null
        return SystemEvent.NetworkChanged(available, timestamp)
    }

    /**
     * Seeds the current screen state at monitor start without emitting —
     * screen broadcasts only fire on transitions, so without a seed the
     * first real transition would be swallowed as a baseline.
     */
    @Synchronized
    fun seedScreenState(on: Boolean) {
        if (screenOn == null) screenOn = on
    }

    /** Screen broadcasts are transitions already — first observation emits. */
    @Synchronized
    fun onScreenState(on: Boolean, timestamp: Long): SystemEvent.ScreenChanged? {
        val previous = screenOn
        screenOn = on
        if (previous == on) return null
        return SystemEvent.ScreenChanged(on, timestamp)
    }

    /** Baseline on first call (audio callbacks report current devices on register). */
    @Synchronized
    fun onHeadsetState(connected: Boolean, deviceName: String, timestamp: Long): SystemEvent.HeadsetChanged? {
        val previous = headsetConnected
        headsetConnected = connected
        if (previous == null || previous == connected) return null
        return SystemEvent.HeadsetChanged(connected, deviceName, timestamp)
    }

    /** ACL broadcasts are transitions already — first observation emits. */
    @Synchronized
    fun onBluetoothEvent(
        connected: Boolean,
        address: String,
        name: String,
        timestamp: Long
    ): SystemEvent.BluetoothChanged? {
        if (address.isBlank()) return null
        val previous = bluetooth[address]
        bluetooth.remove(address)
        bluetooth[address] = connected
        val iterator = bluetooth.entries.iterator()
        while (bluetooth.size > maxBluetoothDevices && iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
        if (previous == connected) return null
        return SystemEvent.BluetoothChanged(connected, address, name, timestamp)
    }

    // Snapshot accessors for the DeviceStateProvider.

    @Synchronized
    fun isBluetoothConnected(address: String): Boolean =
        bluetooth[address.trim()] == true ||
            bluetooth.entries.any { it.key.equals(address.trim(), ignoreCase = true) && it.value }

    @Synchronized
    fun isAnyBluetoothConnected(): Boolean = bluetooth.values.any { it }
}
