package com.dhruw.autoflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room row for one execution. [automationName] is denormalized so history
 * survives automation rename/delete (history is kept when an automation is
 * deleted — see RoomExecutionRepository).
 */
@Entity(
    tableName = "executions",
    indices = [
        Index("automation_id"),
        Index("started_at")
    ]
)
data class ExecutionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "automation_id") val automationId: String,
    @ColumnInfo(name = "automation_name") val automationName: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    val status: String,
    @ColumnInfo(name = "total_actions") val totalActions: Int,
    @ColumnInfo(name = "completed_actions") val completedActions: Int,
    @ColumnInfo(name = "current_action") val currentAction: String?,
    val message: String?,
    val error: String?,
    @ColumnInfo(name = "logs_json") val logsJson: String
)
