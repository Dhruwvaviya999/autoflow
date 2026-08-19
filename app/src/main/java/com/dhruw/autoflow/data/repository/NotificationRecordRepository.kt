package com.dhruw.autoflow.data.repository

import com.dhruw.autoflow.automation.model.NotificationRecord

/**
 * Storage for notifications the user explicitly chose to save via a
 * SaveNotificationAction. Local only; capped by the implementation.
 */
interface NotificationRecordRepository {

    suspend fun save(record: NotificationRecord)

    /** Newest first. */
    suspend fun getAll(): List<NotificationRecord>

    suspend fun clear()
}
