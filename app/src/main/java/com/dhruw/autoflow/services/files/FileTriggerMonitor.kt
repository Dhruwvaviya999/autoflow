package com.dhruw.autoflow.services.files

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Detection mechanism behind file triggers, kept behind an interface so it
 * can be replaced (content observers, faster polling, push-style APIs)
 * without touching the scheduler or the engine.
 */
interface FileTriggerMonitor {

    /** Make sure background scanning is running (idempotent). */
    fun ensureMonitoring()

    /** One prompt scan — e.g. when the app comes to the foreground. */
    fun requestImmediateScan()

    fun stopMonitoring()
}

/**
 * WorkManager-backed monitor. SAF tree URIs offer no reliable change
 * events, so detection is honest polling: one shared periodic scan (15 min,
 * WorkManager's minimum) plus an immediate scan whenever the app opens.
 * [FileScanWorker] cancels the periodic work itself once no enabled
 * file-trigger automations remain.
 */
class WorkManagerFileMonitor(context: Context) : FileTriggerMonitor {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun ensureMonitoring() {
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<FileScanWorker>(15, TimeUnit.MINUTES).build()
        )
    }

    override fun requestImmediateScan() {
        workManager.enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<FileScanWorker>().build()
        )
    }

    override fun stopMonitoring() {
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    companion object {
        const val PERIODIC_WORK_NAME = "file-trigger-scan"
        const val IMMEDIATE_WORK_NAME = "file-trigger-scan-now"
    }
}
