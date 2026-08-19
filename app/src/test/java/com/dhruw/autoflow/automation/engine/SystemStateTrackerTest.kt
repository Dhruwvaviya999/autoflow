package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.SystemEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemStateTrackerTest {

    private val tracker = SystemStateTracker()

    // --- Battery / charging ---

    @Test
    fun `first battery reading is a baseline and emits nothing`() {
        assertTrue(tracker.onBatteryReading(level = 50, isCharging = true, timestamp = 1).isEmpty())
    }

    @Test
    fun `level change emits BatteryChanged with previous level`() {
        tracker.onBatteryReading(21, false, 1)
        val events = tracker.onBatteryReading(19, false, 2)

        val battery = events.filterIsInstance<SystemEvent.BatteryChanged>().single()
        assertEquals(19, battery.level)
        assertEquals(21, battery.previousLevel)
    }

    @Test
    fun `same level emits nothing`() {
        tracker.onBatteryReading(50, false, 1)
        assertTrue(tracker.onBatteryReading(50, false, 2).isEmpty())
    }

    @Test
    fun `charging transition emits once and not while state persists`() {
        tracker.onBatteryReading(50, false, 1)

        val plugged = tracker.onBatteryReading(50, true, 2)
        assertEquals(1, plugged.filterIsInstance<SystemEvent.ChargingChanged>().size)
        assertTrue(plugged.filterIsInstance<SystemEvent.ChargingChanged>().single().isCharging)

        // Still charging: no repeat.
        assertTrue(tracker.onBatteryReading(50, true, 3).isEmpty())

        val unplugged = tracker.onBatteryReading(50, false, 4)
        assertFalse(unplugged.filterIsInstance<SystemEvent.ChargingChanged>().single().isCharging)
    }

    @Test
    fun `already charging at process start is not charging started`() {
        val events = tracker.onBatteryReading(80, true, 1)
        assertTrue(events.isEmpty())
        // First real transition still works.
        val next = tracker.onBatteryReading(80, false, 2)
        assertEquals(1, next.size)
    }

    @Test
    fun `one reading can emit level and charging together`() {
        tracker.onBatteryReading(50, false, 1)
        val events = tracker.onBatteryReading(49, true, 2)
        assertEquals(2, events.size)
    }

    // --- Wi-Fi ---

    @Test
    fun `wifi baseline then edge transitions`() {
        assertNull(tracker.onWifiState(true, "Home", 1)) // baseline
        assertNull(tracker.onWifiState(true, "Home", 2)) // repeat
        val lost = tracker.onWifiState(false, null, 3)
        assertNotNull(lost)
        assertFalse(lost!!.connected)
        assertEquals("Home", lost.ssid) // disconnect carries the lost SSID
        val reconnected = tracker.onWifiState(true, "Home", 4)
        assertTrue(reconnected!!.connected)
    }

    @Test
    fun `roaming to a different ssid while connected emits`() {
        tracker.onWifiState(true, "Home", 1)
        val roamed = tracker.onWifiState(true, "Office", 2)
        assertNotNull(roamed)
        assertEquals("Office", roamed!!.ssid)
        assertTrue(roamed.connected)
    }

    // --- Network ---

    @Test
    fun `network baseline then edge transitions`() {
        assertNull(tracker.onNetworkState(true, 1)) // baseline
        assertNull(tracker.onNetworkState(true, 2)) // repeat
        assertNotNull(tracker.onNetworkState(false, 3))
        assertNull(tracker.onNetworkState(false, 4))
        assertNotNull(tracker.onNetworkState(true, 5))
    }

    // --- Screen ---

    @Test
    fun `seeded screen state prevents duplicate and keeps first transition`() {
        tracker.seedScreenState(true)
        assertNull(tracker.onScreenState(true, 1)) // same as seed
        val off = tracker.onScreenState(false, 2)
        assertNotNull(off)
        assertFalse(off!!.on)
        assertNull(tracker.onScreenState(false, 3))
    }

    @Test
    fun `unseeded screen event emits because broadcasts are transitions`() {
        assertNotNull(tracker.onScreenState(true, 1))
    }

    // --- Headset ---

    @Test
    fun `headset baseline then edge transitions`() {
        assertNull(tracker.onHeadsetState(false, "", 1)) // registration report
        val connected = tracker.onHeadsetState(true, "Buds", 2)
        assertNotNull(connected)
        assertEquals("Buds", connected!!.deviceName)
        assertNull(tracker.onHeadsetState(true, "Buds", 3))
        assertNotNull(tracker.onHeadsetState(false, "", 4))
    }

    // --- Bluetooth ---

    @Test
    fun `bluetooth per-device edge detection`() {
        val first = tracker.onBluetoothEvent(true, "AA:BB", "Car", 1)
        assertNotNull(first) // ACL broadcast is a real transition
        assertNull(tracker.onBluetoothEvent(true, "AA:BB", "Car", 2)) // repeat
        assertNotNull(tracker.onBluetoothEvent(true, "CC:DD", "Buds", 3)) // other device
        val disconnected = tracker.onBluetoothEvent(false, "AA:BB", "Car", 4)
        assertNotNull(disconnected)
        assertFalse(disconnected!!.connected)
    }

    @Test
    fun `bluetooth device map is bounded`() {
        val small = SystemStateTracker(maxBluetoothDevices = 2)
        small.onBluetoothEvent(true, "A", "", 1)
        small.onBluetoothEvent(true, "B", "", 2)
        small.onBluetoothEvent(true, "C", "", 3) // evicts A
        // A was evicted, so a repeated connect emits again — bounded beats perfect.
        assertNotNull(small.onBluetoothEvent(true, "A", "", 4))
    }

    @Test
    fun `bluetooth snapshot answers condition queries`() {
        tracker.onBluetoothEvent(true, "AA:BB", "Car", 1)
        assertTrue(tracker.isBluetoothConnected("AA:BB"))
        assertTrue(tracker.isAnyBluetoothConnected())
        tracker.onBluetoothEvent(false, "AA:BB", "Car", 2)
        assertFalse(tracker.isBluetoothConnected("AA:BB"))
        assertFalse(tracker.isAnyBluetoothConnected())
    }
}
