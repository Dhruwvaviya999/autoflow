package com.dhruw.autoflow.services.background

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.dhruw.autoflow.automation.engine.AutomationScheduler
import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.automation.model.Trigger
import com.dhruw.autoflow.automation.model.summary
import java.util.concurrent.TimeUnit

/**
 * WorkManager-backed [AutomationScheduler]. Each automation maps to one
 * deterministic unique work name ("automation-<id>"), so re-saving never
 * stacks duplicate workers. WorkManager persists enqueued work in its own
 * database, which survives app restarts and device reboots — no custom boot
 * receiver is needed.
 *
 * Timing is inexact by design: WorkManager batches deferrable work, so a run
 * lands at or shortly after the requested time, not to the minute.
 */
class WorkManagerAutomationScheduler(
    context: Context,
    private val fileMonitor: com.dhruw.autoflow.services.files.FileTriggerMonitor,
    private val clock: () -> Long = System::currentTimeMillis
) : AutomationScheduler {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun schedule(automation: Automation) {
        val trigger = automation.trigger
        val name = uniqueWorkName(automation.id)
        if (trigger is Trigger.FileTrigger) {
            // File triggers share one scan worker; per-automation time work
            // (from a previous trigger type) must still be dropped. The scan
            // worker cancels itself when no file automations remain enabled.
            workManager.cancelUniqueWork(name)
            if (automation.enabled) fileMonitor.ensureMonitoring()
            return
        }
        // An edit can switch a trigger from time to manual, or disable the
        // automation; both must drop any previously enqueued work.
        if (trigger !is Trigger.TimeTrigger || !automation.enabled) {
            workManager.cancelUniqueWork(name)
            return
        }

        val delayMillis = NextRunCalculator.delayUntilNextOccurrenceMillis(
            hour = trigger.hour,
            minute = trigger.minute,
            nowMillis = clock()
        )
        val input = workDataOf(AutomationWorker.KEY_AUTOMATION_ID to automation.id)

        // Unique names are shared across one-time and periodic work, and the
        // REPLACE policies below cancel any holder of the name atomically. A
        // separate cancel-then-enqueue would open a window where a stale
        // JobScheduler job survives and double-fires the automation.
        when (trigger.repeat) {
            Trigger.TimeTrigger.Repeat.ONCE -> workManager.enqueueUniqueWork(
                name,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<AutomationWorker>()
                    .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                    .setInputData(input)
                    .build()
            )
            Trigger.TimeTrigger.Repeat.DAILY -> workManager.enqueueUniquePeriodicWork(
                name,
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                PeriodicWorkRequestBuilder<AutomationWorker>(24, TimeUnit.HOURS)
                    .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                    .setInputData(input)
                    .build()
            )
        }
        Log.i(TAG, "Scheduled '${automation.name}' (${trigger.summary}) in ${delayMillis / 1000}s")
    }

    override fun cancel(automationId: String) {
        workManager.cancelUniqueWork(uniqueWorkName(automationId))
    }

    companion object {
        private const val TAG = "AutoFlowScheduler"

        fun uniqueWorkName(automationId: String): String = "automation-$automationId"
    }
}
