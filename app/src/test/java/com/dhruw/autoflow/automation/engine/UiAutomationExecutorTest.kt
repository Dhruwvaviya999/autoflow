package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.ScrollDirection
import com.dhruw.autoflow.automation.model.TriggerPayload
import com.dhruw.autoflow.automation.model.UiSelector
import com.dhruw.autoflow.automation.model.UiStep
import com.dhruw.autoflow.automation.model.UiStepResult
import com.dhruw.autoflow.automation.model.UiStepStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UiAutomationExecutorTest {

    private val target = "com.example.app"

    private fun kotlinx.coroutines.test.TestScope.executor(
        host: FakeUiAutomationHost
    ) = UiAutomationExecutor(
        host = host,
        clock = { testScheduler.currentTime }
    )

    private suspend fun kotlinx.coroutines.test.TestScope.run(
        host: FakeUiAutomationHost,
        steps: List<UiStep>,
        payload: TriggerPayload? = null,
        timeoutMillis: Long = 60_000,
        onStep: (UiStepResult) -> Unit = {}
    ) = executor(host).run(target, steps, payload, timeoutMillis, onStep = onStep)

    // --- Happy path ---

    @Test
    fun `full workflow succeeds and reports every step`() = runTest {
        val button = FakeUiNode(text = "Demo", isClickable = true)
        val input = FakeUiNode(viewId = "app:id/input", isEditable = true)
        val host = FakeUiAutomationHost(
            root = FakeUiNode(childNodes = listOf(button, input)),
            currentPkg = target
        )
        val results = mutableListOf<UiStepResult>()

        val outcome = run(
            host,
            listOf(
                UiStep.LaunchApp(target, "Example"),
                UiStep.WaitForElement(UiSelector(text = "Demo")),
                UiStep.ClickElement(UiSelector(text = "Demo")),
                UiStep.SetText(UiSelector(viewId = "input"), "Hello from AutoFlow"),
                UiStep.RequireUserConfirmation("Continue?"),
                UiStep.GlobalBack
            ),
            onStep = { results += it }
        )

        assertTrue(outcome.success)
        assertEquals(6, results.size)
        assertTrue(results.all { it.status == UiStepStatus.SUCCESS })
        assertEquals(1, button.clickCount)
        assertEquals("Hello from AutoFlow", input.enteredText)
        assertEquals(listOf(target), host.launchedPackages)
        assertEquals(1, host.backCount)
    }

    @Test
    fun `set text detail never contains the entered value`() = runTest {
        val input = FakeUiNode(viewId = "app:id/input", isEditable = true)
        val host = FakeUiAutomationHost(root = FakeUiNode(childNodes = listOf(input)))
        val outcome = run(
            host,
            listOf(UiStep.SetText(UiSelector(viewId = "input"), "my secret message"))
        )
        assertTrue(outcome.success)
        assertFalse(outcome.results[0].detail.contains("secret"))
    }

    // --- Click behavior ---

    @Test
    fun `click walks up to nearest clickable ancestor`() = runTest {
        val label = FakeUiNode(text = "Send")
        val clickableParent = FakeUiNode(isClickable = true, childNodes = listOf(label))
        val host = FakeUiAutomationHost(root = FakeUiNode(childNodes = listOf(clickableParent)))

        val outcome = run(host, listOf(UiStep.ClickElement(UiSelector(text = "Send"))))

        assertTrue(outcome.success)
        assertEquals(1, clickableParent.clickCount)
        assertEquals(0, label.clickCount)
    }

    @Test
    fun `click fails when nothing clickable is nearby`() = runTest {
        val host = FakeUiAutomationHost(root = FakeUiNode(childNodes = listOf(FakeUiNode(text = "Send"))))
        val outcome = run(host, listOf(UiStep.ClickElement(UiSelector(text = "Send"))))
        assertFalse(outcome.success)
        assertEquals(UiStepStatus.FAILED, outcome.results[0].status)
        assertTrue(outcome.results[0].detail.contains("clickable"))
    }

    @Test
    fun `ambiguous selector fails instead of picking a node`() = runTest {
        val first = FakeUiNode(text = "Send", isClickable = true)
        val second = FakeUiNode(text = "Send", isClickable = true)
        val host = FakeUiAutomationHost(root = FakeUiNode(childNodes = listOf(first, second)))

        val outcome = run(host, listOf(UiStep.ClickElement(UiSelector(text = "Send"))))

        assertFalse(outcome.success)
        assertTrue(outcome.results[0].detail.contains("2 elements match"))
        assertEquals(0, first.clickCount)
        assertEquals(0, second.clickCount)
    }

    @Test
    fun `missing element fails with structured result`() = runTest {
        val host = FakeUiAutomationHost(root = FakeUiNode())
        val outcome = run(host, listOf(UiStep.ClickElement(UiSelector(text = "Ghost"))))
        assertFalse(outcome.success)
        assertEquals(UiStepStatus.FAILED, outcome.results[0].status)
    }

    @Test
    fun `long click fails cleanly when unsupported`() = runTest {
        val node = FakeUiNode(text = "Item", isClickable = true) // not long-clickable
        val host = FakeUiAutomationHost(root = FakeUiNode(childNodes = listOf(node)))
        val outcome = run(host, listOf(UiStep.LongClickElement(UiSelector(text = "Item"))))
        assertFalse(outcome.success)
        assertTrue(outcome.results[0].detail.contains("Long press"))
    }

    // --- SetText safety ---

    @Test
    fun `set text refuses password fields`() = runTest {
        val password = FakeUiNode(viewId = "app:id/password", isEditable = true, isPassword = true)
        val host = FakeUiAutomationHost(root = FakeUiNode(childNodes = listOf(password)))

        val outcome = run(
            host,
            listOf(UiStep.SetText(UiSelector(viewId = "password"), "hunter2"))
        )

        assertFalse(outcome.success)
        assertEquals(UiStepStatus.FAILED, outcome.results[0].status)
        assertTrue(outcome.results[0].detail.contains("password"))
        assertNull(password.enteredText)
    }

    @Test
    fun `set text fails on non-editable nodes`() = runTest {
        val label = FakeUiNode(text = "Just a label")
        val host = FakeUiAutomationHost(root = FakeUiNode(childNodes = listOf(label)))
        val outcome = run(host, listOf(UiStep.SetText(UiSelector(text = "Just a label"), "x")))
        assertFalse(outcome.success)
        assertTrue(outcome.results[0].detail.contains("not a text field"))
    }

    @Test
    fun `set text resolves template from notification payload`() = runTest {
        val input = FakeUiNode(viewId = "app:id/input", isEditable = true)
        val host = FakeUiAutomationHost(root = FakeUiNode(childNodes = listOf(input)))
        val payload = TriggerPayload.NotificationEvent(
            packageName = "com.other",
            appName = "Other",
            title = "job opening",
            text = "come work with us",
            subText = "",
            timestamp = 1L,
            notificationKey = "k",
            category = ""
        )

        val outcome = run(
            host,
            listOf(UiStep.SetText(UiSelector(viewId = "input"), "About: {{notification.title}}")),
            payload = payload
        )

        assertTrue(outcome.success)
        assertEquals("About: job opening", input.enteredText)
    }

    @Test
    fun `set text with unavailable variable fails with clear detail`() = runTest {
        val input = FakeUiNode(viewId = "app:id/input", isEditable = true)
        val host = FakeUiAutomationHost(root = FakeUiNode(childNodes = listOf(input)))
        val outcome = run(
            host,
            listOf(UiStep.SetText(UiSelector(viewId = "input"), "{{notification.title}}")),
            payload = null
        )
        assertFalse(outcome.success)
        assertTrue(outcome.results[0].detail.contains("not available"))
    }

    // --- Waits and timeouts ---

    @Test
    fun `wait for element succeeds when it appears later`() = runTest {
        val host = FakeUiAutomationHost(currentPkg = target)
        val appearAt = testScheduler.currentTime + 2_000
        host.rootProvider = {
            if (testScheduler.currentTime >= appearAt) {
                FakeUiNode(childNodes = listOf(FakeUiNode(text = "Search")))
            } else {
                FakeUiNode()
            }
        }

        val outcome = run(
            host,
            listOf(UiStep.WaitForElement(UiSelector(text = "Search"), timeoutMillis = 10_000))
        )

        assertTrue(outcome.success)
    }

    @Test
    fun `wait for element times out with TIMEOUT status`() = runTest {
        val host = FakeUiAutomationHost(root = FakeUiNode(), currentPkg = target)
        val outcome = run(
            host,
            listOf(UiStep.WaitForElement(UiSelector(text = "Never"), timeoutMillis = 3_000))
        )
        assertFalse(outcome.success)
        assertEquals(UiStepStatus.TIMEOUT, outcome.results[0].status)
    }

    @Test
    fun `overall timeout stops a long wait`() = runTest {
        val host = FakeUiAutomationHost(root = FakeUiNode())
        val outcome = run(
            host,
            listOf(UiStep.Wait(90_000)),
            timeoutMillis = 5_000
        )
        assertFalse(outcome.success)
        assertEquals(UiStepStatus.TIMEOUT, outcome.results[0].status)
    }

    @Test
    fun `overall timeout stops before starting further steps`() = runTest {
        val button = FakeUiNode(text = "Demo", isClickable = true)
        val host = FakeUiAutomationHost(root = FakeUiNode(childNodes = listOf(button)))
        val outcome = run(
            host,
            listOf(
                UiStep.Wait(10_000),
                UiStep.Wait(10_000),
                UiStep.ClickElement(UiSelector(text = "Demo"))
            ),
            timeoutMillis = 15_000
        )
        assertFalse(outcome.success)
        assertEquals(UiStepStatus.TIMEOUT, outcome.results.last().status)
        assertEquals(0, button.clickCount)
    }

    @Test
    fun `confirmation wait does not consume the workflow budget`() = runTest {
        val host = FakeUiAutomationHost(root = FakeUiNode())
        host.confirmationHandler = { _, _ ->
            delay(300_000) // the user thinks for 5 minutes
            true
        }
        val outcome = run(
            host,
            listOf(
                UiStep.RequireUserConfirmation("Continue?"),
                UiStep.Wait(2_000)
            ),
            timeoutMillis = 10_000
        )
        assertTrue(outcome.success)
    }

    // --- Confirmation ---

    @Test
    fun `declined confirmation cancels the run`() = runTest {
        val button = FakeUiNode(text = "Send", isClickable = true)
        val host = FakeUiAutomationHost(
            root = FakeUiNode(childNodes = listOf(button)),
            confirmationAnswer = false
        )
        val outcome = run(
            host,
            listOf(
                UiStep.RequireUserConfirmation("Send this?", "Tap Send"),
                UiStep.ClickElement(UiSelector(text = "Send"))
            )
        )
        assertFalse(outcome.success)
        assertTrue(outcome.cancelled)
        assertEquals(UiStepStatus.CANCELLED, outcome.results[0].status)
        assertEquals(0, button.clickCount)
        assertEquals("Send this?" to "Tap Send", host.confirmationPrompts.single())
    }

    // --- Safety guards ---

    @Test
    fun `locked device cancels immediately`() = runTest {
        val host = FakeUiAutomationHost(root = FakeUiNode(), locked = true)
        val outcome = run(host, listOf(UiStep.GlobalBack))
        assertFalse(outcome.success)
        assertTrue(outcome.cancelled)
        assertTrue(outcome.results[0].detail.contains("locked"))
        assertEquals(0, host.backCount)
    }

    @Test
    fun `package drift after launch stops element steps`() = runTest {
        val button = FakeUiNode(text = "Demo", isClickable = true)
        val host = FakeUiAutomationHost(root = FakeUiNode(childNodes = listOf(button)))
        host.launchSetsPackageTo = "com.other.app" // wrong app came up

        val outcome = run(
            host,
            listOf(
                UiStep.LaunchApp(target),
                UiStep.ClickElement(UiSelector(text = "Demo"))
            )
        )

        assertFalse(outcome.success)
        assertTrue(outcome.results[1].detail.contains("com.other.app"))
        assertEquals(0, button.clickCount)
    }

    @Test
    fun `unreadable screen fails element steps with clear detail`() = runTest {
        val host = FakeUiAutomationHost(root = null)
        val outcome = run(host, listOf(UiStep.ClickElement(UiSelector(text = "Demo"))))
        assertFalse(outcome.success)
        assertTrue(outcome.results[0].detail.contains("not readable"))
    }

    // --- Launch / scroll / back failures ---

    @Test
    fun `failed launch produces structured failure`() = runTest {
        val host = FakeUiAutomationHost(launchResult = false)
        val outcome = run(host, listOf(UiStep.LaunchApp("com.missing.app")))
        assertFalse(outcome.success)
        assertTrue(outcome.results[0].detail.contains("launch"))
    }

    @Test
    fun `scroll fails cleanly when unsupported`() = runTest {
        val list = FakeUiNode(viewId = "app:id/list", isScrollable = true, scrollResult = false)
        val host = FakeUiAutomationHost(root = FakeUiNode(childNodes = listOf(list)))
        val outcome = run(
            host,
            listOf(UiStep.Scroll(UiSelector(viewId = "list"), ScrollDirection.FORWARD))
        )
        assertFalse(outcome.success)
        assertTrue(outcome.results[0].detail.contains("Scroll"))
    }

    @Test
    fun `scroll backward uses the backward action`() = runTest {
        val list = FakeUiNode(viewId = "app:id/list", isScrollable = true)
        val host = FakeUiAutomationHost(root = FakeUiNode(childNodes = listOf(list)))
        val outcome = run(
            host,
            listOf(UiStep.Scroll(UiSelector(viewId = "list"), ScrollDirection.BACKWARD))
        )
        assertTrue(outcome.success)
        assertTrue(list.scrolledBackward)
        assertFalse(list.scrolledForward)
    }

    @Test
    fun `failed global back stops the run`() = runTest {
        val host = FakeUiAutomationHost(backResult = false)
        val outcome = run(host, listOf(UiStep.GlobalBack))
        assertFalse(outcome.success)
    }

    @Test
    fun `failure stops subsequent steps`() = runTest {
        val host = FakeUiAutomationHost(root = FakeUiNode(), backResult = true)
        val outcome = run(
            host,
            listOf(
                UiStep.ClickElement(UiSelector(text = "Ghost")),
                UiStep.GlobalBack
            )
        )
        assertFalse(outcome.success)
        assertEquals(1, outcome.results.size)
        assertEquals(0, host.backCount)
    }

    // --- Cancellation ---

    @Test
    fun `cancelling the coroutine stops a running wait`() = runTest {
        val host = FakeUiAutomationHost(root = FakeUiNode())
        var completed = false
        val job = launch {
            executor(host).run(target, listOf(UiStep.Wait(60_000)), null, 120_000)
            completed = true
        }
        delay(1_000)
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(completed)
    }
}
