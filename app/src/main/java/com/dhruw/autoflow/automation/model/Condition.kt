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
}

val Condition.displayName: String
    get() = when (this) {
        is Condition.AlwaysCondition -> "Always"
        is Condition.FileExtensionCondition -> "File extension"
        is Condition.FileNameContainsCondition -> "File name contains"
        is Condition.FileSizeCondition -> "File size"
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
    }
