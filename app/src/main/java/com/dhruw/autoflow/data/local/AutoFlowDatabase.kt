package com.dhruw.autoflow.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dhruw.autoflow.data.local.dao.AutomationDao
import com.dhruw.autoflow.data.local.dao.ExecutionDao
import com.dhruw.autoflow.data.local.dao.NotificationRecordDao
import com.dhruw.autoflow.data.local.entity.AutomationEntity
import com.dhruw.autoflow.data.local.entity.ExecutionEntity
import com.dhruw.autoflow.data.local.entity.NotificationRecordEntity

/**
 * Version 1 is the initial schema (exported to app/schemas); version 2 adds
 * the notification_records table; version 3 adds the per-automation
 * consecutive-failure auto-disable threshold. Future versions must ship
 * explicit migrations — no destructive fallback is configured, so a missing
 * migration fails loudly in development instead of wiping user data.
 *
 * Workflow contents (triggers, conditions, actions, and everything Phase 8
 * added: variables, branches, disabled steps, group labels) ride inside the
 * existing JSON TEXT columns and never require a schema change.
 */
@Database(
    entities = [
        AutomationEntity::class,
        ExecutionEntity::class,
        NotificationRecordEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AutoFlowDatabase : RoomDatabase() {

    abstract fun automationDao(): AutomationDao

    abstract fun executionDao(): ExecutionDao

    abstract fun notificationRecordDao(): NotificationRecordDao

    companion object {
        private const val NAME = "autoflow.db"

        /** Kept in step with the @Database annotation; shown in Diagnostics. */
        const val SCHEMA_VERSION = 3

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notification_records` (
                        `id` TEXT NOT NULL,
                        `package_name` TEXT NOT NULL,
                        `app_name` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `automation_id` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_notification_records_timestamp` " +
                        "ON `notification_records` (`timestamp`)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `automations` ADD COLUMN `disable_after_failures` INTEGER DEFAULT NULL"
                )
            }
        }

        @Volatile
        private var instance: AutoFlowDatabase? = null

        fun getInstance(context: Context): AutoFlowDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AutoFlowDatabase::class.java,
                    NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
