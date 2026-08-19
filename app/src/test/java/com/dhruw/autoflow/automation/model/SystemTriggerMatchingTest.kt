package com.dhruw.autoflow.automation.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemTriggerMatchingTest {

    // --- Battery crossing (spec: 25→21 no, 21→19 yes, 19→18 no dup) ---

    private val atMost20 = Trigger.BatteryLevelTrigger(LevelComparison.LESS_OR_EQUAL, 20)

    private fun battery(level: Int, previous: Int?) =
        SystemEvent.BatteryChanged(level, previous, isCharging = false, timestamp = 0)

    @Test
    fun `fires only when crossing into the zone`() {
        assertFalse(atMost20.matches(battery(21, 25)))
        assertTrue(atMost20.matches(battery(19, 21)))
        assertFalse(atMost20.matches(battery(18, 19))) // already in zone
        assertFalse(atMost20.matches(battery(17, 18)))
    }

    @Test
    fun `unknown previous level never fires`() {
        assertFalse(atMost20.matches(battery(15, null)))
    }

    @Test
    fun `fires again after leaving and re-entering the zone`() {
        assertTrue(atMost20.matches(battery(20, 21)))
        assertFalse(atMost20.matches(battery(21, 20))) // left the zone: no fire
        assertTrue(atMost20.matches(battery(20, 21))) // re-entered
    }

    @Test
    fun `greater-or-equal crossing works upward`() {
        val atLeast80 = Trigger.BatteryLevelTrigger(LevelComparison.GREATER_OR_EQUAL, 80)
        assertTrue(atLeast80.matches(battery(80, 79)))
        assertFalse(atLeast80.matches(battery(81, 80)))
        assertFalse(atLeast80.matches(battery(50, 49)))
    }

    // --- Charging ---

    @Test
    fun `charging trigger matches configured direction`() {
        val starts = Trigger.ChargingStateTrigger(onCharging = true)
        val stops = Trigger.ChargingStateTrigger(onCharging = false)
        val started = SystemEvent.ChargingChanged(isCharging = true, batteryLevel = 50, timestamp = 0)
        val stopped = SystemEvent.ChargingChanged(isCharging = false, batteryLevel = 50, timestamp = 0)

        assertTrue(starts.matches(started))
        assertFalse(starts.matches(stopped))
        assertTrue(stops.matches(stopped))
        assertFalse(stops.matches(started))
    }

    // --- Wi-Fi ---

    private fun wifi(connected: Boolean, ssid: String?) =
        SystemEvent.WiFiChanged(connected, ssid, timestamp = 0)

    @Test
    fun `any-network wifi trigger matches every ssid`() {
        val trigger = Trigger.WiFiConnectionTrigger(ConnectionEvent.CONNECTED)
        assertTrue(trigger.matches(wifi(true, "Home")))
        assertTrue(trigger.matches(wifi(true, null))) // hidden SSID still counts
        assertFalse(trigger.matches(wifi(false, "Home")))
    }

    @Test
    fun `specific ssid matches case-insensitively and only that network`() {
        val trigger = Trigger.WiFiConnectionTrigger(ConnectionEvent.CONNECTED, ssid = "Home")
        assertTrue(trigger.matches(wifi(true, "home")))
        assertFalse(trigger.matches(wifi(true, "Office")))
        assertFalse(trigger.matches(wifi(true, null))) // hidden SSID cannot be verified
    }

    @Test
    fun `wifi disconnect trigger with ssid matches the lost network`() {
        val trigger = Trigger.WiFiConnectionTrigger(ConnectionEvent.DISCONNECTED, ssid = "Home")
        assertTrue(trigger.matches(wifi(false, "Home")))
        assertFalse(trigger.matches(wifi(false, "Office")))
    }

    // --- Network ---

    @Test
    fun `network trigger matches configured direction`() {
        val onAvailable = Trigger.NetworkAvailabilityTrigger(onAvailable = true)
        assertTrue(onAvailable.matches(SystemEvent.NetworkChanged(true, 0)))
        assertFalse(onAvailable.matches(SystemEvent.NetworkChanged(false, 0)))
    }

    // --- Bluetooth ---

    private fun bt(connected: Boolean, address: String = "AA:BB:CC:DD:EE:FF", name: String = "Car") =
        SystemEvent.BluetoothChanged(connected, address, name, timestamp = 0)

    @Test
    fun `any-device bluetooth trigger matches every device`() {
        val trigger = Trigger.BluetoothConnectionTrigger(ConnectionEvent.CONNECTED)
        assertTrue(trigger.matches(bt(true)))
        assertTrue(trigger.matches(bt(true, address = "11:22:33:44:55:66", name = "Buds")))
        assertFalse(trigger.matches(bt(false)))
    }

    @Test
    fun `specific device matches by stable address not name`() {
        val trigger = Trigger.BluetoothConnectionTrigger(
            ConnectionEvent.CONNECTED,
            deviceAddress = "aa:bb:cc:dd:ee:ff",
            deviceName = "Old Name"
        )
        assertTrue(trigger.matches(bt(true, name = "Renamed Car"))) // name ignored
        assertFalse(trigger.matches(bt(true, address = "11:22:33:44:55:66")))
    }

    // --- Screen / headset / boot ---

    @Test
    fun `screen trigger matches configured direction`() {
        val onOn = Trigger.ScreenStateTrigger(onScreenOn = true)
        assertTrue(onOn.matches(SystemEvent.ScreenChanged(true, 0)))
        assertFalse(onOn.matches(SystemEvent.ScreenChanged(false, 0)))
    }

    @Test
    fun `headset trigger matches configured direction`() {
        val onConnect = Trigger.HeadsetConnectionTrigger(ConnectionEvent.CONNECTED)
        assertTrue(onConnect.matches(SystemEvent.HeadsetChanged(true, "Buds", 0)))
        assertFalse(onConnect.matches(SystemEvent.HeadsetChanged(false, "", 0)))
    }
}
