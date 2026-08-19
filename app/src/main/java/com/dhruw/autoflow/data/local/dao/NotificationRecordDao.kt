package com.dhruw.autoflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dhruw.autoflow.data.local.entity.NotificationRecordEntity

@Dao
interface NotificationRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: NotificationRecordEntity)

    @Query("SELECT * FROM notification_records ORDER BY timestamp DESC")
    suspend fun getAll(): List<NotificationRecordEntity>

    @Query("SELECT COUNT(*) FROM notification_records")
    suspend fun count(): Int

    /** Keeps the newest [keep] rows so saved notifications cannot grow without bound. */
    @Query(
        """
        DELETE FROM notification_records WHERE id NOT IN (
            SELECT id FROM notification_records ORDER BY timestamp DESC LIMIT :keep
        )
        """
    )
    suspend fun prune(keep: Int)

    @Query("DELETE FROM notification_records")
    suspend fun clear()
}
