package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.Condition
import com.dhruw.autoflow.automation.model.LevelComparison
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemConditionEvaluatorTest {

    private class FakeDeviceState(
        var battery: Int? = 50,
        var charging: Boolean? = false,
        var network: Boolean? = true,
        var wifiConnected: Boolean? = true,
        var ssid: String? = "Home",
        var screenOn: Boolean? = true,
        var connectedBt: Set<String> = emptySet()
    ) : DeviceStateProvider {
        override fun batteryLevel() = battery
        override fun isCharging() = charging
        override fun isNetworkAvailable() = network
        override fun isWifiConnected() = wifiConnected
        override fun connectedWifiSsid() = ssid
        override fun isScreenOn() = screenOn
        override fun isBluetoothDeviceConnected(address: String) =
            connectedBt.any { it.equals(address, ignoreCase = true) }
        override fun isAnyBluetoothDeviceConnected() = connectedBt.isNotEmpty()
    }

    private val state = FakeDeviceState()
    private val evaluator = ConditionEvaluator(state)

    @Test
    fun `battery level condition compares current level`() {
        state.battery = 25
        assertTrue(evaluator.evaluate(Condition.BatteryLevelCondition(LevelComparison.LESS_THAN, 30)))
        assertFalse(evaluator.evaluate(Condition.BatteryLevelCondition(LevelComparison.LESS_THAN, 20)))
        assertTrue(evaluator.evaluate(Condition.BatteryLevelCondition(LevelComparison.GREATER_OR_EQUAL, 25)))
    }

    @Test
    fun `charging condition checks both directions`() {
        state.charging = true
        assertTrue(evaluator.evaluate(Condition.IsChargingCondition(charging = true)))
        assertFalse(evaluator.evaluate(Condition.IsChargingCondition(charging = false)))
        state.charging = false
        assertTrue(evaluator.evaluate(Condition.IsChargingCondition(charging = false)))
    }

    @Test
    fun `network condition checks availability`() {
        assertTrue(evaluator.evaluate(Condition.NetworkAvailableCondition(available = true)))
        state.network = false
        assertTrue(evaluator.evaluate(Condition.NetworkAvailableCondition(available = false)))
        assertFalse(evaluator.evaluate(Condition.NetworkAvailableCondition(available = true)))
    }

    @Test
    fun `wifi condition matches any network or specific ssid`() {
        assertTrue(evaluator.evaluate(Condition.WiFiConnectedCondition()))
        assertTrue(evaluator.evaluate(Condition.WiFiConnectedCondition("home")))
        assertFalse(evaluator.evaluate(Condition.WiFiConnectedCondition("Office")))
        state.ssid = null // SSID hidden: specific check fails, any-network still works
        assertFalse(evaluator.evaluate(Condition.WiFiConnectedCondition("Home")))
        assertTrue(evaluator.evaluate(Condition.WiFiConnectedCondition()))
    }

    @Test
    fun `screen condition checks interactive state`() {
        assertTrue(evaluator.evaluate(Condition.ScreenOnCondition(on = true)))
        state.screenOn = false
        assertTrue(evaluator.evaluate(Condition.ScreenOnCondition(on = false)))
    }

    @Test
    fun `bluetooth condition matches any or specific device`() {
        state.connectedBt = setOf("AA:BB:CC:DD:EE:FF")
        assertTrue(evaluator.evaluate(Condition.BluetoothConnectedCondition()))
        assertTrue(evaluator.evaluate(Condition.BluetoothConnectedCondition("aa:bb:cc:dd:ee:ff")))
        assertFalse(evaluator.evaluate(Condition.BluetoothConnectedCondition("11:22:33:44:55:66")))
        state.connectedBt = emptySet()
        assertFalse(evaluator.evaluate(Condition.BluetoothConnectedCondition()))
    }

    @Test
    fun `unknown state fails instead of guessing`() {
        val unknown = ConditionEvaluator(
            FakeDeviceState(
                battery = null, charging = null, network = null,
                wifiConnected = null, ssid = null, screenOn = null
            )
        )
        assertFalse(unknown.evaluate(Condition.BatteryLevelCondition(LevelComparison.LESS_THAN, 99)))
        assertFalse(unknown.evaluate(Condition.IsChargingCondition(true)))
        assertFalse(unknown.evaluate(Condition.IsChargingCondition(false)))
        assertFalse(unknown.evaluate(Condition.NetworkAvailableCondition(true)))
        assertFalse(unknown.evaluate(Condition.WiFiConnectedCondition()))
        assertFalse(unknown.evaluate(Condition.ScreenOnCondition(true)))
    }

    @Test
    fun `no provider at all fails system conditions`() {
        val bare = ConditionEvaluator()
        assertFalse(bare.evaluate(Condition.IsChargingCondition(true)))
        assertFalse(bare.evaluate(Condition.NetworkAvailableCondition(true)))
    }

    @Test
    fun `system conditions compose with AND OR NOT`() {
        state.battery = 15
        state.charging = false
        // battery < 20 AND NOT charging
        val lowAndNotCharging = Condition.AndCondition(
            listOf(
                Condition.BatteryLevelCondition(LevelComparison.LESS_THAN, 20),
                Condition.NotCondition(Condition.IsChargingCondition(charging = true))
            )
        )
        assertTrue(evaluator.evaluate(lowAndNotCharging))

        state.charging = true
        assertFalse(evaluator.evaluate(lowAndNotCharging))

        // charging OR wifi
        val chargingOrWifi = Condition.OrCondition(
            listOf(
                Condition.IsChargingCondition(charging = true),
                Condition.WiFiConnectedCondition()
            )
        )
        assertTrue(evaluator.evaluate(chargingOrWifi))
        state.charging = false
        state.wifiConnected = false
        assertFalse(evaluator.evaluate(chargingOrWifi))
    }

    @Test
    fun `system condition works with a notification payload`() {
        // "WHEN notification arrives IF battery < 20" — payload type is irrelevant.
        state.battery = 15
        val payload = com.dhruw.autoflow.automation.model.TriggerPayload.NotificationEvent(
            packageName = "com.whatsapp", appName = "WhatsApp", title = "t", text = "x",
            subText = "", timestamp = 0, notificationKey = "k", category = ""
        )
        assertTrue(
            evaluator.evaluate(Condition.BatteryLevelCondition(LevelComparison.LESS_THAN, 20), payload)
        )
    }
}
