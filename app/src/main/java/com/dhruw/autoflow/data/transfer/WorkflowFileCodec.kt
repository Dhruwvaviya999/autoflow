package com.dhruw.autoflow.data.transfer

import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.data.local.converters.WorkflowJson
import com.dhruw.autoflow.data.local.converters.WorkflowJsonException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Thrown when a workflow file cannot be read as a valid AutoFlow export. */
class WorkflowFileException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * What a file claims to be. Backups additionally carry transferable settings;
 * both kinds decode into the same automation definitions.
 */
enum class TransferKind { WORKFLOWS, BACKUP }

/** One decoded automation definition — no id, no history, no device data. */
data class WorkflowDefinition(
    val name: String,
    val description: String,
    val automation: Automation
)

/** A decoded file: what it claims to be plus the definitions it carries. */
data class TransferPayload(
    val kind: TransferKind,
    val exportedAt: Long,
    val appVersion: String,
    val definitions: List<WorkflowDefinition>
)

/**
 * Reads and writes `.autoflow` files: a small versioned JSON envelope around
 * the same type-tagged workflow JSON the database stores.
 *
 * Deliberately excluded from every export — this is the privacy contract for
 * sharing: automation ids, execution history, saved notification records,
 * notification or screen content, Instagram exports, folder contents, device
 * identifiers. A file contains only what the user configured: the trigger,
 * the conditions and the steps.
 *
 * Unknown schema versions and malformed files are rejected with a clear
 * message; nothing is imported partially.
 */
object WorkflowFileCodec {

    /** Bumped only when the envelope itself changes shape. */
    const val SCHEMA_VERSION = 1

    const val FORMAT = "autoflow"
    const val FILE_EXTENSION = "autoflow"
    const val MIME_TYPE = "application/json"

    /**
     * MIME used when asking the storage picker to create the file. The
     * content is JSON, but DocumentsUI rewrites the display name to match
     * the type it is given — with "application/json" it saves
     * "My workflow.autoflow.json". A type with no registered extension
     * leaves the ".autoflow" name the user was shown intact.
     */
    const val CREATE_DOCUMENT_MIME_TYPE = "application/octet-stream"

    private const val KEY_FORMAT = "format"
    private const val KEY_SCHEMA_VERSION = "schemaVersion"
    private const val KEY_KIND = "kind"
    private const val KEY_EXPORTED_AT = "exportedAt"
    private const val KEY_APP_VERSION = "appVersion"
    private const val KEY_AUTOMATIONS = "automations"

    fun encode(
        automations: List<Automation>,
        kind: TransferKind = TransferKind.WORKFLOWS,
        exportedAt: Long,
        appVersion: String
    ): String {
        val array = JSONArray()
        automations.forEach { automation ->
            array.put(
                JSONObject()
                    .put("name", automation.name)
                    .put("description", automation.description)
                    .put("trigger", JSONObject(WorkflowJson.encodeTrigger(automation.trigger)))
                    .put("conditions", JSONArray(WorkflowJson.encodeConditions(automation.conditions)))
                    .put("actions", JSONArray(WorkflowJson.encodeActions(automation.actions)))
                    .apply {
                        automation.disableAfterFailures?.let { put("disableAfterFailures", it) }
                    }
            )
        }
        return JSONObject()
            .put(KEY_FORMAT, FORMAT)
            .put(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            .put(KEY_KIND, kind.name.lowercase())
            .put(KEY_EXPORTED_AT, exportedAt)
            .put(KEY_APP_VERSION, appVersion)
            .put(KEY_AUTOMATIONS, array)
            .toString(2)
    }

    /**
     * Decodes [raw] into definitions. Ids are NOT part of the format: the
     * importer mints fresh ones so an import can never overwrite an existing
     * automation.
     */
    fun decode(raw: String): TransferPayload {
        val json = try {
            JSONObject(raw)
        } catch (e: JSONException) {
            throw WorkflowFileException("This file is not an AutoFlow workflow file.", e)
        }

        val format = json.optString(KEY_FORMAT)
        if (format != FORMAT) {
            throw WorkflowFileException("This file is not an AutoFlow workflow file.")
        }

        val version = json.optInt(KEY_SCHEMA_VERSION, -1)
        if (version <= 0) {
            throw WorkflowFileException("This workflow file has no version and cannot be read.")
        }
        if (version > SCHEMA_VERSION) {
            throw WorkflowFileException(
                "This file was made with a newer version of AutoFlow (format $version). " +
                    "Update AutoFlow to import it."
            )
        }

        val kind = when (json.optString(KEY_KIND, TransferKind.WORKFLOWS.name.lowercase())) {
            "backup" -> TransferKind.BACKUP
            "workflows" -> TransferKind.WORKFLOWS
            else -> throw WorkflowFileException("This workflow file has an unknown kind.")
        }

        val array = json.optJSONArray(KEY_AUTOMATIONS)
            ?: throw WorkflowFileException("This workflow file contains no automations.")
        if (array.length() == 0) {
            throw WorkflowFileException("This workflow file contains no automations.")
        }

        val definitions = (0 until array.length()).map { index ->
            val item = try {
                array.getJSONObject(index)
            } catch (e: JSONException) {
                throw WorkflowFileException("Workflow ${index + 1} in this file is malformed.", e)
            }
            decodeDefinition(item, index)
        }

        return TransferPayload(
            kind = kind,
            exportedAt = json.optLong(KEY_EXPORTED_AT, 0L),
            appVersion = json.optString(KEY_APP_VERSION, ""),
            definitions = definitions
        )
    }

    private fun decodeDefinition(item: JSONObject, index: Int): WorkflowDefinition {
        val name = item.optString("name").ifBlank { "Imported workflow ${index + 1}" }
        val description = item.optString("description", "")
        try {
            val trigger = WorkflowJson.decodeTrigger(
                item.getJSONObject("trigger").toString()
            )
            val conditions = WorkflowJson.decodeConditions(
                item.optJSONArray("conditions")?.toString() ?: "[]"
            )
            val actions = WorkflowJson.decodeActions(
                item.optJSONArray("actions")?.toString() ?: "[]"
            )
            val disableAfterFailures =
                if (item.has("disableAfterFailures")) item.getInt("disableAfterFailures") else null

            return WorkflowDefinition(
                name = name,
                description = description,
                automation = Automation(
                    // Placeholder: the importer replaces this with a fresh id.
                    id = "",
                    name = name,
                    description = description,
                    // Imported workflows always arrive switched off; the user
                    // reviews requirements before enabling them.
                    enabled = false,
                    trigger = trigger,
                    conditions = conditions,
                    actions = actions,
                    createdAt = 0L,
                    updatedAt = 0L,
                    disableAfterFailures = disableAfterFailures
                )
            )
        } catch (e: WorkflowJsonException) {
            throw WorkflowFileException(
                "\"$name\" uses a trigger, condition or action this version of AutoFlow does not know.",
                e
            )
        } catch (e: JSONException) {
            throw WorkflowFileException("\"$name\" is missing required workflow data.", e)
        }
    }
}
