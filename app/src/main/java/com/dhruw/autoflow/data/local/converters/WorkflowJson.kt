package com.dhruw.autoflow.data.local.converters

import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.Condition
import com.dhruw.autoflow.automation.model.ConnectionEvent
import com.dhruw.autoflow.automation.model.LevelComparison
import com.dhruw.autoflow.automation.model.TextMatchMode
import com.dhruw.autoflow.automation.model.Trigger
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Thrown when stored workflow JSON cannot be decoded into domain models. */
class WorkflowJsonException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Type-tagged JSON for the polymorphic workflow parts (trigger, conditions,
 * actions). New trigger/condition/action types only need a new `when` branch
 * here — the schema (plain TEXT columns) never changes. Unknown or malformed
 * data throws [WorkflowJsonException]; callers decide whether to drop the row.
 */
object WorkflowJson {

    private const val KEY_TYPE = "type"

    private const val TRIGGER_MANUAL = "manual"
    private const val TRIGGER_TIME = "time"
    private const val TRIGGER_FILE = "file"
    private const val TRIGGER_NOTIFICATION = "notification"
    private const val TRIGGER_BATTERY_LEVEL = "battery_level"
    private const val TRIGGER_CHARGING_STATE = "charging_state"
    private const val TRIGGER_WIFI_CONNECTION = "wifi_connection"
    private const val TRIGGER_NETWORK_AVAILABILITY = "network_availability"
    private const val TRIGGER_BLUETOOTH_CONNECTION = "bluetooth_connection"
    private const val TRIGGER_SCREEN_STATE = "screen_state"
    private const val TRIGGER_HEADSET_CONNECTION = "headset_connection"
    private const val TRIGGER_DEVICE_BOOT = "device_boot"
    private const val CONDITION_ALWAYS = "always"
    private const val CONDITION_FILE_EXTENSION = "file_extension"
    private const val CONDITION_FILE_NAME_CONTAINS = "file_name_contains"
    private const val CONDITION_FILE_SIZE = "file_size"
    private const val CONDITION_NOTIFICATION_APP = "notification_app"
    private const val CONDITION_NOTIFICATION_TITLE = "notification_title"
    private const val CONDITION_NOTIFICATION_TEXT = "notification_text"
    private const val CONDITION_NOTIFICATION_CATEGORY = "notification_category"
    private const val CONDITION_AND = "and"
    private const val CONDITION_OR = "or"
    private const val CONDITION_NOT = "not"
    private const val CONDITION_BATTERY_LEVEL = "battery_level"
    private const val CONDITION_IS_CHARGING = "is_charging"
    private const val CONDITION_NETWORK_AVAILABLE = "network_available"
    private const val CONDITION_WIFI_CONNECTED = "wifi_connected"
    private const val CONDITION_SCREEN_ON = "screen_on"
    private const val CONDITION_BLUETOOTH_CONNECTED = "bluetooth_connected"
    private const val ACTION_SHOW_NOTIFICATION = "show_notification"
    private const val ACTION_DELAY = "delay"
    private const val ACTION_LOG = "log"
    private const val ACTION_COPY_FILE = "copy_file"
    private const val ACTION_MOVE_FILE = "move_file"
    private const val ACTION_RENAME_FILE = "rename_file"
    private const val ACTION_INSTAGRAM_ANALYSIS = "instagram_analysis"
    private const val ACTION_SAVE_NOTIFICATION = "save_notification"

    // --- Trigger ---

    fun encodeTrigger(trigger: Trigger): String = triggerToJson(trigger).toString()

