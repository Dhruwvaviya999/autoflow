package com.dhruw.autoflow.services.background

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dhruw.autoflow.AutoFlowApplication

/**
 * Thin adapter between WorkManager and the automation engine: load from Room,
 * run through AutomationRunner (which handles the disabled → SKIPPED case and
 * persists every execution update), done. No engine logic lives here.
 */
class AutomationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val automationId = inputData.getString(KEY_AUTOMATION_ID)
        if (automationId.isNullOrBlank()) {
            Log.w(TAG, "Scheduled work without an automation id — dropping")
            return Result.success()
        }
        val container = (applicationContext as AutoFlowApplication).container
        val automation = container.automationRepository.getById(automationId)
        if (automation == null) {
            // Deleted (or its stored JSON is unreadable): skip quietly. No
            // execution is recorded because there is no name to show for it.
            Log.i(TAG, "Automation $automationId no longer exists — skipping")
            return Result.success()
        }
        // WorkManager schedules delayed work through both an in-process timer
        // and JobScheduler; on some devices both can fire seconds apart. A
        // trigger can never legitimately run twice within this window, so the
        // second fire is dropped instead of re-running the actions.
        val lastRun = automation.lastRunAt
        if (lastRun != null && System.currentTimeMillis() - lastRun < DUPLICATE_RUN_WINDOW_MS) {
            Log.i(TAG, "Skipping duplicate scheduled run of '${automation.name}'")
            return Result.success()
        }
        // A failed automation is recorded as FAILED in history; retrying would
        // re-run its actions (e.g. duplicate notifications), so never retry.
        val result = container.runner.run(automation)
        Log.i(TAG, "Scheduled run of '${automation.name}' finished: ${result.status}")
        return Result.success()
    }

    companion object {
        const val KEY_AUTOMATION_ID = "automation_id"
        private const val TAG = "AutoFlowWorker"
        private const val DUPLICATE_RUN_WINDOW_MS = 60_000L
    }
}
