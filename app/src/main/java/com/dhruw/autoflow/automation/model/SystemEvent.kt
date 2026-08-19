package com.dhruw.autoflow.automation.model

/**
 * Device/system state transitions, mapped from Android by the monitors in
 * services/system and consumed by triggers and the dispatcher. Pure domain —
 * no Android types. Every event represents an actual transition: the
 * SystemStateTracker filters out repeated identical callbacks before an
 * event is created (edge-trigger semantics).
 */
sealed interface SystemEvent : TriggerPayload {

    val timestamp: Long

    /**
     * Battery level moved to a new percentage. [previousLevel] is null when
     * this is the first reading after process start — threshold triggers
     * never fire on an unknown previous level, so process recreation cannot
     * cause false "crossed the threshold" executions.
     */
    data class BatteryChanged(
        val level: Int,
        val previousLevel: Int?,
        val isCharging: Boolean,
        override val timestamp: Long
    ) : SystemEvent

    /** Charger plugged in ([isCharging] true) or unplugged (false). */
    data class ChargingChanged(
        val isCharging: Boolean,
        val batteryLevel: Int,
        override val timestamp: Long
    ) : SystemEvent

    /**
     * Wi-Fi connected/disconnected or roamed to a different network.
     * [ssid] is null when Android does not expose it (missing location
     * permission, platform redaction); a disconnect carries the SSID of the
     * network that was lost when it was known.
     */
    data class WiFiChanged(
        val connected: Boolean,
        val ssid: String?,
        override val timestamp: Long
    ) : SystemEvent

    /** Default network (any transport) became available/unavailable. */
    data class NetworkChanged(
        val available: Boolean,
        override val timestamp: Long
    ) : SystemEvent

    /**
     * A Bluetooth device connected/disconnected. [deviceAddress] is the
     * stable identifier; [deviceName] is display-only and may be empty when
     * the BLUETOOTH_CONNECT permission is missing.
     */
    data class BluetoothChanged(
        val connected: Boolean,
        val deviceAddress: String,
        val deviceName: String,
        override val timestamp: Long
    ) : SystemEvent

    /** Screen turned on/off. */
    data class ScreenChanged(
        val on: Boolean,
        override val timestamp: Long
    ) : SystemEvent

    /** A wired or Bluetooth audio output device connected/disconnected. */
    data class HeadsetChanged(
        val connected: Boolean,
        val deviceName: String,
        override val timestamp: Long
    ) : SystemEvent

    /** Android finished booting. */
    data class DeviceBooted(
        override val timestamp: Long
    ) : SystemEvent
}