    fun decodeTrigger(raw: String): Trigger = parse(raw) { json ->
        when (val type = json.getString(KEY_TYPE)) {
            TRIGGER_MANUAL -> Trigger.ManualTrigger
            TRIGGER_TIME -> Trigger.TimeTrigger(
                hour = json.getInt("hour"),
                minute = json.getInt("minute"),
                repeat = Trigger.TimeTrigger.Repeat.valueOf(json.getString("repeat"))
            )
            TRIGGER_FILE -> Trigger.FileTrigger(
                folderUri = json.getString("folderUri"),
                folderLabel = json.getString("folderLabel"),
                namePattern = json.optString("namePattern", ""),
                extension = json.optString("extension", "")
            )
            TRIGGER_NOTIFICATION -> Trigger.NotificationTrigger(
                allowedPackages = json.getJSONArray("allowedPackages").let { arr ->
                    List(arr.length()) { arr.getString(it) }.toSet()
                },
                appLabel = json.optString("appLabel", ""),
                titlePattern = json.optString("titlePattern", ""),
                textPattern = json.optString("textPattern", ""),
                matchMode = TextMatchMode.valueOf(json.optString("matchMode", TextMatchMode.CONTAINS.name))
            )
            TRIGGER_BATTERY_LEVEL -> Trigger.BatteryLevelTrigger(
                comparison = LevelComparison.valueOf(json.getString("comparison")),
                level = json.getInt("level")
            )
            TRIGGER_CHARGING_STATE -> Trigger.ChargingStateTrigger(
                onCharging = json.getBoolean("onCharging")
            )
            TRIGGER_WIFI_CONNECTION -> Trigger.WiFiConnectionTrigger(
                event = ConnectionEvent.valueOf(json.getString("event")),
                ssid = json.optString("ssid", "")
            )
            TRIGGER_NETWORK_AVAILABILITY -> Trigger.NetworkAvailabilityTrigger(
                onAvailable = json.getBoolean("onAvailable")
            )
            TRIGGER_BLUETOOTH_CONNECTION -> Trigger.BluetoothConnectionTrigger(
                event = ConnectionEvent.valueOf(json.getString("event")),
                deviceAddress = json.optString("deviceAddress", ""),
                deviceName = json.optString("deviceName", "")
            )
            TRIGGER_SCREEN_STATE -> Trigger.ScreenStateTrigger(
                onScreenOn = json.getBoolean("onScreenOn")
            )
            TRIGGER_HEADSET_CONNECTION -> Trigger.HeadsetConnectionTrigger(
                event = ConnectionEvent.valueOf(json.getString("event"))
            )
            TRIGGER_DEVICE_BOOT -> Trigger.DeviceBootTrigger
            else -> throw WorkflowJsonException("Unknown trigger type: $type")
        }
    }

    private fun triggerToJson(trigger: Trigger): JSONObject = when (trigger) {
        is Trigger.ManualTrigger -> JSONObject().put(KEY_TYPE, TRIGGER_MANUAL)
        is Trigger.TimeTrigger -> JSONObject()
            .put(KEY_TYPE, TRIGGER_TIME)
            .put("hour", trigger.hour)
            .put("minute", trigger.minute)
            .put("repeat", trigger.repeat.name)
        is Trigger.FileTrigger -> JSONObject()
            .put(KEY_TYPE, TRIGGER_FILE)
            .put("folderUri", trigger.folderUri)
            .put("folderLabel", trigger.folderLabel)
            .put("namePattern", trigger.namePattern)
            .put("extension", trigger.extension)
        is Trigger.NotificationTrigger -> JSONObject()
            .put(KEY_TYPE, TRIGGER_NOTIFICATION)
            .put("allowedPackages", JSONArray(trigger.allowedPackages))
            .put("appLabel", trigger.appLabel)
            .put("titlePattern", trigger.titlePattern)
            .put("textPattern", trigger.textPattern)
            .put("matchMode", trigger.matchMode.name)
        is Trigger.BatteryLevelTrigger -> JSONObject()
            .put(KEY_TYPE, TRIGGER_BATTERY_LEVEL)
            .put("comparison", trigger.comparison.name)
            .put("level", trigger.level)
        is Trigger.ChargingStateTrigger -> JSONObject()
            .put(KEY_TYPE, TRIGGER_CHARGING_STATE)
            .put("onCharging", trigger.onCharging)
        is Trigger.WiFiConnectionTrigger -> JSONObject()
            .put(KEY_TYPE, TRIGGER_WIFI_CONNECTION)
            .put("event", trigger.event.name)
            .put("ssid", trigger.ssid)
        is Trigger.NetworkAvailabilityTrigger -> JSONObject()
            .put(KEY_TYPE, TRIGGER_NETWORK_AVAILABILITY)
            .put("onAvailable", trigger.onAvailable)
        is Trigger.BluetoothConnectionTrigger -> JSONObject()
            .put(KEY_TYPE, TRIGGER_BLUETOOTH_CONNECTION)
            .put("event", trigger.event.name)
            .put("deviceAddress", trigger.deviceAddress)
            .put("deviceName", trigger.deviceName)
        is Trigger.ScreenStateTrigger -> JSONObject()
            .put(KEY_TYPE, TRIGGER_SCREEN_STATE)
            .put("onScreenOn", trigger.onScreenOn)
        is Trigger.HeadsetConnectionTrigger -> JSONObject()
            .put(KEY_TYPE, TRIGGER_HEADSET_CONNECTION)
            .put("event", trigger.event.name)
        is Trigger.DeviceBootTrigger -> JSONObject()
            .put(KEY_TYPE, TRIGGER_DEVICE_BOOT)
    }

