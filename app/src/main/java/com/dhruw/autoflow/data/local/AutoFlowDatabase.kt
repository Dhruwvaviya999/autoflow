package com.dhruw.autoflow.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.dhruw.autoflow.data.local.dao.AutomationDao
import com.dhruw.autoflow.data.local.dao.ExecutionDao
import com.dhruw.autoflow.data.local.entity.AutomationEntity
import com.dhruw.autoflow.data.local.entity.ExecutionEntity

/**
 * Version 1 is the initial schema (exported to app/schemas). Future versions
 * must ship explicit migrations — no destructive fallback is configured, so a
 * missing migration fails loudly in development instead of wiping user data.
 */
@Database(
    entities = [AutomationEntity::class, ExecutionEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AutoFlowDatabase : RoomDatabase() {

    abstract fun automationDao(): AutomationDao

    abstract fun executionDao(): ExecutionDao

    companion object {
        private const val NAME = "autoflow.db"

        @Volatile
        private var instance: AutoFlowDatabase? = null

        fun getInstance(context: Context): AutoFlowDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AutoFlowDatabase::class.java,
                    NAME
                ).build().also { instance = it }
            }
    }
}
