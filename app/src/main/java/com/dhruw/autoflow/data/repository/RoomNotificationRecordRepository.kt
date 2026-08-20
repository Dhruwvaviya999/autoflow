package com.dhruw.autoflow.data.repository

import com.dhruw.autoflow.automation.engine.EngineLimits
import com.dhruw.autoflow.automation.model.NotificationRecord
import com.dhruw.autoflow.data.local.converters.toDomain
import com.dhruw.autoflow.data.local.converters.toEntity
import com.dhruw.autoflow.data.local.dao.NotificationRecordDao

/**
 * Room-backed [NotificationRecordRepository]. Retention policy: the newest
 * [maxEntries] (default 500) records are kept and older ones are deleted on
 * every save — a simple, predictable local cap chosen over time-based
 * expiry so the store never grows unbounded even under heavy use.
 */
class RoomNotificationRecordRepository(
    private val dao: NotificationRecordDao,
    private val maxEntries: Int = EngineLimits.MAX_NOTIFICATION_RECORDS
) : NotificationRecordRepository {

    override suspend fun save(record: NotificationRecord) {
        dao.insert(record.toEntity())
        dao.prune(maxEntries)
    }

    override suspend fun getAll(): List<NotificationRecord> =
        dao.getAll().map { it.toDomain() }

    override suspend fun clear() {
        dao.clear()
    }
}