    // --- Conditions ---

    fun encodeConditions(conditions: List<Condition>): String =
        JSONArray(conditions.map(::conditionToJson)).toString()

    fun decodeConditions(raw: String): List<Condition> = parseArray(raw, ::decodeCondition)

    private fun decodeCondition(json: JSONObject): Condition =
        when (val type = json.getString(KEY_TYPE)) {
            CONDITION_ALWAYS -> Condition.AlwaysCondition
            CONDITION_FILE_EXTENSION ->
                Condition.FileExtensionCondition(extension = json.getString("extension"))
            CONDITION_FILE_NAME_CONTAINS ->
                Condition.FileNameContainsCondition(text = json.getString("text"))
            CONDITION_FILE_SIZE -> Condition.FileSizeCondition(
                comparison = Condition.FileSizeCondition.Comparison.valueOf(json.getString("comparison")),
                sizeBytes = json.getLong("sizeBytes")
            )
            CONDITION_NOTIFICATION_APP -> Condition.NotificationAppCondition(
                packageName = json.getString("packageName"),
                appLabel = json.optString("appLabel", "")
            )
            CONDITION_NOTIFICATION_TITLE -> Condition.NotificationTitleCondition(
                value = json.getString("value"),
                mode = TextMatchMode.valueOf(json.optString("mode", TextMatchMode.CONTAINS.name))
            )
            CONDITION_NOTIFICATION_TEXT -> Condition.NotificationTextCondition(
                value = json.getString("value"),
                mode = TextMatchMode.valueOf(json.optString("mode", TextMatchMode.CONTAINS.name))
            )
            CONDITION_NOTIFICATION_CATEGORY -> Condition.NotificationCategoryCondition(
                category = json.getString("category")
            )
            CONDITION_AND -> Condition.AndCondition(
                conditions = json.getJSONArray("conditions").let { arr ->
                    List(arr.length()) { decodeCondition(arr.getJSONObject(it)) }
                }
            )
            CONDITION_OR -> Condition.OrCondition(
                conditions = json.getJSONArray("conditions").let { arr ->
                    List(arr.length()) { decodeCondition(arr.getJSONObject(it)) }
                }
            )
            CONDITION_NOT -> Condition.NotCondition(
                condition = decodeCondition(json.getJSONObject("condition"))
            )
            CONDITION_BATTERY_LEVEL -> Condition.BatteryLevelCondition(
                comparison = LevelComparison.valueOf(json.getString("comparison")),
                level = json.getInt("level")
            )
            CONDITION_IS_CHARGING -> Condition.IsChargingCondition(
                charging = json.getBoolean("charging")
            )
            CONDITION_NETWORK_AVAILABLE -> Condition.NetworkAvailableCondition(
                available = json.getBoolean("available")
            )
            CONDITION_WIFI_CONNECTED -> Condition.WiFiConnectedCondition(
                ssid = json.optString("ssid", "")
            )
            CONDITION_SCREEN_ON -> Condition.ScreenOnCondition(
                on = json.getBoolean("on")
            )
            CONDITION_BLUETOOTH_CONNECTED -> Condition.BluetoothConnectedCondition(
                deviceAddress = json.optString("deviceAddress", ""),
                deviceName = json.optString("deviceName", "")
            )
            else -> throw WorkflowJsonException("Unknown condition type: $type")
        }

