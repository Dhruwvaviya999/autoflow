package com.dhruw.autoflow.automation.model

/**
 * A single step an automation performs. Execution is done by an
 * [com.dhruw.autoflow.automation.engine.ActionHandler] so the model stays
 * free of platform code.
 *
 * File actions operate on the run's [TriggerPayload.FileEvent]; destination
 * folders are opaque storage URIs picked by the user. Deletion is deferred —
 * no destructive file action ships until it has a safe confirmation flow.
 */
sealed interface Action {

    data class ShowNotificationAction(
        val title: String,
        val message: String
    ) : Action

    data class DelayAction(
        val durationMillis: Long
    ) : Action

    data class LogAction(
        val message: String
    ) : Action

    /** Copy the triggering file into another user-selected folder. */
    data class CopyFileAction(
        val destinationFolderUri: String,
        val destinationLabel: String
    ) : Action

    /** Move the triggering file into another user-selected folder. */
    data class MoveFileAction(
        val destinationFolderUri: String,
        val destinationLabel: String
    ) : Action

    /** Rename the triggering file in place. */
    data class RenameFileAction(
        val newName: String
    ) : Action

    /**
     * Run the Instagram follower analysis on the triggering file (an
     * Instagram data export). Results land in the Instagram Analyzer screen
     * and the execution log. No credentials, no network.
     */
    data object InstagramAnalysisAction : Action

    /**
     * Store the triggering notification in the local database. Opt-in only:
     * notifications are never persisted unless the user adds this action to
     * an automation. Storage is capped (newest 500 records) so it cannot
     * grow without bound.
     */
    data object SaveNotificationAction : Action

    /**
     * Run a UI automation workflow on [targetPackage] using Android
     * Accessibility. [steps] is an ordered list of [UiStep]s executed by the
     * UiAutomationExecutor once the accessibility service is enabled.
     * [overallTimeoutMillis] bounds the whole run. Only one UI automation may
     * run at a time. All processing is on-device; no network.
     */
    data class UiAutomationAction(
        val targetPackage: String,
        val targetLabel: String = "",
        val steps: List<UiStep> = emptyList(),
        val overallTimeoutMillis: Long = 60_000
    ) : Action
}

val Action.displayName: String
    get() = when (this) {
        is Action.ShowNotificationAction -> "Show notification"
        is Action.DelayAction -> "Delay"
        is Action.LogAction -> "Log"
        is Action.CopyFileAction -> "Copy file"
        is Action.MoveFileAction -> "Move file"
        is Action.RenameFileAction -> "Rename file"
        is Action.InstagramAnalysisAction -> "Instagram analysis"
        is Action.SaveNotificationAction -> "Save notification"
        is Action.UiAutomationAction -> "UI automation"
    }

val Action.summary: String
    get() = when (this) {
        is Action.ShowNotificationAction -> title.ifBlank { "Untitled notification" }
        is Action.DelayAction -> {
            val seconds = durationMillis / 1000.0
            if (seconds == seconds.toLong().toDouble()) "${seconds.toLong()} s" else "$seconds s"
        }
        is Action.LogAction -> message.ifBlank { "Empty message" }
        is Action.CopyFileAction -> "Copy to ${destinationLabel.ifBlank { "selected folder" }}"
        is Action.MoveFileAction -> "Move to ${destinationLabel.ifBlank { "selected folder" }}"
        is Action.RenameFileAction -> "Rename to \"${newName.trim()}\""
        is Action.InstagramAnalysisAction -> "Find who doesn't follow back"
        is Action.SaveNotificationAction -> "Keep the notification on this device"
        is Action.UiAutomationAction -> buildString {
            append(steps.size).append(if (steps.size == 1) " step" else " steps")
            append(" on ").append(targetLabel.ifBlank { targetPackage.ifBlank { "an app" } })
        }
    }
