package com.dhruw.autoflow.data.transfer

import com.dhruw.autoflow.automation.engine.WorkflowValidator
import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.automation.model.Capability
import com.dhruw.autoflow.automation.model.Trigger
import com.dhruw.autoflow.automation.model.requiredCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplatesAndDuplicationTest {

    private val validator = WorkflowValidator()

    // --- Templates ---

    @Test
    fun everyTemplateHasAUniqueId() {
        val ids = AutomationTemplates.all.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun templatesBuildDisabledAutomationsWithNoId() {
        AutomationTemplates.all.forEach { template ->
            val automation = template.build()

            assertFalse("${template.id} must start disabled", automation.enabled)
            assertEquals("${template.id} must not carry an id", "", automation.id)
            assertEquals(null, automation.lastRunAt)
        }
    }

    @Test
    fun templatesThatOnlyNeedConfigurationStillDeclareTheirCapabilities() {
        val instagram = AutomationTemplates.byId("instagram_analyzer")!!.build()
        val uiWorkflow = AutomationTemplates.byId("app_ui_workflow")!!.build()

        assertTrue(instagram.requiredCapabilities.contains(Capability.FILE_ACCESS))
        assertTrue(uiWorkflow.requiredCapabilities.contains(Capability.ACCESSIBILITY))
    }

    @Test
    fun readyToRunTemplatesPassValidation() {
        // These need no user configuration, so they must be valid as shipped.
        listOf("low_battery_alert", "important_notification").forEach { id ->
            val report = validator.validate(AutomationTemplates.byId(id)!!.build())

            assertTrue("$id should be valid: ${report.errors}", report.isValid)
        }
    }

    @Test
    fun templatesNeedingSetupReportWhatIsMissing() {
        // The folder/app fields are intentionally blank — the validator must
        // say so rather than letting a half-configured workflow look ready.
        val report = validator.validate(AutomationTemplates.byId("downloads_organizer")!!.build())

        assertFalse(report.isValid)
        assertTrue(report.errors.any { it.message.contains("folder") })
    }

    @Test
    fun templateVariablesResolveAgainstTheirOwnSteps() {
        val report = validator.validate(AutomationTemplates.byId("important_notification")!!.build())

        // {{source}} is set by the template's own Set variable step.
        assertTrue(report.errors.none { it.message.contains("source") })
    }

    // --- Duplication semantics (the rules the list applies) ---

    private fun original() = Automation(
        id = "id-1",
        name = "Job alert",
        enabled = true,
        trigger = Trigger.ManualTrigger,
        actions = listOf(Action.LogAction("hi")),
        createdAt = 1L,
        updatedAt = 2L,
        lastRunAt = 3L
    )

    /** Mirrors AutomationsViewModel.duplicate's transformation. */
    private fun duplicate(source: Automation, newId: String, now: Long) = source.copy(
        id = newId,
        name = "${source.name} (Copy)",
        enabled = false,
        createdAt = now,
        updatedAt = now,
        lastRunAt = null
    )

    @Test
    fun aDuplicateKeepsTheWorkflowButResetsIdentityAndState() {
        val copy = duplicate(original(), newId = "id-2", now = 99L)

        assertNotEquals(original().id, copy.id)
        assertEquals("Job alert (Copy)", copy.name)
        assertFalse(copy.enabled)
        assertEquals(null, copy.lastRunAt)
        assertEquals(original().actions, copy.actions)
        assertEquals(original().trigger, copy.trigger)
    }

    @Test
    fun aDuplicateIsStillAValidWorkflow() {
        assertTrue(validator.validate(duplicate(original(), "id-2", 99L)).isValid)
    }
}