    private fun conditionToJson(condition: Condition): JSONObject = when (condition) {
        is Condition.AlwaysCondition -> JSONObject().put(KEY_TYPE, CONDITION_ALWAYS)
        is Condition.FileExtensionCondition -> JSONObject()
            .put(KEY_TYPE, CONDITION_FILE_EXTENSION)
            .put("extension", condition.extension)
        is Condition.FileNameContainsCondition -> JSONObject()
            .put(KEY_TYPE, CONDITION_FILE_NAME_CONTAINS)
            .put("text", condition.text)
        is Condition.FileSizeCondition -> JSONObject()
            .put(KEY_TYPE, CONDITION_FILE_SIZE)
            .put("comparison", condition.comparison.name)
            .put("sizeBytes", condition.sizeBytes)
        is Condition.NotificationAppCondition -> JSONObject()
            .put(KEY_TYPE, CONDITION_NOTIFICATION_APP)
            .put("packageName", condition.packageName)
            .put("appLabel", condition.appLabel)
        is Condition.NotificationTitleCondition -> JSONObject()
            .put(KEY_TYPE, CONDITION_NOTIFICATION_TITLE)
            .put("value", condition.value)
            .put("mode", condition.mode.name)
        is Condition.NotificationTextCondition -> JSONObject()
            .put(KEY_TYPE, CONDITION_NOTIFICATION_TEXT)
            .put("value", condition.value)
            .put("mode", condition.mode.name)
        is Condition.NotificationCategoryCondition -> JSONObject()
            .put(KEY_TYPE, CONDITION_NOTIFICATION_CATEGORY)
            .put("category", condition.category)
        is Condition.AndCondition -> JSONObject()
            .put(KEY_TYPE, CONDITION_AND)
            .put("conditions", JSONArray(condition.conditions.map(::conditionToJson)))
        is Condition.OrCondition -> JSONObject()
            .put(KEY_TYPE, CONDITION_OR)
            .put("conditions", JSONArray(condition.conditions.map(::conditionToJson)))
        is Condition.NotCondition -> JSONObject()
            .put(KEY_TYPE, CONDITION_NOT)
            .put("condition", conditionToJson(condition.condition))
        is Condition.BatteryLevelCondition -> JSONObject()
            .put(KEY_TYPE, CONDITION_BATTERY_LEVEL)
            .put("comparison", condition.comparison.name)
            .put("level", condition.level)
        is Condition.IsChargingCondition -> JSONObject()
            .put(KEY_TYPE, CONDITION_IS_CHARGING)
            .put("charging", condition.charging)
        is Condition.NetworkAvailableCondition -> JSONObject()
            .put(KEY_TYPE, CONDITION_NETWORK_AVAILABLE)
            .put("available", condition.available)
        is Condition.WiFiConnectedCondition -> JSONObject()
            .put(KEY_TYPE, CONDITION_WIFI_CONNECTED)
            .put("ssid", condition.ssid)
        is Condition.ScreenOnCondition -> JSONObject()
            .put(KEY_TYPE, CONDITION_SCREEN_ON)
            .put("on", condition.on)
        is Condition.BluetoothConnectedCondition -> JSONObject()
            .put(KEY_TYPE, CONDITION_BLUETOOTH_CONNECTED)
            .put("deviceAddress", condition.deviceAddress)
            .put("deviceName", condition.deviceName)
    }

    // --- Actions ---

    fun encodeActions(actions: List<Action>): String =
        JSONArray(actions.map(::actionToJson)).toString()

