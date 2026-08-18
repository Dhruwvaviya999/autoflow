package com.dhruw.autoflow.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.dhruw.autoflow.data.local.entity.AutomationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationDao {

    @Query("SELECT * FROM automations ORDER BY created_at DESC")
    fun observeAll(): Flow<List<AutomationEntity>>

    @Query("SELECT * FROM automations WHERE id = :id")
    suspend fun getById(id: String): AutomationEntity?

    @Query("SELECT * FROM automations ORDER BY created_at DESC")
    suspend fun getAll(): List<AutomationEntity>

    @Upsert
    suspend fun upsert(automation: AutomationEntity)

    @Query("DELETE FROM automations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE automations SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("UPDATE automations SET last_run_at = :timestamp WHERE id = :id")
    suspend fun markRun(id: String, timestamp: Long)
}
