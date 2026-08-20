package com.dhruw.autoflow.automation.model

/**
 * An Android capability a workflow needs before it can run. Used to tell the
 * user what an automation (or an imported workflow) requires — AutoFlow never
 * grants anything itself, it only explains and links to the system screen.
 */
enum class Capability {
    /** NotificationListenerService access — notification triggers/conditions. */
    NOTIFICATION_ACCESS,

    /** AccessibilityService — UI automation actions. */
    ACCESSIBILITY,

    /** A folder the user picked through the storage picker — file triggers/actions. */
    FILE_ACCESS,

    /** Permission to post notifications — notification actions and confirmations. */
    POST_NOTIFICATIONS
}

val Capability.label: String
    get() = when (this) {
        Capability.NOTIFICATION_ACCESS -> "Notification access"
        Capability.ACCESSIBILITY -> "Accessibility"
        Capability.FILE_ACCESS -> "Folder access"
        Capability.POST_NOTIFICATIONS -> "Notifications"
    }

/** Everything this automation will need, derived from its trigger and steps. */
val Automation.requiredCapabilities: Set<Capability>
    get() {
        val required = linkedSetOf<Capability>()
        when (trigger) {
            is Trigger.NotificationTrigger -> required += Capability.NOTIFICATION_ACCESS
            is Trigger.FileTrigger -> required += Capability.FILE_ACCESS
            else -> Unit
        }
        if (conditions.any { it.needsNotificationAccess }) {
            required += Capability.NOTIFICATION_ACCESS
        }
        actions.forEach { collectActionCapabilities(it, required) }
        return required
    }

private fun collectActionCapabilities(action: Action, into: MutableSet<Capability>) {
    when (val a = action.unwrapped) {
        is Action.ShowNotificationAction -> into += Capability.POST_NOTIFICATIONS
        is Action.SaveNotificationAction -> into += Capability.NOTIFICATION_ACCESS
        is Action.CopyFileAction, is Action.MoveFileAction,
        is Action.RenameFileAction, is Action.InstagramAnalysisAction ->
            into += Capability.FILE_ACCESS
        is Action.UiAutomationAction -> {
            into += Capability.ACCESSIBILITY
            // Confirmation pauses are answered from a notification when
            // AutoFlow is in the background.
            if (a.steps.any { it is UiStep.RequireUserConfirmation }) {
                into += Capability.POST_NOTIFICATIONS
            }
        }
        is Action.BranchAction -> {
            a.thenActions.forEach { collectActionCapabilities(it, into) }
            a.elseActions.forEach { collectActionCapabilities(it, into) }
        }
        else -> Unit
    }
}

private val Condition.needsNotificationAccess: Boolean
    get() = when (this) {
        is Condition.NotificationAppCondition,
        is Condition.NotificationTitleCondition,
        is Condition.NotificationTextCondition,
        is Condition.NotificationCategoryCondition -> true
        is Condition.AndCondition -> conditions.any { it.needsNotificationAccess }
        is Condition.OrCondition -> conditions.any { it.needsNotificationAccess }
        is Condition.NotCondition -> condition.needsNotificationAccess
        else -> false
    }
