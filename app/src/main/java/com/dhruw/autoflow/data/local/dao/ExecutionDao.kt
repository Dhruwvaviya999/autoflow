package com.dhruw.autoflow.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.dhruw.autoflow.data.local.entity.ExecutionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExecutionDao {

    @Query("SELECT * FROM executions ORDER BY started_at DESC")
    fun observeAll(): Flow<List<ExecutionEntity>>

    @Upsert
    suspend fun upsert(execution: ExecutionEntity)

    /** Keeps the newest [keep] rows so history cannot grow without bound. */
    @Query(
        """
        DELETE FROM executions WHERE id NOT IN (
            SELECT id FROM executions ORDER BY started_at DESC LIMIT :keep
        )
        """
    )
    suspend fun prune(keep: Int)

    @Query("DELETE FROM executions")
    suspend fun clear()
}
