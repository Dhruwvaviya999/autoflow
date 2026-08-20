package com.dhruw.autoflow.data.transfer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.dhruw.autoflow.automation.engine.WorkflowValidator
import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.automation.model.Capability
import com.dhruw.autoflow.automation.model.requiredCapabilities
import com.dhruw.autoflow.data.repository.AutomationRepository
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One importable workflow plus what the validator and capability scan found. */
data class ImportCandidate(
    val definition: WorkflowDefinition,
    val capabilities: Set<Capability>,
    val errors: List<String>,
    val warnings: List<String>
) {
    val isImportable: Boolean get() = errors.isEmpty()
    val name: String get() = definition.name
}

/** Everything a file offered, ready for the review sheet. */
data class ImportPreview(
    val kind: TransferKind,
    val candidates: List<ImportCandidate>
) {
    val importableCount: Int get() = candidates.count { it.isImportable }
    val capabilities: Set<Capability>
        get() = candidates.filter { it.isImportable }.flatMap { it.capabilities }.toSet()
}

/**
 * Reads and writes workflow files through the storage picker, and turns an
 * imported file into new automations.
 *
 * Import rules, in order: parse (rejects malformed/newer files) → validate
 * every workflow → mint a fresh id → insert **disabled**. Nothing is ever
 * overwritten, and no permission is granted: the user reviews what the
 * workflow needs and enables it themselves.
 */
class WorkflowTransferService(
    private val context: Context,
    private val repository: AutomationRepository,
    private val validator: WorkflowValidator = WorkflowValidator(),
    private val appVersion: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {

    /** Serializes [automations] into the file at [uri] (created by the picker). */
    suspend fun export(
        uri: Uri,
        automations: List<Automation>,
        kind: TransferKind = TransferKind.WORKFLOWS
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val text = WorkflowFileCodec.encode(
                automations = automations,
                kind = kind,
                exportedAt = clock(),
                appVersion = appVersion
            )
            context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.write(text.toByteArray())
            } ?: throw IOException("Could not open the chosen file for writing")
            automations.size
        }.recoverCatching { e ->
            throw WorkflowFileException(
                when (e) {
                    is SecurityException -> "AutoFlow is not allowed to write to that location."
                    else -> e.message ?: "Could not save the workflow file."
                },
                e
            )
        }
    }

    /**
     * Writes [automations] to a private cache file and returns a share intent
     * for it. The grant is read-only and temporary, and the file lives in the
     * one directory the FileProvider exposes — no other app data is reachable
     * through it.
     */
    suspend fun shareIntent(automations: List<Automation>): Result<Intent> =
        withContext(Dispatchers.IO) {
            runCatching {
                val text = WorkflowFileCodec.encode(
                    automations = automations,
                    exportedAt = clock(),
                    appVersion = appVersion
                )
                val dir = File(context.cacheDir, SHARE_DIR).apply {
                    // Previous shares are not kept around.
                    if (exists()) listFiles()?.forEach { it.delete() } else mkdirs()
                }
                val name = automations.singleOrNull()?.name?.toFileName() ?: "autoflow-workflows"
                val file = File(dir, "$name.${WorkflowFileCodec.FILE_EXTENSION}")
                file.writeText(text)

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                Intent(Intent.ACTION_SEND).apply {
                    type = WorkflowFileCodec.MIME_TYPE
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(
                        Intent.EXTRA_TITLE,
                        automations.singleOrNull()?.name ?: "AutoFlow workflows"
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }.recoverCatching { e ->
                throw WorkflowFileException(
                    e.message ?: "Could not prepare the workflow for sharing.",
                    e
                )
            }
        }

    /** Reads and checks a file without importing anything yet. */
    suspend fun preview(uri: Uri): Result<ImportPreview> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                if (bytes.size > MAX_FILE_BYTES) {
                    throw WorkflowFileException("That file is too large to be an AutoFlow workflow file.")
                }
                bytes.toString(Charsets.UTF_8)
            } ?: throw WorkflowFileException("Could not open the chosen file.")

            val payload = WorkflowFileCodec.decode(raw)
            ImportPreview(
                kind = payload.kind,
                candidates = payload.definitions.map { definition ->
                    val report = validator.validate(definition.automation)
                    ImportCandidate(
                        definition = definition,
                        capabilities = definition.automation.requiredCapabilities,
                        errors = report.errors.map { it.message },
                        warnings = report.warnings.map { it.message }
                    )
                }
            )
        }
    }

    /**
     * Inserts the importable candidates as new, disabled automations.
     * Returns how many were stored.
     */
    suspend fun import(candidates: List<ImportCandidate>): Int {
        val now = clock()
        var stored = 0
        candidates.filter { it.isImportable }.forEach { candidate ->
            repository.upsert(
                candidate.definition.automation.copy(
                    id = newId(),
                    enabled = false,
                    createdAt = now,
                    updatedAt = now,
                    lastRunAt = null
                )
            )
            stored++
        }
        return stored
    }

    private fun String.toFileName(): String =
        trim().replace(Regex("""[^A-Za-z0-9 _-]"""), "").replace(' ', '-').ifBlank { "workflow" }

    private companion object {
        /** Workflow files are tiny; anything larger is not one of ours. */
        const val MAX_FILE_BYTES = 2 * 1024 * 1024

        /** Matches the cache-path declared in res/xml/file_paths.xml. */
        const val SHARE_DIR = "shared"
    }
}
