package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.automation.model.Capability
import com.dhruw.autoflow.automation.model.Execution
import com.dhruw.autoflow.automation.model.ExecutionStatus
import com.dhruw.autoflow.automation.model.requiredCapabilities

/**
 * What the automation list shows next to each item. Every state is derived
 * from real data — granted capabilities, validation output and recorded
 * executions. There is no invented score.
 */
enum class HealthStatus {
    /** Configured correctly, has what it needs, and is not failing. */
    HEALTHY,

    /** Valid, but a capability it needs is not granted right now. */
    NEEDS_PERMISSION,

    /** The workflow itself has a validation error. */
    CONFIGURATION_ISSUE,

    /** The recent runs are failing. */
    FAILING,

    /** Switched off by the user (or by the auto-disable policy). */
    DISABLED
}

data class AutomationHealth(
    val status: HealthStatus,
    /** Short user-facing explanation; empty for healthy automations. */
    val detail: String,
    /** Capabilities this automation needs that are not granted. */
    val missingCapabilities: Set<Capability> = emptySet(),
    val consecutiveFailures: Int = 0
)

/**
 * Computes [AutomationHealth] from data the app already has. Pure Kotlin: the
 * caller supplies which capabilities are currently granted, so this stays
 * testable and platform-free.
 */
class AutomationHealthCalculator(
    private val validator: WorkflowValidator = WorkflowValidator()
) {

    /**
     * [recentExecutions] should be that automation's runs, newest first.
     * Only completed runs count — a RUNNING or SKIPPED entry says nothing
     * about health.
     */
    fun calculate(
        automation: Automation,
        recentExecutions: List<Execution>,
        grantedCapabilities: Set<Capability>
    ): AutomationHealth {
        val consecutiveFailures = countConsecutiveFailures(recentExecutions)

        if (!automation.enabled) {
            return AutomationHealth(
                status = HealthStatus.DISABLED,
                detail = "Switched off",
                consecutiveFailures = consecutiveFailures
            )
        }

        val report = validator.validate(automation)
        if (!report.isValid) {
            return AutomationHealth(
                status = HealthStatus.CONFIGURATION_ISSUE,
                detail = report.errors.first().message,
                consecutiveFailures = consecutiveFailures
            )
        }

        val missing = automation.requiredCapabilities - grantedCapabilities
        if (missing.isNotEmpty()) {
            return AutomationHealth(
                status = HealthStatus.NEEDS_PERMISSION,
                detail = missing.joinToString(", ") { it.shortLabel } + " needed",
                missingCapabilities = missing,
                consecutiveFailures = consecutiveFailures
            )
        }

        if (consecutiveFailures >= FAILING_THRESHOLD) {
            return AutomationHealth(
                status = HealthStatus.FAILING,
                detail = "$consecutiveFailures runs in a row failed",
                consecutiveFailures = consecutiveFailures
            )
        }

        return AutomationHealth(
            status = HealthStatus.HEALTHY,
            detail = "",
            consecutiveFailures = consecutiveFailures
        )
    }

    /** Failed runs at the head of the list, stopping at the first success. */
    fun countConsecutiveFailures(recentExecutions: List<Execution>): Int {
        var count = 0
        for (execution in recentExecutions) {
            when (execution.status) {
                ExecutionStatus.FAILED -> count++
                ExecutionStatus.SUCCESS -> return count
                // Cancelled/skipped/running runs are not evidence either way.
                else -> Unit
            }
        }
        return count
    }

    private companion object {
        const val FAILING_THRESHOLD = 3
    }
}

private val Capability.shortLabel: String
    get() = when (this) {
        Capability.NOTIFICATION_ACCESS -> "Notification access"
        Capability.ACCESSIBILITY -> "Accessibility"
        Capability.FILE_ACCESS -> "Folder access"
        Capability.POST_NOTIFICATIONS -> "Notification permission"
    }
