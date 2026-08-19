package com.dhruw.autoflow.automation.model

/**
 * What starts an automation. Extensible: future phases add
 * FileTrigger, NotificationTrigger, AppTrigger, etc.
 */
sealed interface Trigger {

    /** The user explicitly presses Run. */
    data object ManualTrigger : Trigger

    /**
     * A scheduled execution. Pure schedule description — actual
     * scheduling is done by [com.dhruw.autoflow.automation.engine.AutomationScheduler].
     */
    data class TimeTrigger(
        val hour: Int,
        val minute: Int,
        val repeat: Repeat
    ) : Trigger {
        enum class Repeat { ONCE, DAILY }
    }

    /**
     * Fires when a new file matching the filters appears in a user-selected
     * folder. [folderUri] is an opaque storage URI (SAF tree URI on Android);
     * the domain never assumes raw filesystem paths. Blank filter fields
     * match everything.
     */
    data class FileTrigger(
        val folderUri: String,
        val folderLabel: String,
        val namePattern: String = "",
        val extension: String = ""
    ) : Trigger {
        /** ".ZIP" / "zip" / " .Zip " all normalize to "zip". */
        val normalizedExtension: String
            get() = extension.trim().removePrefix(".").lowercase()
    }

    /**
     * Fires when another app posts a notification. Generic by design: no
     * application is special-cased. An empty [allowedPackages] means "any
     * app"; [appLabel] is only the display name captured when the user picked
     * an app (package names stay the stable identifier). Blank patterns
     * match everything; non-blank ones are compared with [matchMode],
     * case-insensitively.
     */
    data class NotificationTrigger(
        val allowedPackages: Set<String> = emptySet(),
        val appLabel: String = "",
        val titlePattern: String = "",
        val textPattern: String = "",
        val matchMode: TextMatchMode = TextMatchMode.CONTAINS
    ) : Trigger {

        /** Cheap first-level filter run for every incoming notification. */
        fun matches(event: TriggerPayload.NotificationEvent): Boolean {
            if (allowedPackages.isNotEmpty() && event.packageName !in allowedPackages) return false
            if (titlePattern.isNotBlank() && !matchMode.matches(event.title, titlePattern)) return false
            if (textPattern.isNotBlank() && !matchMode.matches(event.text, textPattern)) return false
            return true
        }
    }

    // System triggers (Phase 6). All are edge-triggered: they fire on an
    // actual state transition, never repeatedly while a state persists.
    // Each `matches` is the cheap first-level filter the dispatcher runs.

    /**
     * Fires when the battery level crosses INTO the configured zone — e.g.
     * "at or below 20%" fires on 21% → 19%, then stays silent through
     * 19% → 18% → 17%. An unknown previous level (first reading after
     * process start) never fires.
     */
    data class BatteryLevelTrigger(
        val comparison: LevelComparison,
        val level: Int
    ) : Trigger {
        fun matches(event: SystemEvent.BatteryChanged): Boolean {
            val previous = event.previousLevel ?: return false
            return comparison.matches(event.level, level) && !comparison.matches(previous, level)
        }
    }

    /** Fires when charging starts ([onCharging] true) or stops (false). */
    data class ChargingStateTrigger(val onCharging: Boolean) : Trigger {
        fun matches(event: SystemEvent.ChargingChanged): Boolean =
            event.isCharging == onCharging
    }

    /**
     * Fires on Wi-Fi connect/disconnect. Blank [ssid] matches any network.
     * A configured SSID compares case-insensitively; when Android hides the
     * SSID (no location permission), an SSID-filtered trigger cannot match.
     */
    data class WiFiConnectionTrigger(
        val event: ConnectionEvent,
        val ssid: String = ""
    ) : Trigger {
        fun matches(e: SystemEvent.WiFiChanged): Boolean {
            val wantConnected = event == ConnectionEvent.CONNECTED
            if (e.connected != wantConnected) return false
            if (ssid.isBlank()) return true
            return e.ssid != null && e.ssid.equals(ssid.trim(), ignoreCase = true)
        }
    }

    /** Fires when the default network becomes available/unavailable. */
    data class NetworkAvailabilityTrigger(val onAvailable: Boolean) : Trigger {
        fun matches(event: SystemEvent.NetworkChanged): Boolean =
            event.available == onAvailable
    }

    /**
     * Fires on Bluetooth device connect/disconnect. Blank [deviceAddress]
     * matches any device; otherwise the stable MAC address is compared.
     * [deviceName] is display-only.
     */
    data class BluetoothConnectionTrigger(
        val event: ConnectionEvent,
        val deviceAddress: String = "",
        val deviceName: String = ""
    ) : Trigger {
        fun matches(e: SystemEvent.BluetoothChanged): Boolean {
            val wantConnected = event == ConnectionEvent.CONNECTED
            if (e.connected != wantConnected) return false
            if (deviceAddress.isBlank()) return true
            return e.deviceAddress.equals(deviceAddress.trim(), ignoreCase = true)
        }
    }

