package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.automation.model.Capability
import com.dhruw.autoflow.automation.model.Condition
import com.dhruw.autoflow.automation.model.Trigger
import com.dhruw.autoflow.automation.model.displayName
import com.dhruw.autoflow.automation.model.requiredCapabilities
import com.dhruw.autoflow.automation.model.unwrapped

/** Severity of a validation finding. */
enum class IssueSeverity { ERROR, WARNING, INFO }

/**
 * One validation finding. [ERROR] blocks saving/enabling/importing;
 * [WARNING] is advisory; [INFO] states a requirement (e.g. a permission the
 * workflow will need).
 */
data class ValidationIssue(
    val severity: IssueSeverity,
    val message: String
)

/** Result of validating one automation. */
data class ValidationReport(val issues: List<ValidationIssue>) {
    val errors: List<ValidationIssue> get() = issues.filter { it.severity == IssueSeverity.ERROR }
    val warnings: List<ValidationIssue> get() = issues.filter { it.severity == IssueSeverity.WARNING }
    val infos: List<ValidationIssue> get() = issues.filter { it.severity == IssueSeverity.INFO }
    val isValid: Boolean get() = errors.isEmpty()
}

/**
 * Central workflow validation: the one place that decides whether a workflow
 * is well-formed enough to save, enable, import or test. Pure Kotlin — it
 * never touches Android or storage, so the editor, the importer and the
 * tester all get identical answers.
 *
 * It checks structure and configuration only. Whether a permission is
 * actually granted is a device question answered by the caller (the tester
 * and the automation health calculator); this validator reports which
 * capabilities the workflow will need as INFO.
 */
class WorkflowValidator {

    fun validate(automation: Automation): ValidationReport {
        val issues = mutableListOf<ValidationIssue>()

        if (automation.name.isBlank()) {
            issues += ValidationIssue(IssueSeverity.ERROR, "Give the automation a name.")
        }

        validateTrigger(automation.trigger, issues)

        automation.conditions.forEach { validateCondition(it, depth = 0, issues = issues) }

        if (automation.actions.isEmpty()) {
            issues += ValidationIssue(IssueSeverity.ERROR, "Add at least one action.")
        }
        if (automation.actions.size > EngineLimits.MAX_ACTIONS) {
            issues += ValidationIssue(
                IssueSeverity.ERROR,
                "This workflow has ${automation.actions.size} steps — the limit is ${EngineLimits.MAX_ACTIONS}."
            )
        }
        if (automation.actions.all { it.unwrapped is Action.GroupMarker }) {
            issues += ValidationIssue(
                IssueSeverity.ERROR,
                "This workflow only has group labels — add a step that does something."
            )
        }
        if (automation.actions.isNotEmpty() && automation.actions.all { !it.isRunnable }) {
            issues += ValidationIssue(
                IssueSeverity.WARNING,
                "Every step is disabled, so this automation will do nothing."
            )
        }

        // Variables: locals must be defined before they are used. Payload
        // variables and result.* keys are checked structurally by the resolver.
        val defined = mutableSetOf<String>()
        automation.actions.forEach { action ->
            validateAction(action, depth = 0, defined = defined, issues = issues)
        }

        automation.requiredCapabilities.forEach { capability ->
            issues += ValidationIssue(IssueSeverity.INFO, capability.requirementMessage)
        }

        return ValidationReport(issues)
    }

    private fun validateTrigger(trigger: Trigger, issues: MutableList<ValidationIssue>) {
        when (trigger) {
            is Trigger.TimeTrigger -> {
                if (trigger.hour !in 0..23 || trigger.minute !in 0..59) {
                    issues += ValidationIssue(IssueSeverity.ERROR, "The scheduled time is not valid.")
                }
            }
            is Trigger.FileTrigger -> {
                if (trigger.folderUri.isBlank()) {
                    issues += ValidationIssue(IssueSeverity.ERROR, "Choose a folder to watch.")
                }
            }
            is Trigger.BatteryLevelTrigger -> {
                if (trigger.level !in 0..100) {
                    issues += ValidationIssue(IssueSeverity.ERROR, "Battery level must be between 0 and 100.")
                }
            }
            is Trigger.NotificationTrigger -> {
                if (trigger.allowedPackages.isEmpty() &&
                    trigger.titlePattern.isBlank() &&
                    trigger.textPattern.isBlank()
                ) {
                    issues += ValidationIssue(
                        IssueSeverity.WARNING,
                        "This runs for every notification from every app. Add an app or a text filter to narrow it down."
                    )
                }
            }
            is Trigger.WiFiConnectionTrigger -> {
                if (trigger.ssid.isNotBlank()) {
                    issues += ValidationIssue(
                        IssueSeverity.INFO,
                        "Matching a specific Wi-Fi name needs the Location permission — without it Android hides network names."
                    )
                }
            }
            else -> Unit
        }
    }

