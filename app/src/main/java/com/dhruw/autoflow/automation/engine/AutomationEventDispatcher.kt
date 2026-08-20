package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.automation.model.SystemEvent
import com.dhruw.autoflow.automation.model.Trigger
import com.dhruw.autoflow.automation.model.TriggerPayload
import com.dhruw.autoflow.data.repository.AutomationRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Routes push-style trigger events to the automations they concern:
 * find enabled automations whose trigger matches → run each through the
 * existing [AutomationRunner] (conditions, actions, and history persistence
 * all stay in the engine — nothing is duplicated here). Pure Kotlin, fully
 * testable without Android.
 *
 * Notification events are deduplicated here so an Android notification
 * update cannot re-run automations (see [NotificationDeduplicator]).
 * File events are not dispatched through this class yet: file detection is
 * pull-based ([com.dhruw.autoflow.services.files.FileScanWorker]) and its
 * dedup is tied to per-automation scan state.
 */
class AutomationEventDispatcher(
    private val automationRepository: AutomationRepository,
    private val runner: AutomationRunner,
    private val deduplicator: NotificationDeduplicator = NotificationDeduplicator()
) {

    suspend fun dispatch(event: TriggerPayload) {
        when (event) {
            is TriggerPayload.NotificationEvent -> dispatchNotification(event)
            is TriggerPayload.FileEvent -> Unit // handled by FileScanWorker (see class doc)
            is SystemEvent -> dispatchSystem(event)
        }
    }

    /**
     * System events arrive pre-deduplicated from [SystemStateTracker]
     * (edge-trigger semantics), so this only routes: cheap trigger-type +
     * config filter first, engine (conditions/actions) only for matches.
     */
    private suspend fun dispatchSystem(event: SystemEvent) {
        val matching = enabledAutomations().filter { it.trigger.matchesSystem(event) }
        for (automation in matching) {
            runIsolated(automation, event)
        }
    }

    /**
     * One automation cancelling (a user pressing Cancel on a UI automation
     * surfaces as CancellationException) must not abort the other
     * automations matched by the same event. Real coroutine cancellation
     * still propagates.
     */
    private suspend fun runIsolated(automation: Automation, event: TriggerPayload) {
        try {
            runner.run(automation, event)
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
        }
    }

    private fun Trigger.matchesSystem(event: SystemEvent): Boolean = when (this) {
        is Trigger.BatteryLevelTrigger -> event is SystemEvent.BatteryChanged && matches(event)
        is Trigger.ChargingStateTrigger -> event is SystemEvent.ChargingChanged && matches(event)
        is Trigger.WiFiConnectionTrigger -> event is SystemEvent.WiFiChanged && matches(event)
        is Trigger.NetworkAvailabilityTrigger -> event is SystemEvent.NetworkChanged && matches(event)
        is Trigger.BluetoothConnectionTrigger -> event is SystemEvent.BluetoothChanged && matches(event)
        is Trigger.ScreenStateTrigger -> event is SystemEvent.ScreenChanged && matches(event)
        is Trigger.HeadsetConnectionTrigger -> event is SystemEvent.HeadsetChanged && matches(event)
        is Trigger.DeviceBootTrigger -> event is SystemEvent.DeviceBooted
        else -> false
    }

    private suspend fun dispatchNotification(event: TriggerPayload.NotificationEvent) {
        // Group summaries mostly repeat content already delivered by their
        // child notifications; acting on them would double-fire automations.
        if (event.isGroupSummary) return
        // Ongoing notifications (timers, downloads, media) re-post with new
        // content continuously — a countdown updates every second. They are
        // status displays, not events; device-verified that acting on them
        // floods executions faster than any content-based dedup can stop.
        if (event.isOngoing) return
        if (!deduplicator.shouldProcess(event)) return

        val matching = enabledAutomations().filter { automation ->
            (automation.trigger as? Trigger.NotificationTrigger)?.matches(event) == true
        }
        for (automation in matching) {
            runIsolated(automation, event)
        }
    }

    /**
     * The repository's StateFlow cache avoids a database query per
     * notification once it is warm; the direct fetch covers a cold process
     * where the cache has not populated yet.
     */
    private suspend fun enabledAutomations() =
        automationRepository.automations.value
            .ifEmpty { automationRepository.getAll() }
            .filter { it.enabled }
}
