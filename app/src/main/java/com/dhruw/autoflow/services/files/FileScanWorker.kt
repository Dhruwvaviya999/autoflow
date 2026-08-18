package com.dhruw.autoflow.services.files

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dhruw.autoflow.AutoFlowApplication
import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.automation.model.Trigger
import com.dhruw.autoflow.automation.model.TriggerPayload
import kotlinx.coroutines.sync.withLock

/**
 * Scans the folders of all enabled file-trigger automations and runs each
 * automation once per newly appeared matching file. New = modified after
 * the automation's baseline and not processed before; the first scan only
 * records a baseline so pre-existing files never fire.
 */
class FileScanWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = scanMutex.withLock {
        // The periodic scan and the on-app-open scan are separate unique
        // works and can be started concurrently by WorkManager; the mutex
        // serializes them so both see each other's processed-file state.
        val container = (applicationContext as AutoFlowApplication).container
        val automations = container.automationRepository.getAll()
            .filter { it.enabled && it.trigger is Trigger.FileTrigger }

        if (automations.isEmpty()) {
            container.fileMonitor.stopMonitoring()
            return@withLock Result.success()
        }

        for (automation in automations) {
            try {
                scan(automation, container)
            } catch (e: FileAccessException) {
                // Folder gone or permission revoked: skip quietly this round.
                Log.w(TAG, "Scan failed for '${automation.name}': ${e.message}")
            }
        }
        Result.success()
    }

    private suspend fun scan(
        automation: Automation,
        container: com.dhruw.autoflow.di.AppContainer
    ) {
        val trigger = automation.trigger as Trigger.FileTrigger
        val store = container.fileScanStateStore
        val now = System.currentTimeMillis()

        val previous = store.stateFor(automation.id)
        if (previous == null) {
            // First scan: baseline only, so files that already exist don't fire.
            store.save(automation.id, FileScanStateStore.ScanState(now, emptySet()))
            return
        }

        val files = container.fileAccess.listFolder(trigger.folderUri)
        val processed = previous.processedKeys.toMutableSet()

        for (file in files) {
            if (file.lastModified <= previous.baselineMillis) continue
            val key = store.key(file)
            if (key in processed) continue
            if (!matches(trigger, file)) {
                processed += key
                continue
            }

            Log.i(TAG, "File '${file.name}' triggers '${automation.name}'")
            val payload = TriggerPayload.FileEvent(
                uri = file.uri,
                name = file.name,
                extension = file.extension,
                sizeBytes = file.sizeBytes,
                detectedAt = now
            )
            container.runner.run(automation, payload)
            processed += key
        }

        // The baseline stays put: lastModified-based advancement could skip
        // files written between listing and saving. processedKeys carries
        // the dedup burden; it is capped, which is fine because everything
        // older than the previous scan round has long been processed.
        store.save(
            automation.id,
            FileScanStateStore.ScanState(previous.baselineMillis, processed)
        )
    }

    private fun matches(trigger: Trigger.FileTrigger, file: FileInfo): Boolean {
        val ext = trigger.normalizedExtension
        if (ext.isNotEmpty() && file.extension != ext) return false
        val pattern = trigger.namePattern.trim()
        if (pattern.isNotEmpty() && !file.name.contains(pattern, ignoreCase = true)) return false
        return true
    }

    private companion object {
        const val TAG = "AutoFlowFileScan"
        val scanMutex = kotlinx.coroutines.sync.Mutex()
    }
}
