package com.dhruw.autoflow.services.files

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Bookkeeping for the file scanner, kept out of the Room schema on purpose
 * (it is scanner state, not domain data). Per automation it stores a
 * baseline timestamp plus the keys of already-processed files so a file is
 * never triggered twice and pre-existing files never fire a new automation.
 */
class FileScanStateStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("file_scan_state", Context.MODE_PRIVATE)

    data class ScanState(
        val baselineMillis: Long,
        val processedKeys: Set<String>
    )

    fun stateFor(automationId: String): ScanState? {
        val raw = prefs.getString(automationId, null) ?: return null
        return try {
            val json = JSONObject(raw)
            val keys = json.optJSONArray("processed") ?: JSONArray()
            ScanState(
                baselineMillis = json.getLong("baseline"),
                processedKeys = (0 until keys.length()).mapTo(HashSet()) { keys.getString(it) }
            )
        } catch (e: JSONException) {
            null
        }
    }

    fun save(automationId: String, state: ScanState) {
        // Cap the processed set: the baseline already excludes old files, so
        // only recent keys matter for de-duplication.
        val keys = state.processedKeys.toList().takeLast(MAX_KEYS)
        val json = JSONObject()
            .put("baseline", state.baselineMillis)
            .put("processed", JSONArray(keys))
        prefs.edit().putString(automationId, json.toString()).apply()
    }

    fun clear(automationId: String) {
        prefs.edit().remove(automationId).apply()
    }

    /**
     * Dedup key deliberately excludes the URI: Android's Downloads provider
     * re-issues document ids once MediaStore indexes a file (raw:… → msf:…)
     * and rounds lastModified to seconds, which would make a URI-based key
     * fire twice for the same file. Name + size + second-precision mtime is
     * stable across that churn.
     */
    fun key(file: FileInfo): String =
        "${file.name}|${file.sizeBytes}|${file.lastModified / 1000}"

    private companion object {
        const val MAX_KEYS = 200
    }
}
