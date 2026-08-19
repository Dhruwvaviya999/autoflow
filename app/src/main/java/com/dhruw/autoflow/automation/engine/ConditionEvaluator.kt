package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.Condition
import com.dhruw.autoflow.automation.model.TriggerPayload
import java.util.Locale

/**
 * Evaluates a condition tree, recursing through And/Or/Not composites.
 *
 * File conditions need the run's [TriggerPayload.FileEvent] and notification
 * conditions the run's [TriggerPayload.NotificationEvent]; without the right
 * payload they fail, which turns the run into a SKIPPED execution rather
 * than a crash or a blind pass. System conditions read the CURRENT device
 * state from [deviceState]; without a provider (or when a value is unknown)
 * they fail the same way.
 */
open class ConditionEvaluator(
    private val deviceState: DeviceStateProvider? = null
) {

    open fun evaluate(condition: Condition, payload: TriggerPayload? = null): Boolean {
        val file = payload as? TriggerPayload.FileEvent
        val notification = payload as? TriggerPayload.NotificationEvent
        return when (condition) {
            is Condition.AlwaysCondition -> true
            is Condition.FileExtensionCondition ->
                file != null &&
                    file.extension.trim().removePrefix(".").lowercase(Locale.ROOT) ==
                    condition.normalizedExtension
            is Condition.FileNameContainsCondition ->
                file != null &&
                    condition.text.isNotBlank() &&
                    file.name.lowercase(Locale.ROOT)
                        .contains(condition.text.trim().lowercase(Locale.ROOT))
            is Condition.FileSizeCondition -> file != null && when (condition.comparison) {
                Condition.FileSizeCondition.Comparison.LESS_THAN ->
                    file.sizeBytes < condition.sizeBytes
                Condition.FileSizeCondition.Comparison.GREATER_THAN ->
                    file.sizeBytes > condition.sizeBytes
            }
            is Condition.NotificationAppCondition ->
                notification != null &&
                    condition.packageName.isNotBlank() &&
                    notification.packageName.equals(condition.packageName.trim(), ignoreCase = true)
            is Condition.NotificationTitleCondition ->
                notification != null &&
                    condition.value.isNotBlank() &&
                    condition.mode.matches(notification.title, condition.value)
            is Condition.NotificationTextCondition ->
                notification != null &&
                    condition.value.isNotBlank() &&
                    condition.mode.matches(notification.text, condition.value)
            is Condition.NotificationCategoryCondition ->
                notification != null &&
                    condition.category.isNotBlank() &&
                    notification.category.equals(condition.category.trim(), ignoreCase = true)
            is Condition.AndCondition -> condition.conditions.all { evaluate(it, payload) }
            is Condition.OrCondition -> condition.conditions.any { evaluate(it, payload) }
            is Condition.NotCondition -> !evaluate(condition.condition, payload)
            is Condition.BatteryLevelCondition -> deviceState?.batteryLevel()
                ?.let { condition.comparison.matches(it, condition.level) } == true
            is Condition.IsChargingCondition ->
                deviceState?.isCharging() == condition.charging
            is Condition.NetworkAvailableCondition ->
                deviceState?.isNetworkAvailable() == condition.available
            is Condition.WiFiConnectedCondition ->
                if (condition.ssid.isBlank()) {
                    deviceState?.isWifiConnected() == true
                } else {
                    deviceState?.connectedWifiSsid()
                        ?.equals(condition.ssid.trim(), ignoreCase = true) == true
                }
            is Condition.ScreenOnCondition ->
                deviceState?.isScreenOn() == condition.on
            is Condition.BluetoothConnectedCondition ->
                if (condition.deviceAddress.isBlank()) {
                    deviceState?.isAnyBluetoothDeviceConnected() == true
                } else {
                    deviceState?.isBluetoothDeviceConnected(condition.deviceAddress.trim()) == true
                }
        }
    }

    /** All conditions must pass; an empty list passes. */
    open fun evaluateAll(conditions: List<Condition>, payload: TriggerPayload? = null): Boolean =
        conditions.all { evaluate(it, payload) }
}
