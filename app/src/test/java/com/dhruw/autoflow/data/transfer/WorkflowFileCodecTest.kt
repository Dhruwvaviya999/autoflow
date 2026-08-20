package com.dhruw.autoflow.data.transfer

import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.automation.model.Condition
import com.dhruw.autoflow.automation.model.LevelComparison
import com.dhruw.autoflow.automation.model.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs on the JVM: org.json is available through the unit-test runtime, and
 * the codec deliberately uses nothing else.
 */
class WorkflowFileCodecTest {

    private fun automation(
        name: String = "Low battery alert",
        actions: List<Action> = listOf(Action.ShowNotificationAction("Low", "{{battery.level}}%"))
    ) = Automation(
        id = "local-id-should-not-be-exported",
        name = name,
        description = "Warns on low battery",
        enabled = true,
        trigger = Trigger.BatteryLevelTrigger(LevelComparison.LESS_OR_EQUAL, 20),
        conditions = listOf(Condition.IsChargingCondition(false)),
        actions = actions,
        createdAt = 111L,
        updatedAt = 222L,
        lastRunAt = 333L
    )

    private fun encode(vararg automations: Automation) = WorkflowFileCodec.encode(
        automations = automations.toList(),
        exportedAt = 1_000L,
        appVersion = "1.0"
    )

    @Test
    fun `round trip preserves the workflow definition`() {
        val payload = WorkflowFileCodec.decode(encode(automation()))
        val decoded = payload.definitions.single().automation

        assertEquals("Low battery alert", decoded.name)
        assertEquals(Trigger.BatteryLevelTrigger(LevelComparison.LESS_OR_EQUAL, 20), decoded.trigger)
        assertEquals(listOf(Condition.IsChargingCondition(false)), decoded.conditions)
        assertEquals(1, decoded.actions.size)
    }

    @Test
    fun `export never carries ids history or timestamps`() {
        val raw = encode(automation())

        assertFalse(raw.contains("local-id-should-not-be-exported"))
        assertFalse(raw.contains("lastRunAt"))
        assertFalse(raw.contains("333"))
    }

    @Test
    fun `imported workflows arrive disabled with no id`() {
        val decoded = WorkflowFileCodec.decode(encode(automation())).definitions.single().automation

        assertFalse(decoded.enabled)
        assertEquals("", decoded.id)
        assertEquals(null, decoded.lastRunAt)
    }

    @Test
    fun `multiple automations round trip`() {
        val payload = WorkflowFileCodec.decode(
            encode(automation(name = "One"), automation(name = "Two"))
        )

        assertEquals(listOf("One", "Two"), payload.definitions.map { it.name })
    }

    @Test
    fun `phase 8 action types survive a round trip`() {
        val rich = automation(
            actions = listOf(
                Action.SetVariableAction("jobType", "developer"),
                Action.GroupMarker("Decide"),
                Action.BranchAction(
                    condition = Condition.NotificationTextCondition("job"),
                    thenActions = listOf(Action.LogAction("matched {{jobType}}")),
                    elseActions = listOf(Action.DisabledAction(Action.LogAction("off")))
                )
            )
        )

        val decoded = WorkflowFileCodec.decode(encode(rich)).definitions.single().automation
        val branch = decoded.actions[2] as Action.BranchAction

        assertEquals(Action.SetVariableAction("jobType", "developer"), decoded.actions[0])
        assertEquals(Action.GroupMarker("Decide"), decoded.actions[1])
        assertEquals(listOf(Action.LogAction("matched {{jobType}}")), branch.thenActions)
        assertTrue(branch.elseActions.single() is Action.DisabledAction)
    }

    @Test
    fun `auto-disable threshold survives a round trip`() {
        val decoded = WorkflowFileCodec.decode(
            encode(automation().copy(disableAfterFailures = 5))
        ).definitions.single().automation

        assertEquals(5, decoded.disableAfterFailures)
    }

    @Test
    fun `a newer schema version is rejected`() {
        val raw = encode(automation()).replace("\"schemaVersion\": 1", "\"schemaVersion\": 99")

        val error = runCatching { WorkflowFileCodec.decode(raw) }.exceptionOrNull()

        assertTrue(error is WorkflowFileException)
        assertTrue(error!!.message!!.contains("newer version"))
    }

    @Test
    fun `a file from another app is rejected`() {
        val error = runCatching {
            WorkflowFileCodec.decode("""{"format":"something-else","schemaVersion":1}""")
        }.exceptionOrNull()

        assertTrue(error is WorkflowFileException)
    }

    @Test
    fun `malformed json is rejected`() {
        assertTrue(
            runCatching { WorkflowFileCodec.decode("not json at all") }
                .exceptionOrNull() is WorkflowFileException
        )
    }

    @Test
    fun `an empty automation list is rejected`() {
        val error = runCatching {
            WorkflowFileCodec.decode(
                """{"format":"autoflow","schemaVersion":1,"kind":"workflows","automations":[]}"""
            )
        }.exceptionOrNull()

        assertTrue(error is WorkflowFileException)
    }

    @Test
    fun `an unknown action type is rejected with the workflow name`() {
        val raw = """
            {"format":"autoflow","schemaVersion":1,"kind":"workflows","automations":[
              {"name":"Weird","description":"","trigger":{"type":"manual"},
               "conditions":[],"actions":[{"type":"launch_missiles"}]}
            ]}
        """.trimIndent()

        val error = runCatching { WorkflowFileCodec.decode(raw) }.exceptionOrNull()

        assertTrue(error is WorkflowFileException)
        assertTrue(error!!.message!!.contains("Weird"))
    }

    @Test
    fun `backup kind is preserved`() {
        val raw = WorkflowFileCodec.encode(
            automations = listOf(automation()),
            kind = TransferKind.BACKUP,
            exportedAt = 1L,
            appVersion = "1.0"
        )

        assertEquals(TransferKind.BACKUP, WorkflowFileCodec.decode(raw).kind)
    }
}
