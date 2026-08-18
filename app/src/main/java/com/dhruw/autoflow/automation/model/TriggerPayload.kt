package com.dhruw.autoflow.automation.model

/**
 * Data the trigger hands to a run. Conditions and actions read it through
 * the engine; triggers without extra data (manual, time) pass null.
 */
sealed interface TriggerPayload {

    /**
     * A file detected by a FileTrigger. [uri] is an opaque storage URI
     * (Android content URI) — never assumed to be a filesystem path.
     */
    data class FileEvent(
        val uri: String,
        val name: String,
        val extension: String,
        val sizeBytes: Long,
        val detectedAt: Long
    ) : TriggerPayload
}
