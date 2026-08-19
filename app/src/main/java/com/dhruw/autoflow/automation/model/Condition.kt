package com.dhruw.autoflow.automation.model

import java.util.Locale

/**
 * A gate between trigger and actions. The sealed hierarchy is built for
 * nesting, so composite conditions (And/Or/Not) can be added later without
 * touching the engine — only ConditionEvaluator grows a new branch.
 *
 * File conditions evaluate against the [TriggerPayload.FileEvent] carried by
 * the run; without a file payload they fail, so a file-gated automation run
 * manually is SKIPPED instead of acting on nothing.
 */
sealed interface Condition {

    /** Always passes. */
    data object AlwaysCondition : Condition

    /** Passes when the triggering file has this extension (".zip"/"zip"/case-insensitive). */
    data class FileExtensionCondition(val extension: String) : Condition {
        val normalizedExtension: String
            get() = extension.trim().removePrefix(".").lowercase(Locale.ROOT)
    }

    /** Passes when the triggering file's name contains [text] (case-insensitive). */
    data class FileNameContainsCondition(val text: String) : Condition

    /** Passes when the triggering file's size compares against [sizeBytes]. */
    data class FileSizeCondition(
        val comparison: Comparison,
        val sizeBytes: Long
    ) : Condition {
        enum class Comparison { LESS_THAN, GREATER_THAN }
    }

    /**
     * Notification conditions evaluate against the run's
     * [TriggerPayload.NotificationEvent]; without one they fail, so a
     * notification-gated automation run any other way is SKIPPED.
     */

    /** Passes when the notification comes from [packageName] (exact, case-insensitive). */
    data class NotificationAppCondition(
        val packageName: String,
        /** Display name captured when the user picked the app; UI only. */
        val appLabel: String = ""
    ) : Condition

    /** Passes when the notification title matches [value] under [mode]. */
    data class NotificationTitleCondition(
        val value: String,
        val mode: TextMatchMode = TextMatchMode.CONTAINS
    ) : Condition

    /** Passes when the notification text matches [value] under [mode]. */
    data class NotificationTextCondition(
        val value: String,
        val mode: TextMatchMode = TextMatchMode.CONTAINS
    ) : Condition

    /** Passes when the notification's Android category equals [category] (e.g. "msg", "email"). */
    data class NotificationCategoryCondition(val category: String) : Condition

    // Composite conditions. The editor currently builds a flat list (implicit
    // AND); these make nested logic expressible and serializable everywhere
    // else in the stack.

    /** Passes when all [conditions] pass. An empty list passes. */
    data class AndCondition(val conditions: List<Condition>) : Condition

    /** Passes when at least one of [conditions] passes. An empty list fails. */
    data class OrCondition(val conditions: List<Condition>) : Condition

    /** Passes when [condition] fails. */
    data class NotCondition(val condition: Condition) : Condition
}

val Condition.displayName: String
    get() = when (this) {
        is Condition.AlwaysCondition -> "Always"
        is Condition.FileExtensionCondition -> "File extension"
        is Condition.FileNameContainsCondition -> "File name contains"
        is Condition.FileSizeCondition -> "File size"
        is Condition.NotificationAppCondition -> "Notification app"
        is Condition.NotificationTitleCondition -> "Notification title"
        is Condition.NotificationTextCondition -> "Notification text"
        is Condition.NotificationCategoryCondition -> "Notification category"
        is Condition.AndCondition -> "All of"
        is Condition.OrCondition -> "Any of"
        is Condition.NotCondition -> "Not"
    }

private val TextMatchMode.summaryVerb: String
    get() = when (this) {
        TextMatchMode.CONTAINS -> "contains"
        TextMatchMode.EQUALS -> "is"
        TextMatchMode.STARTS_WITH -> "starts with"
    }

val Condition.summary: String
    get() = when (this) {
        is Condition.AlwaysCondition -> "Run every time"
        is Condition.FileExtensionCondition -> "Extension is .$normalizedExtension"
        is Condition.FileNameContainsCondition -> "Name contains \"${text.trim()}\""
        is Condition.FileSizeCondition -> {
            val mb = sizeBytes / (1024.0 * 1024.0)
            val value = if (mb == mb.toLong().toDouble()) "${mb.toLong()}" else "%.1f".format(mb)
            when (comparison) {
                Condition.FileSizeCondition.Comparison.LESS_THAN -> "Smaller than $value MB"
                Condition.FileSizeCondition.Comparison.GREATER_THAN -> "Larger than $value MB"
            }
        }
        is Condition.NotificationAppCondition ->
            "App is ${appLabel.ifBlank { packageName }}"
        is Condition.NotificationTitleCondition ->
            "Title ${mode.summaryVerb} \"${value.trim()}\""
        is Condition.NotificationTextCondition ->
            "Text ${mode.summaryVerb} \"${value.trim()}\""
        is Condition.NotificationCategoryCondition ->
            "Category is \"${category.trim()}\""
        is Condition.AndCondition ->
            if (conditions.isEmpty()) "Always" else conditions.joinToString(" AND ") { it.summary }
        is Condition.OrCondition ->
            if (conditions.isEmpty()) "Never" else conditions.joinToString(" OR ") { it.summary }
        is Condition.NotCondition -> "NOT (${condition.summary})"
    }
