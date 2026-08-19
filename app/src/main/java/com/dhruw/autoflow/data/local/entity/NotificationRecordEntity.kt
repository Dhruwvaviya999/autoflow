package com.dhruw.autoflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room row for one notification explicitly saved by a SaveNotificationAction.
 * Never written for notifications the user did not opt to save. The table is
 * pruned to the newest rows on every insert (see RoomNotificationRecordRepository).
 */
@Entity(
    tableName = "notification_records",
    indices = [Index("timestamp")]
)
data class NotificationRecordEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "app_name") val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    @ColumnInfo(name = "automation_id") val automationId: String
)