    private fun validateCondition(
        condition: Condition,
        depth: Int,
        issues: MutableList<ValidationIssue>
    ) {
        if (depth > EngineLimits.MAX_CONDITION_DEPTH) {
            issues += ValidationIssue(
                IssueSeverity.ERROR,
                "Conditions are nested deeper than ${EngineLimits.MAX_CONDITION_DEPTH} levels."
            )
            return
        }
        when (condition) {
            is Condition.AndCondition -> {
                if (condition.conditions.isEmpty()) {
                    issues += ValidationIssue(IssueSeverity.WARNING, "An empty \"all of\" group always passes.")
                }
                condition.conditions.forEach { validateCondition(it, depth + 1, issues) }
            }
            is Condition.OrCondition -> {
                if (condition.conditions.isEmpty()) {
                    issues += ValidationIssue(IssueSeverity.ERROR, "An empty \"any of\" group can never pass.")
                }
                condition.conditions.forEach { validateCondition(it, depth + 1, issues) }
            }
            is Condition.NotCondition -> validateCondition(condition.condition, depth + 1, issues)
            is Condition.NotificationTitleCondition ->
                requireText(condition.value, "Notification title condition", issues)
            is Condition.NotificationTextCondition ->
                requireText(condition.value, "Notification text condition", issues)
            is Condition.NotificationAppCondition ->
                requireText(condition.packageName, "Notification app condition", issues)
            is Condition.NotificationCategoryCondition ->
                requireText(condition.category, "Notification category condition", issues)
            is Condition.FileNameContainsCondition ->
                requireText(condition.text, "File name condition", issues)
            is Condition.FileExtensionCondition ->
                requireText(condition.extension, "File extension condition", issues)
            is Condition.BatteryLevelCondition -> {
                if (condition.level !in 0..100) {
                    issues += ValidationIssue(IssueSeverity.ERROR, "Battery condition must be between 0 and 100.")
                }
            }
            else -> Unit
        }
    }

    private fun requireText(value: String, what: String, issues: MutableList<ValidationIssue>) {
        if (value.isBlank()) {
            issues += ValidationIssue(IssueSeverity.ERROR, "$what has no value.")
        }
    }

