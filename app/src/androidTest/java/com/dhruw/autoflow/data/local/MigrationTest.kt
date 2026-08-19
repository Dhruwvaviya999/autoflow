package com.dhruw.autoflow.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AutoFlowDatabase::class.java
    )

    @Test
    fun migrate1To2PreservesDataAndAddsNotificationRecords() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO automations
                    (id, name, description, enabled, trigger_json, conditions_json,
                     actions_json, created_at, updated_at, last_run_at)
                VALUES
                    ('auto-1', 'Test', '', 1, '{"type":"manual"}', '[]',
                     '[{"type":"log","message":"hi"}]', 1, 2, NULL)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, AutoFlowDatabase.MIGRATION_1_2)
            .use { db ->
                // Pre-migration data survives.
                db.query("SELECT id, name FROM automations").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("auto-1", cursor.getString(0))
                    assertEquals("Test", cursor.getString(1))
                }
                // New table exists and is empty.
                db.query("SELECT COUNT(*) FROM notification_records").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
            }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
