package com.dhruw.autoflow.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dhruw.autoflow.automation.model.Trigger
import com.dhruw.autoflow.data.local.converters.WorkflowJson
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

    @Test
    fun migrate2To3AddsFailurePolicyColumnAndKeepsWorkflows() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                """
                INSERT INTO automations
                    (id, name, description, enabled, trigger_json, conditions_json,
                     actions_json, created_at, updated_at, last_run_at)
                VALUES
                    ('auto-2', 'Phase 7 workflow', '', 1, '{"type":"manual"}', '[]',
                     '[{"type":"log","message":"hi"}]', 1, 2, 3)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 3, true, AutoFlowDatabase.MIGRATION_2_3)
            .use { db ->
                db.query(
                    "SELECT name, last_run_at, disable_after_failures FROM automations"
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Phase 7 workflow", cursor.getString(0))
                    assertEquals(3, cursor.getInt(1))
                    // Existing rows keep the policy off.
                    assertTrue(cursor.isNull(2))
                }
            }
    }

    /**
     * A workflow created before Phase 8 must still decode after migrating all
     * the way to the current schema — the Phase 8 action types are additive.
     */
    @Test
    fun oldWorkflowSurvivesMigrationToCurrentVersion() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO automations
                    (id, name, description, enabled, trigger_json, conditions_json,
                     actions_json, created_at, updated_at, last_run_at)
                VALUES
                    ('legacy', 'Legacy', '', 1,
                     '{"type":"notification","allowedPackages":["com.example"],"appLabel":"Example"}',
                     '[{"type":"notification_text","value":"job","mode":"CONTAINS"}]',
                     '[{"type":"show_notification","title":"t","message":"m"}]', 1, 2, NULL)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            AutoFlowDatabase.SCHEMA_VERSION,
            true,
            AutoFlowDatabase.MIGRATION_1_2,
            AutoFlowDatabase.MIGRATION_2_3
        ).use { db ->
            db.query("SELECT trigger_json, conditions_json, actions_json FROM automations")
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    val trigger = WorkflowJson.decodeTrigger(cursor.getString(0))
                    val conditions = WorkflowJson.decodeConditions(cursor.getString(1))
                    val actions = WorkflowJson.decodeActions(cursor.getString(2))

                    assertTrue(trigger is Trigger.NotificationTrigger)
                    assertEquals(1, conditions.size)
                    assertEquals(1, actions.size)
                }
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
