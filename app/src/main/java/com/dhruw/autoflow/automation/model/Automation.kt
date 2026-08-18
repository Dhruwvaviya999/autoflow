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
    val lastRunAt: Long? = null
)
