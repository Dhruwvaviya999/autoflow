package com.dhruw.autoflow.automation.model

enum class ExecutionStatus {
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED,
    SKIPPED
}

/**
 * One run of an automation. [automationName] is denormalized so history
 * stays readable after the automation is renamed or deleted.
 */
data class Execution(
    val id: String,
    val automationId: String,
    val automationName: String,
    val startedAt: Long,
    val completedAt: Long? = null,
    val status: ExecutionStatus,
    val totalActions: Int,
    val completedActions: Int = 0,
    val currentAction: String? = null,
    val message: String? = null,
    val error: String? = null,
    val logs: List<String> = emptyList()
)
