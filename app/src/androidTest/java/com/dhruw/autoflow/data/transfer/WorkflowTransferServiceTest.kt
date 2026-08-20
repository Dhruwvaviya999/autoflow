package com.dhruw.autoflow.data.transfer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.net.toUri
import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.automation.model.Condition
import com.dhruw.autoflow.automation.model.Trigger
import com.dhruw.autoflow.data.local.InMemoryAutomationRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises export → file → preview → import through real Android storage,
 * using a file:// URI in the app's cache directory so no picker is involved.
 */
@RunWith(AndroidJUnit4::class)
class WorkflowTransferServiceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var repository: InMemoryAutomationRepository
    private lateinit var service: WorkflowTransferService
    private lateinit var file: File

    @Before
    fun setUp() {
        repository = InMemoryAutomationRepository()
        service = WorkflowTransferService(
            context = context,
            repository = repository,
            appVersion = "test"
        )
        file = File(context.cacheDir, "transfer-test.autoflow")
    }

    @After
    fun tearDown() {
        file.delete()
    }

    private fun automation(name: String = "Job alert") = Automation(
        id = "original-id",
        name = name,
        description = "Watches for job notifications",
        enabled = true,
        trigger = Trigger.NotificationTrigger(
            allowedPackages = setOf("com.example.chat"),
            appLabel = "Chat"
        ),
        conditions = listOf(Condition.NotificationTextCondition("job")),
        actions = listOf(
            Action.SetVariableAction("source", "{{notification.appName}}"),
            Action.ShowNotificationAction("Job from {{source}}", "{{notification.title}}")
        ),
        createdAt = 1L,
        updatedAt = 2L,
        lastRunAt = 3L
    )

    @Test
    fun exportThenImportCreatesANewDisabledAutomation() = runBlocking {
        file.createNewFile()
        val uri = file.toUri()

        val exported = service.export(uri, listOf(automation()))
        assertTrue(exported.isSuccess)

        val preview = service.preview(uri).getOrThrow()
        assertEquals(1, preview.candidates.size)
        assertTrue(preview.candidates.single().isImportable)

        val stored = service.import(preview.candidates)
        assertEquals(1, stored)

        val imported = repository.getAll().single()
        assertEquals("Job alert", imported.name)
        assertFalse("imported workflows must arrive switched off", imported.enabled)
        assertNotEquals("original-id", imported.id)
        assertEquals(null, imported.lastRunAt)
        assertEquals(2, imported.actions.size)
    }

    @Test
    fun exportedFileContainsNoIdentifiersOrHistory() = runBlocking {
        file.createNewFile()
        service.export(file.toUri(), listOf(automation()))

        val raw = file.readText()
        assertFalse(raw.contains("original-id"))
        assertFalse(raw.contains("lastRunAt"))
    }

    @Test
    fun importingTwiceCreatesTwoIndependentAutomations() = runBlocking {
        file.createNewFile()
        val uri = file.toUri()
        service.export(uri, listOf(automation()))

        service.import(service.preview(uri).getOrThrow().candidates)
        service.import(service.preview(uri).getOrThrow().candidates)

        val ids = repository.getAll().map { it.id }
        assertEquals(2, ids.size)
        assertEquals(2, ids.toSet().size)
    }

    @Test
    fun requiredCapabilitiesAreReportedBeforeImport() = runBlocking {
        file.createNewFile()
        val uri = file.toUri()
        service.export(uri, listOf(automation()))

        val preview = service.preview(uri).getOrThrow()

        assertTrue(preview.capabilities.isNotEmpty())
    }

    @Test
    fun aMalformedFileIsRejectedAndNothingIsStored() = runBlocking {
        file.writeText("this is not a workflow file")

        val preview = service.preview(file.toUri())

        assertTrue(preview.isFailure)
        assertTrue(repository.getAll().isEmpty())
    }

    @Test
    fun aWorkflowWithValidationErrorsCannotBeImported() = runBlocking {
        file.createNewFile()
        val uri = file.toUri()
        // No actions at all — the validator rejects it.
        service.export(uri, listOf(automation().copy(actions = emptyList())))

        val preview = service.preview(uri).getOrThrow()
        val candidate = preview.candidates.single()

        assertFalse(candidate.isImportable)
        assertEquals(0, service.import(preview.candidates))
        assertTrue(repository.getAll().isEmpty())
    }
}
