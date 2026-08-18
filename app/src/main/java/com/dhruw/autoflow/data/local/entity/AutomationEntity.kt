package com.dhruw.autoflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room row for an automation. The polymorphic workflow definition (trigger,
 * conditions, actions) is stored as type-tagged JSON TEXT so new types never
 * require a schema change.
 */
@Entity(tableName = "automations")
data class AutomationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val enabled: Boolean,
    @ColumnInfo(name = "trigger_json") val triggerJson: String,
    @ColumnInfo(name = "conditions_json") val conditionsJson: String,
    @ColumnInfo(name = "actions_json") val actionsJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "last_run_at") val lastRunAt: Long?
)