    fun decodeActions(raw: String): List<Action> = parseArray(raw) { json ->
        when (val type = json.getString(KEY_TYPE)) {
            ACTION_SHOW_NOTIFICATION -> Action.ShowNotificationAction(
                title = json.getString("title"),
                message = json.getString("message")
            )
            ACTION_DELAY -> Action.DelayAction(durationMillis = json.getLong("durationMillis"))
            ACTION_LOG -> Action.LogAction(message = json.getString("message"))
            ACTION_COPY_FILE -> Action.CopyFileAction(
                destinationFolderUri = json.getString("destinationFolderUri"),
                destinationLabel = json.getString("destinationLabel")
            )
            ACTION_MOVE_FILE -> Action.MoveFileAction(
                destinationFolderUri = json.getString("destinationFolderUri"),
                destinationLabel = json.getString("destinationLabel")
            )
            ACTION_RENAME_FILE -> Action.RenameFileAction(newName = json.getString("newName"))
            ACTION_INSTAGRAM_ANALYSIS -> Action.InstagramAnalysisAction
            ACTION_SAVE_NOTIFICATION -> Action.SaveNotificationAction
            else -> throw WorkflowJsonException("Unknown action type: $type")
        }
    }

    private fun actionToJson(action: Action): JSONObject = when (action) {
        is Action.ShowNotificationAction -> JSONObject()
            .put(KEY_TYPE, ACTION_SHOW_NOTIFICATION)
            .put("title", action.title)
            .put("message", action.message)
        is Action.DelayAction -> JSONObject()
            .put(KEY_TYPE, ACTION_DELAY)
            .put("durationMillis", action.durationMillis)
        is Action.LogAction -> JSONObject()
            .put(KEY_TYPE, ACTION_LOG)
            .put("message", action.message)
        is Action.CopyFileAction -> JSONObject()
            .put(KEY_TYPE, ACTION_COPY_FILE)
            .put("destinationFolderUri", action.destinationFolderUri)
            .put("destinationLabel", action.destinationLabel)
        is Action.MoveFileAction -> JSONObject()
            .put(KEY_TYPE, ACTION_MOVE_FILE)
            .put("destinationFolderUri", action.destinationFolderUri)
            .put("destinationLabel", action.destinationLabel)
        is Action.RenameFileAction -> JSONObject()
            .put(KEY_TYPE, ACTION_RENAME_FILE)
            .put("newName", action.newName)
        is Action.InstagramAnalysisAction -> JSONObject()
            .put(KEY_TYPE, ACTION_INSTAGRAM_ANALYSIS)
        is Action.SaveNotificationAction -> JSONObject()
            .put(KEY_TYPE, ACTION_SAVE_NOTIFICATION)
    }

    // --- Log lines (not polymorphic, but stored the same way) ---

    fun encodeLogs(logs: List<String>): String = JSONArray(logs).toString()

    fun decodeLogs(raw: String): List<String> = try {
        val array = JSONArray(raw)
        List(array.length()) { array.getString(it) }
    } catch (e: JSONException) {
        throw WorkflowJsonException("Malformed log JSON", e)
    }

    // --- Helpers ---

    private inline fun <T> parse(raw: String, map: (JSONObject) -> T): T = try {
        map(JSONObject(raw))
    } catch (e: JSONException) {
        throw WorkflowJsonException("Malformed workflow JSON: $raw", e)
    } catch (e: IllegalArgumentException) {
        throw WorkflowJsonException("Malformed workflow JSON: $raw", e)
    }

    private inline fun <T> parseArray(raw: String, map: (JSONObject) -> T): List<T> = try {
        val array = JSONArray(raw)
        List(array.length()) { map(array.getJSONObject(it)) }
    } catch (e: JSONException) {
        throw WorkflowJsonException("Malformed workflow JSON: $raw", e)
    } catch (e: IllegalArgumentException) {
        throw WorkflowJsonException("Malformed workflow JSON: $raw", e)
    }
}
