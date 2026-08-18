package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.Condition
import com.dhruw.autoflow.automation.model.TriggerPayload
import java.util.Locale

/**
 * Evaluates a condition tree. Composite conditions (And/Or/Not) get new
 * recursive branches here when they are introduced.
 *
 * File conditions need the run's [TriggerPayload.FileEvent]; without one
 * they fail, which turns the run into a SKIPPED execution rather than a
 * crash or a blind pass.
 */
open class ConditionEvaluator {

    open fun evaluate(condition: Condition, payload: TriggerPayload? = null): Boolean {
        val file = payload as? TriggerPayload.FileEvent
        return when (condition) {
            is Condition.AlwaysCondition -> true
            is Condition.FileExtensionCondition ->
                file != null &&
                    file.extension.trim().removePrefix(".").lowercase(Locale.ROOT) ==
                    condition.normalizedExtension
            is Condition.FileNameContainsCondition ->
                file != null &&
                    condition.text.isNotBlank() &&
                    file.name.lowercase(Locale.ROOT)
                        .contains(condition.text.trim().lowercase(Locale.ROOT))
            is Condition.FileSizeCondition -> file != null && when (condition.comparison) {
                Condition.FileSizeCondition.Comparison.LESS_THAN ->
                    file.sizeBytes < condition.sizeBytes
                Condition.FileSizeCondition.Comparison.GREATER_THAN ->
                    file.sizeBytes > condition.sizeBytes
            }
        }
    }

    /** All conditions must pass; an empty list passes. */
    open fun evaluateAll(conditions: List<Condition>, payload: TriggerPayload? = null): Boolean =
        conditions.all { evaluate(it, payload) }
}