    private fun validateAction(
        action: Action,
        depth: Int,
        defined: MutableSet<String>,
        issues: MutableList<ValidationIssue>
    ) {
        if (depth > EngineLimits.MAX_BRANCH_DEPTH) {
            issues += ValidationIssue(
                IssueSeverity.ERROR,
                "Branches are nested deeper than ${EngineLimits.MAX_BRANCH_DEPTH} levels."
            )
            return
        }
        when (val a = action.unwrapped) {
            is Action.ShowNotificationAction -> {
                if (a.title.isBlank()) {
                    issues += ValidationIssue(IssueSeverity.ERROR, "The notification action has no title.")
                }
                checkTemplate(a.title, "notification title", defined, issues)
                checkTemplate(a.message, "notification message", defined, issues)
            }
            is Action.LogAction -> checkTemplate(a.message, "log message", defined, issues)
            is Action.DelayAction -> {
                if (a.durationMillis !in 0..EngineLimits.MAX_DELAY_MILLIS) {
                    issues += ValidationIssue(
                        IssueSeverity.ERROR,
                        "Delay must be between 0 and ${EngineLimits.MAX_DELAY_MILLIS / 1000} seconds."
                    )
                }
            }
            is Action.SetVariableAction -> {
                val name = a.name.trim()
                if (!TemplateResolver.VALID_LOCAL_NAME.matches(name)) {
                    issues += ValidationIssue(
                        IssueSeverity.ERROR,
                        "\"${a.name}\" is not a valid variable name."
                    )
                } else {
                    checkTemplate(a.value, "variable \"$name\"", defined, issues)
                    defined += name
                }
            }
            is Action.RenameFileAction -> {
                if (a.newName.isBlank()) {
                    issues += ValidationIssue(IssueSeverity.ERROR, "The rename action has no new name.")
                }
                checkTemplate(a.newName, "new file name", defined, issues)
            }
            is Action.CopyFileAction -> {
                if (a.destinationFolderUri.isBlank()) {
                    issues += ValidationIssue(IssueSeverity.ERROR, "The copy action has no destination folder.")
                }
            }
            is Action.MoveFileAction -> {
                if (a.destinationFolderUri.isBlank()) {
                    issues += ValidationIssue(IssueSeverity.ERROR, "The move action has no destination folder.")
                }
            }
            is Action.BranchAction -> {
                validateCondition(a.condition, depth = 0, issues = issues)
                if (a.thenActions.isEmpty() && a.elseActions.isEmpty()) {
                    issues += ValidationIssue(IssueSeverity.ERROR, "The If / else step has no steps to run.")
                }
                // A branch may or may not run, so variables it defines are not
                // guaranteed for later steps: validate its inner steps against
                // a copy and do not export the names.
                val branchScope = defined.toMutableSet()
                a.thenActions.forEach { validateAction(it, depth + 1, branchScope, issues) }
                val elseScope = defined.toMutableSet()
                a.elseActions.forEach { validateAction(it, depth + 1, elseScope, issues) }
            }
            is Action.UiAutomationAction -> {
                when (val r = UiWorkflowValidator.validate(a)) {
                    is UiWorkflowValidator.Result.Invalid ->
                        issues += ValidationIssue(IssueSeverity.ERROR, r.message)
                    UiWorkflowValidator.Result.Ok ->
                        UiWorkflowValidator.warnings(a).forEach {
                            issues += ValidationIssue(IssueSeverity.WARNING, it)
                        }
                }
                if (a.steps.size > EngineLimits.MAX_UI_STEPS) {
                    issues += ValidationIssue(
                        IssueSeverity.ERROR,
                        "A UI automation may have at most ${EngineLimits.MAX_UI_STEPS} steps."
                    )
                }
                if (a.overallTimeoutMillis !in EngineLimits.MIN_UI_TIMEOUT_MILLIS..EngineLimits.MAX_UI_TIMEOUT_MILLIS) {
                    issues += ValidationIssue(
                        IssueSeverity.ERROR,
                        "The UI automation timeout must be between " +
                            "${EngineLimits.MIN_UI_TIMEOUT_MILLIS / 1000} and " +
                            "${EngineLimits.MAX_UI_TIMEOUT_MILLIS / 1000} seconds."
                    )
                }
                a.steps.forEach { step ->
                    if (step is com.dhruw.autoflow.automation.model.UiStep.SetText) {
                        checkTemplate(step.text, "typed text", defined, issues)
                    }
                }
            }
            is Action.GroupMarker -> {
                if (a.label.isBlank()) {
                    issues += ValidationIssue(IssueSeverity.WARNING, "A group label is empty.")
                }
            }
            else -> Unit
        }
    }

    private fun checkTemplate(
        template: String,
        what: String,
        defined: Set<String>,
        issues: MutableList<ValidationIssue>
    ) {
        when (val r = TemplateResolver.validate(template, defined)) {
            is TemplateResolver.Result.UnknownVariable ->
                issues += ValidationIssue(
                    IssueSeverity.ERROR,
                    "The $what uses {{${r.variable}}}, which is not a known variable. " +
                        "Set it with a Set variable step first, or use one of the trigger variables."
                )
            else -> Unit
        }
    }
}

/** True when this step actually executes (not disabled, not a group label). */
private val Action.isRunnable: Boolean
    get() = this !is Action.DisabledAction && this.unwrapped !is Action.GroupMarker

private val Capability.requirementMessage: String
    get() = when (this) {
        Capability.NOTIFICATION_ACCESS -> "This workflow needs Notification access."
        Capability.ACCESSIBILITY -> "This workflow needs Accessibility."
        Capability.FILE_ACCESS -> "This workflow needs access to a folder you choose."
        Capability.POST_NOTIFICATIONS -> "This workflow needs permission to post notifications."
    }
