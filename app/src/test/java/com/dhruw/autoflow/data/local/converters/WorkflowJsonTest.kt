package com.dhruw.autoflow.data.local.converters

import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.Condition
import com.dhruw.autoflow.automation.model.TextMatchMode
import com.dhruw.autoflow.automation.model.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkflowJsonTest {

    @Test
    fun `manual trigger round-trips`() {
        val json = WorkflowJson.encodeTrigger(Trigger.ManualTrigger)
        assertEquals(Trigger.ManualTrigger, WorkflowJson.decodeTrigger(json))
    }

    @Test
    fun `time trigger round-trips with repeat mode`() {
        val trigger = Trigger.TimeTrigger(hour = 18, minute = 30, repeat = Trigger.TimeTrigger.Repeat.ONCE)
        assertEquals(trigger, WorkflowJson.decodeTrigger(WorkflowJson.encodeTrigger(trigger)))

        val daily = Trigger.TimeTrigger(hour = 8, minute = 0, repeat = Trigger.TimeTrigger.Repeat.DAILY)
        assertEquals(daily, WorkflowJson.decodeTrigger(WorkflowJson.encodeTrigger(daily)))
    }

    @Test
    fun `conditions round-trip`() {
        val conditions = listOf<Condition>(Condition.AlwaysCondition)
        assertEquals(conditions, WorkflowJson.decodeConditions(WorkflowJson.encodeConditions(conditions)))
        assertEquals(emptyList<Condition>(), WorkflowJson.decodeConditions(WorkflowJson.encodeConditions(emptyList())))
    }

    @Test
    fun `all action types round-trip in order`() {
        val actions = listOf(
            Action.ShowNotificationAction(title = "Hi", message = "With \"quotes\" and unicode ✓"),
            Action.DelayAction(durationMillis = 5_000),
            Action.LogAction(message = "")
        )
        assertEquals(actions, WorkflowJson.decodeActions(WorkflowJson.encodeActions(actions)))
    }

    @Test
    fun `logs round-trip`() {
        val logs = listOf("Started 1. Delay", "Completed 1. Delay")
        assertEquals(logs, WorkflowJson.decodeLogs(WorkflowJson.encodeLogs(logs)))
    }

    @Test
    fun `file trigger round-trips`() {
        val trigger = Trigger.FileTrigger(
            folderUri = "content://com.android.externalstorage.documents/tree/primary%3ADownload",
            folderLabel = "Download",
            namePattern = "instagram",
            extension = ".zip"
        )
        assertEquals(trigger, WorkflowJson.decodeTrigger(WorkflowJson.encodeTrigger(trigger)))
    }

    @Test
    fun `file conditions round-trip`() {
        val conditions = listOf(
            Condition.FileExtensionCondition(".zip"),
            Condition.FileNameContainsCondition("instagram"),
            Condition.FileSizeCondition(
                Condition.FileSizeCondition.Comparison.LESS_THAN,
                100L * 1024 * 1024
            )
        )
        assertEquals(conditions, WorkflowJson.decodeConditions(WorkflowJson.encodeConditions(conditions)))
    }

    @Test
    fun `file and instagram actions round-trip`() {
        val actions = listOf(
            Action.CopyFileAction("content://dest", "Backups"),
            Action.MoveFileAction("content://dest2", "Archive"),
            Action.RenameFileAction("renamed.zip"),
            Action.InstagramAnalysisAction
        )
        assertEquals(actions, WorkflowJson.decodeActions(WorkflowJson.encodeActions(actions)))
    }

    @Test
    fun `unknown trigger type throws WorkflowJsonException`() {
        assertThrows(WorkflowJsonException::class.java) {
            WorkflowJson.decodeTrigger("""{"type":"file_changed","path":"/x"}""")
        }
    }

    @Test
    fun `unknown action type throws WorkflowJsonException`() {
        assertThrows(WorkflowJsonException::class.java) {
            WorkflowJson.decodeActions("""[{"type":"launch_app","package":"x"}]""")
        }
    }

    @Test
    fun `malformed json throws WorkflowJsonException`() {
        assertThrows(WorkflowJsonException::class.java) { WorkflowJson.decodeTrigger("not json") }
        assertThrows(WorkflowJsonException::class.java) { WorkflowJson.decodeActions("{}") }
        assertThrows(WorkflowJsonException::class.java) { WorkflowJson.decodeLogs("{\"a\":1}") }
    }

    @Test
    fun `missing fields throw WorkflowJsonException`() {
        assertThrows(WorkflowJsonException::class.java) {
            WorkflowJson.decodeTrigger("""{"type":"time","hour":8}""")
        }
        assertThrows(WorkflowJsonException::class.java) {
            WorkflowJson.decodeTrigger("""{"type":"time","hour":8,"minute":0,"repeat":"WEEKLY"}""")
        }
    }

    @Test
    fun `notification trigger round-trips`() {
        val anyApp = Trigger.NotificationTrigger()
        assertEquals(anyApp, WorkflowJson.decodeTrigger(WorkflowJson.encodeTrigger(anyApp)))

        val specific = Trigger.NotificationTrigger(
            allowedPackages = setOf("com.whatsapp"),
            appLabel = "WhatsApp",
            titlePattern = "Interview",
            textPattern = "job opening",
            matchMode = TextMatchMode.STARTS_WITH
        )
        assertEquals(specific, WorkflowJson.decodeTrigger(WorkflowJson.encodeTrigger(specific)))
    }

    @Test
    fun `notification conditions round-trip`() {
        val conditions = listOf(
            Condition.NotificationAppCondition("com.whatsapp", "WhatsApp"),
            Condition.NotificationTitleCondition("Interview", TextMatchMode.EQUALS),
            Condition.NotificationTextCondition("job opening"),
            Condition.NotificationCategoryCondition("msg")
        )
        assertEquals(conditions, WorkflowJson.decodeConditions(WorkflowJson.encodeConditions(conditions)))
    }

    @Test
    fun `composite conditions round-trip nested`() {
        val conditions = listOf<Condition>(
            Condition.AndCondition(
                listOf(
                    Condition.NotificationAppCondition("com.whatsapp"),
                    Condition.OrCondition(
                        listOf(
                            Condition.NotificationTextCondition("job"),
                            Condition.NotCondition(Condition.NotificationTextCondition("spam"))
                        )
                    )
                )
            )
        )
        assertEquals(conditions, WorkflowJson.decodeConditions(WorkflowJson.encodeConditions(conditions)))
    }

    @Test
    fun `save notification action round-trips`() {
        val actions = listOf<Action>(Action.SaveNotificationAction)
        assertEquals(actions, WorkflowJson.decodeActions(WorkflowJson.encodeActions(actions)))
    }
}
