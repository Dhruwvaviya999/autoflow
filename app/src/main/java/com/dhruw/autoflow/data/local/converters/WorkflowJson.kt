package com.dhruw.autoflow.data.local.converters

import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.Condition
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
    private const val CONDITION_ALWAYS = "always"
    private const val CONDITION_FILE_EXTENSION = "file_extension"
    private const val CONDITION_FILE_NAME_CONTAINS = "file_name_contains"
    private const val CONDITION_FILE_SIZE = "file_size"
    private const val ACTION_SHOW_NOTIFICATION = "show_notification"
    private const val ACTION_DELAY = "delay"
    private const val ACTION_LOG = "log"
    private const val ACTION_COPY_FILE = "copy_file"
    private const val ACTION_MOVE_FILE = "move_file"
    private const val ACTION_RENAME_FILE = "rename_file"
    private const val ACTION_INSTAGRAM_ANALYSIS = "instagram_analysis"

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
    }

    // --- Conditions ---

    fun encodeConditions(conditions: List<Condition>): String =
        JSONArray(conditions.map(::conditionToJson)).toString()

    fun decodeConditions(raw: String): List<Condition> = parseArray(raw) { json ->
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
            else -> throw WorkflowJsonException("Unknown condition type: $type")
        }
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
