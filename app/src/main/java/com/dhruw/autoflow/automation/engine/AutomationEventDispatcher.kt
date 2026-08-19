package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.Trigger
import com.dhruw.autoflow.automation.model.TriggerPayload
import com.dhruw.autoflow.data.repository.AutomationRepository

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
        }
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
            runner.run(automation, event)
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
