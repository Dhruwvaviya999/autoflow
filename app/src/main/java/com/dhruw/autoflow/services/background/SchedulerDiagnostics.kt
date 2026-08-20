package com.dhruw.autoflow.services.background

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.automation.model.Trigger
import com.dhruw.autoflow.automation.model.summary
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** One scheduled automation and what WorkManager currently knows about it. */
data class ScheduledWorkInfo(
    val automationId: String,
    val name: String,
    val schedule: String,
    /** Honest window, not a promise: Android may delay background work. */
    val nextExpectedWindow: String,
    val state: String,
    val healthy: Boolean
)

/**
 * Reads WorkManager's own view of AutoFlow's scheduled work so the user can
 * see whether a time-triggered automation is actually queued.
 *
 * The wording is deliberately careful: WorkManager guarantees "no earlier
 * than", never an exact minute, and Doze can defer work further. Nothing here
 * claims a precise run time.
 */
class SchedulerDiagnostics(context: Context) {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    suspend fun inspect(automations: List<Automation>): List<ScheduledWorkInfo> =
        withContext(Dispatchers.IO) {
            automations
                .filter { it.trigger is Trigger.TimeTrigger }
                .map { automation ->
                    val trigger = automation.trigger as Trigger.TimeTrigger
                    val infos = runCatching {
                        workManager
                            .getWorkInfosForUniqueWorkFlow(workName(automation.id))
                            .first()
                    }.getOrDefault(emptyList())
                    val info = infos.firstOrNull()

                    ScheduledWorkInfo(
                        automationId = automation.id,
                        name = automation.name,
                        schedule = trigger.summary,
                        nextExpectedWindow = when {
                            !automation.enabled -> "Not scheduled — automation is off"
                            info == null -> "Not queued yet"
                            info.state.isFinished -> "Finished — will re-queue on the next change"
                            else -> nextWindow(trigger)
                        },
                        state = info?.state?.label() ?: "Not queued",
                        healthy = automation.enabled && info != null && !info.state.isFinished
                    )
                }
        }

    /** Same unique work name the scheduler enqueues under. */
    private fun workName(automationId: String): String =
        WorkManagerAutomationScheduler.uniqueWorkName(automationId)

    private fun nextWindow(trigger: Trigger.TimeTrigger): String {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, trigger.hour)
            set(Calendar.MINUTE, trigger.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        val sameDay = next.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        val time = "%02d:%02d".format(trigger.hour, trigger.minute)
        return if (sameDay) "Today around $time" else "Tomorrow around $time"
    }
}

private fun WorkInfo.State.label(): String = when (this) {
    WorkInfo.State.ENQUEUED -> "Waiting"
    WorkInfo.State.RUNNING -> "Running"
    WorkInfo.State.SUCCEEDED -> "Completed"
    WorkInfo.State.FAILED -> "Failed"
    WorkInfo.State.BLOCKED -> "Blocked"
    WorkInfo.State.CANCELLED -> "Cancelled"
}