    /** Fires when the screen turns on ([onScreenOn] true) or off (false). */
    data class ScreenStateTrigger(val onScreenOn: Boolean) : Trigger {
        fun matches(event: SystemEvent.ScreenChanged): Boolean =
            event.on == onScreenOn
    }

    /** Fires when a wired/Bluetooth audio device connects or disconnects. */
    data class HeadsetConnectionTrigger(val event: ConnectionEvent) : Trigger {
        fun matches(e: SystemEvent.HeadsetChanged): Boolean =
            e.connected == (event == ConnectionEvent.CONNECTED)
    }

    /** Fires once after Android finishes booting. */
    data object DeviceBootTrigger : Trigger
}

val Trigger.displayName: String
    get() = when (this) {
        is Trigger.ManualTrigger -> "Manual"
        is Trigger.TimeTrigger -> "Time"
        is Trigger.FileTrigger -> "File"
        is Trigger.NotificationTrigger -> "Notification"
        is Trigger.BatteryLevelTrigger -> "Battery level"
        is Trigger.ChargingStateTrigger -> "Charging"
        is Trigger.WiFiConnectionTrigger -> "Wi-Fi"
        is Trigger.NetworkAvailabilityTrigger -> "Network"
        is Trigger.BluetoothConnectionTrigger -> "Bluetooth"
        is Trigger.ScreenStateTrigger -> "Screen"
        is Trigger.HeadsetConnectionTrigger -> "Headset"
        is Trigger.DeviceBootTrigger -> "Device boot"
    }

val Trigger.summary: String
    get() = when (this) {
        is Trigger.ManualTrigger -> "Run it yourself"
        is Trigger.TimeTrigger -> {
            val amPm = if (hour < 12) "AM" else "PM"
            val displayHour = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            val time = "%d:%02d %s".format(displayHour, minute, amPm)
            when (repeat) {
                Trigger.TimeTrigger.Repeat.ONCE -> "Once · $time"
                Trigger.TimeTrigger.Repeat.DAILY -> "Every day · $time"
            }
        }
        is Trigger.FileTrigger -> buildString {
            append("New file in ").append(folderLabel.ifBlank { "selected folder" })
            if (normalizedExtension.isNotEmpty()) append(" · .").append(normalizedExtension)
            if (namePattern.isNotBlank()) append(" · name has \"").append(namePattern.trim()).append("\"")
        }
        is Trigger.NotificationTrigger -> buildString {
            append("Notification from ")
            append(
                when {
                    allowedPackages.isEmpty() -> "any app"
                    appLabel.isNotBlank() -> appLabel
                    else -> allowedPackages.first()
                }
            )
            if (titlePattern.isNotBlank()) append(" · title has \"").append(titlePattern.trim()).append("\"")
            if (textPattern.isNotBlank()) append(" · text has \"").append(textPattern.trim()).append("\"")
        }
        is Trigger.BatteryLevelTrigger -> "Battery crosses ${comparison.label.lowercase()} $level%"
        is Trigger.ChargingStateTrigger ->
            if (onCharging) "Charging starts" else "Charging stops"
        is Trigger.WiFiConnectionTrigger -> buildString {
            append("Wi-Fi ")
            append(if (event == ConnectionEvent.CONNECTED) "connects" else "disconnects")
            if (ssid.isNotBlank()) append(" · \"").append(ssid.trim()).append("\"")
            else append(" · any network")
        }
        is Trigger.NetworkAvailabilityTrigger ->
            if (onAvailable) "Network becomes available" else "Network becomes unavailable"
        is Trigger.BluetoothConnectionTrigger -> buildString {
            append("Bluetooth device ")
            append(if (event == ConnectionEvent.CONNECTED) "connects" else "disconnects")
            when {
                deviceName.isNotBlank() -> append(" · ").append(deviceName.trim())
                deviceAddress.isNotBlank() -> append(" · ").append(deviceAddress.trim())
                else -> append(" · any device")
            }
        }
        is Trigger.ScreenStateTrigger ->
            if (onScreenOn) "Screen turns on" else "Screen turns off"
        is Trigger.HeadsetConnectionTrigger ->
            if (event == ConnectionEvent.CONNECTED) "Headset connects" else "Headset disconnects"
        is Trigger.DeviceBootTrigger -> "After the device restarts"
    }
