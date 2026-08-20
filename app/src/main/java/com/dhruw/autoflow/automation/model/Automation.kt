package com.dhruw.autoflow.automation.model

data class Automation(
    val id: String,
    val name: String,
    val description: String = "",
    val enabled: Boolean = true,
    val trigger: Trigger,
    val conditions: List<Condition> = emptyList(),
    val actions: List<Action> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val lastRunAt: Long? = null,
    /**
     * Switch the automation off automatically after this many consecutive
     * failed runs. Null (the default) never auto-disables. The user is told
     * locally when it happens.
     */
    val disableAfterFailures: Int? = null
)
