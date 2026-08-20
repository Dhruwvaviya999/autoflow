package com.dhruw.autoflow.services.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dhruw.autoflow.automation.engine.AccessibilityNodeFinder
import com.dhruw.autoflow.automation.engine.UiAutomationExecutor
import com.dhruw.autoflow.automation.engine.UiAutomationHost
import com.dhruw.autoflow.automation.model.ScrollDirection
import com.dhruw.autoflow.automation.model.TextMatchMode
import com.dhruw.autoflow.automation.model.UiNode
import com.dhruw.autoflow.automation.model.UiSelector
import com.dhruw.autoflow.automation.model.UiStep
import com.dhruw.autoflow.automation.model.UiStepStatus
import com.dhruw.autoflow.ui.testlab.AccessibilityTestLabActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device verification of the accessibility engine against the safe
 * internal Test Lab screen. The instrumentation's UiAutomation stands in
 * for AutoFlowAccessibilityService — it exposes the same
 * AccessibilityNodeInfo tree, so [AccessibilityUiNode], the finder and the
 * executor run exactly the production code path (no service enablement is
 * possible from an instrumented test).
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityAutomationTest {

    private lateinit var scenario: ActivityScenario<AccessibilityTestLabActivity>
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetPackage =
        InstrumentationRegistry.getInstrumentation().targetContext.packageName

    /** [UiAutomationHost] backed by the instrumentation's UiAutomation. */
    private inner class InstrumentationHost(
        var confirmationAnswer: Boolean = true
    ) : UiAutomationHost {
        val confirmationPrompts = mutableListOf<String>()

        override fun rootNode(): UiNode? =
            instrumentation.uiAutomation.rootInActiveWindow
                ?.let { AccessibilityUiNode(it, NodeTracker()) }

        override fun currentPackage(): String? =
            instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()

        override fun isDeviceLocked(): Boolean = false

        override suspend fun launchApp(packageName: String): Boolean = false

        override suspend fun globalBack(): Boolean =
            instrumentation.uiAutomation.performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_BACK
            )

        override suspend fun requestConfirmation(
            prompt: String,
            nextActionLabel: String
        ): Boolean {
            confirmationPrompts += prompt
            return confirmationAnswer
        }
    }

    @Before
    fun launchTestLab() {
        // Report view IDs so testTag-based ViewId selectors resolve.
        val automation = instrumentation.uiAutomation
        val info: AccessibilityServiceInfo = automation.serviceInfo ?: AccessibilityServiceInfo()
        info.flags = info.flags or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        automation.serviceInfo = info

        scenario = ActivityScenario.launch(AccessibilityTestLabActivity::class.java)
    }

    @After
    fun close() {
        scenario.close()
    }

    private fun executor(host: UiAutomationHost) = UiAutomationExecutor(host)

    private suspend fun awaitLabReady(host: InstrumentationHost) {
        val outcome = executor(host).run(
            targetPackage,
            listOf(UiStep.WaitForElement(UiSelector(text = "Demo Button"), 10_000)),
            payload = null,
            overallTimeoutMillis = 15_000
        )
        assertTrue("Test Lab did not load: ${outcome.results}", outcome.success)
    }

    private fun statusContains(host: InstrumentationHost, needle: String): Boolean {
        val selector = UiSelector(text = needle, matchMode = TextMatchMode.CONTAINS)
        return AccessibilityNodeFinder().findAll(host.rootNode(), selector).isNotEmpty()
    }

    private suspend fun awaitStatus(host: InstrumentationHost, needle: String) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (statusContains(host, needle)) return
            delay(200)
        }
        assertTrue("Status \"$needle\" not reached", statusContains(host, needle))
    }

    @Test
    fun findsNodesByTextViewIdAndClass() = runBlocking {
        val host = InstrumentationHost()
        awaitLabReady(host)
        val finder = AccessibilityNodeFinder()

        val byText = finder.findOne(host.rootNode(), UiSelector(text = "Demo Button"))
        assertTrue(byText is AccessibilityNodeFinder.Result.Found)

        val byViewId = finder.findOne(host.rootNode(), UiSelector(viewId = "lab_demo_button"))
        assertTrue(
            "ViewId lookup failed: $byViewId",
            byViewId is AccessibilityNodeFinder.Result.Found
        )

        val byClass = finder.findAll(
            host.rootNode(),
            UiSelector(className = "android.widget.EditText")
        )
        assertTrue("Expected editable fields, got ${byClass.size}", byClass.size >= 2)
    }

    @Test
    fun tapsDemoButtonAndSeesResult() = runBlocking {
        val host = InstrumentationHost()
        awaitLabReady(host)

        val outcome = executor(host).run(
            targetPackage,
            listOf(UiStep.ClickElement(UiSelector(text = "Demo Button"))),
            payload = null,
            overallTimeoutMillis = 15_000
        )

        assertTrue("$outcome", outcome.success)
        awaitStatus(host, "Demo tapped")
    }

    @Test
    fun entersTextIntoOrdinaryField() = runBlocking {
        val host = InstrumentationHost()
        awaitLabReady(host)

        val outcome = executor(host).run(
            targetPackage,
            listOf(
                UiStep.SetText(UiSelector(viewId = "lab_text_field"), "Hello from AutoFlow")
            ),
            payload = null,
            overallTimeoutMillis = 15_000
        )

        assertTrue("$outcome", outcome.success)
        // The value must be on screen but never in the step detail.
        awaitStatus(host, "Hello from AutoFlow")
        assertFalse(outcome.results[0].detail.contains("Hello from AutoFlow"))
    }

    @Test
    fun refusesPasswordField() = runBlocking {
        val host = InstrumentationHost()
        awaitLabReady(host)

        val outcome = executor(host).run(
            targetPackage,
            listOf(UiStep.SetText(UiSelector(viewId = "lab_password_field"), "hunter2")),
            payload = null,
            overallTimeoutMillis = 15_000
        )

        assertFalse(outcome.success)
        assertEquals(UiStepStatus.FAILED, outcome.results[0].status)
        assertTrue(
            "Wrong detail: ${outcome.results[0].detail}",
            outcome.results[0].detail.contains("password")
        )
        assertFalse(statusContains(host, "hunter2"))
    }

    @Test
    fun waitForElementSeesDelayedElement() = runBlocking {
        val host = InstrumentationHost()
        awaitLabReady(host)

        val outcome = executor(host).run(
            targetPackage,
            listOf(
                UiStep.ClickElement(UiSelector(text = "Show Delayed Element")),
                UiStep.WaitForElement(UiSelector(text = "Delayed Element"), 10_000)
            ),
            payload = null,
            overallTimeoutMillis = 20_000
        )

        assertTrue("$outcome", outcome.success)
    }

    @Test
    fun waitForElementTimesOutForMissingElement() = runBlocking {
        val host = InstrumentationHost()
        awaitLabReady(host)

        val outcome = executor(host).run(
            targetPackage,
            listOf(UiStep.WaitForElement(UiSelector(text = "No Such Element"), 2_000)),
            payload = null,
            overallTimeoutMillis = 15_000
        )

        assertFalse(outcome.success)
        assertEquals(UiStepStatus.TIMEOUT, outcome.results[0].status)
    }

    @Test
    fun scrollsForwardAndBackward() = runBlocking {
        val host = InstrumentationHost()
        awaitLabReady(host)

        val outcome = executor(host).run(
            targetPackage,
            listOf(
                UiStep.Scroll(UiSelector(viewId = "lab_scroll_area"), ScrollDirection.FORWARD),
                UiStep.Scroll(UiSelector(viewId = "lab_scroll_area"), ScrollDirection.BACKWARD)
            ),
            payload = null,
            overallTimeoutMillis = 15_000
        )

        assertTrue("$outcome", outcome.success)
    }

    @Test
    fun declinedConfirmationStopsBeforeFinalButton() = runBlocking {
        val host = InstrumentationHost(confirmationAnswer = false)
        awaitLabReady(host)

        val outcome = executor(host).run(
            targetPackage,
            listOf(
                UiStep.RequireUserConfirmation("Tap the final button?", "Tap Final Test Button"),
                UiStep.ClickElement(UiSelector(text = "Final Test Button"))
            ),
            payload = null,
            overallTimeoutMillis = 15_000
        )

        assertFalse(outcome.success)
        assertTrue(outcome.cancelled)
        assertEquals(listOf("Tap the final button?"), host.confirmationPrompts)
        assertFalse(statusContains(host, "Final test done"))
    }

    @Test
    fun approvedConfirmationRunsFinalButton() = runBlocking {
        val host = InstrumentationHost(confirmationAnswer = true)
        awaitLabReady(host)

        val outcome = executor(host).run(
            targetPackage,
            listOf(
                UiStep.RequireUserConfirmation("Tap the final button?"),
                UiStep.ClickElement(UiSelector(text = "Final Test Button"))
            ),
            payload = null,
            overallTimeoutMillis = 15_000
        )

        assertTrue("$outcome", outcome.success)
        awaitStatus(host, "Final test done")
    }

    @Test
    fun cancellationStopsARunningWorkflow() = runBlocking {
        val host = InstrumentationHost()
        awaitLabReady(host)

        var finished = false
        val job: Job = launch {
            executor(host).run(
                targetPackage,
                listOf(UiStep.Wait(30_000)),
                payload = null,
                overallTimeoutMillis = 60_000
            )
            finished = true
        }
        delay(500)
        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
        assertFalse(finished)
    }

    @Test
    fun globalBackSucceedsAsAStep() = runBlocking {
        val host = InstrumentationHost()
        awaitLabReady(host)

        val outcome = executor(host).run(
            targetPackage,
            listOf(UiStep.GlobalBack),
            payload = null,
            overallTimeoutMillis = 15_000
        )

        assertTrue("$outcome", outcome.success)
    }
}
