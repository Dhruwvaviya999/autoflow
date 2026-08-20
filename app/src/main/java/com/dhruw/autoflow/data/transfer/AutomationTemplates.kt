package com.dhruw.autoflow.data.transfer

import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.automation.model.Capability
import com.dhruw.autoflow.automation.model.Condition
import com.dhruw.autoflow.automation.model.LevelComparison
import com.dhruw.autoflow.automation.model.Trigger
import com.dhruw.autoflow.automation.model.requiredCapabilities

/**
 * A ready-made workflow the user can start from. Templates are ordinary
 * automation definitions — the same shape an export file carries — so
 * instantiating one is just a copy with a fresh id.
 *
 * Templates that need a folder, an app or a selector deliberately leave
 * those blank: the editor opens with the structure filled in and the user
 * supplies the specifics. Nothing is bundled that would run against another
 * app without the user configuring it first.
 */
data class AutomationTemplate(
    val id: String,
    val emoji: String,
    val name: String,
    val description: String,
    val build: () -> Automation
) {
    val requiredCapabilities: Set<Capability> get() = build().requiredCapabilities
}

object AutomationTemplates {

    /** Blank scaffold used by every template body. */
    private fun template(
        name: String,
        description: String,
        trigger: Trigger,
        conditions: List<Condition> = emptyList(),
        actions: List<Action>
    ): Automation = Automation(
        id = "",
        name = name,
        description = description,
        enabled = false,
        trigger = trigger,
        conditions = conditions,
        actions = actions,
        createdAt = 0L,
        updatedAt = 0L
    )

    val all: List<AutomationTemplate> = listOf(
        AutomationTemplate(
            id = "low_battery_alert",
            emoji = "🔋",
            name = "Low battery alert",
            description = "Notifies you when the battery drops to 20%.",
            build = {
                template(
                    name = "Low battery alert",
                    description = "Warns when the battery falls to 20%.",
                    trigger = Trigger.BatteryLevelTrigger(LevelComparison.LESS_OR_EQUAL, 20),
                    actions = listOf(
                        Action.ShowNotificationAction(
                            title = "Battery low",
                            message = "Battery is at {{battery.level}}%. Time to charge."
                        )
                    )
                )
            }
        ),
        AutomationTemplate(
            id = "downloads_organizer",
            emoji = "📂",
            name = "Downloads organizer",
            description = "Copies new PDFs from a folder you choose into another folder.",
            build = {
                template(
                    name = "Downloads organizer",
                    description = "Copies new PDF files into a folder you choose.",
                    trigger = Trigger.FileTrigger(
                        folderUri = "",
                        folderLabel = "",
                        extension = "pdf"
                    ),
                    actions = listOf(
                        Action.GroupMarker("Sort the file"),
                        Action.CopyFileAction(destinationFolderUri = "", destinationLabel = ""),
                        Action.ShowNotificationAction(
                            title = "File organized",
                            message = "Copied {{file.name}}."
                        )
                    )
                )
            }
        ),
        AutomationTemplate(
            id = "important_notification",
            emoji = "🔔",
            name = "Important notification",
            description = "Watches notifications for a keyword and reacts differently when it matches.",
            build = {
                template(
                    name = "Important notification",
                    description = "Flags notifications that mention a keyword.",
                    trigger = Trigger.NotificationTrigger(textPattern = "urgent"),
                    actions = listOf(
                        Action.SetVariableAction(
                            name = "source",
                            value = "{{notification.appName}}"
                        ),
                        Action.BranchAction(
                            condition = Condition.NotificationTextCondition("urgent"),
                            thenActions = listOf(
                                Action.ShowNotificationAction(
                                    title = "Urgent from {{source}}",
                                    message = "{{notification.title}}"
                                )
                            ),
                            elseActions = listOf(
                                Action.LogAction("Notification from {{source}} did not match.")
                            )
                        )
                    )
                )
            }
        ),
        AutomationTemplate(
            id = "app_ui_workflow",
            emoji = "📱",
            name = "App UI workflow",
            description = "Skeleton for a UI automation: launch an app, wait, tap, confirm.",
            build = {
                template(
                    name = "App UI workflow",
                    description = "Starting point for automating another app's screens.",
                    trigger = Trigger.ManualTrigger,
                    actions = listOf(
                        Action.UiAutomationAction(
                            targetPackage = "",
                            targetLabel = "",
                            steps = emptyList()
                        )
                    )
                )
            }
        ),
        AutomationTemplate(
            id = "instagram_analyzer",
            emoji = "📊",
            name = "Instagram analyzer",
            description = "Analyzes an Instagram export dropped into a folder you choose.",
            build = {
                template(
                    name = "Instagram analyzer",
                    description = "Runs the follower analysis on a new export file.",
                    trigger = Trigger.FileTrigger(
                        folderUri = "",
                        folderLabel = "",
                        extension = "zip"
                    ),
                    actions = listOf(
                        Action.InstagramAnalysisAction,
                        Action.ShowNotificationAction(
                            title = "Analysis ready",
                            message = "{{result.count}} accounts don't follow you back."
                        )
                    )
                )
            }
        )
    )

    fun byId(id: String): AutomationTemplate? = all.firstOrNull { it.id == id }
}
