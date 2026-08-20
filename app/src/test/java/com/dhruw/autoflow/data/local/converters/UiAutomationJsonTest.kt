package com.dhruw.autoflow.data.local.converters

import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.ScrollDirection
import com.dhruw.autoflow.automation.model.TextMatchMode
import com.dhruw.autoflow.automation.model.UiSelector
import com.dhruw.autoflow.automation.model.UiStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UiAutomationJsonTest {

    @Test
    fun `ui automation action with every step type round-trips`() {
        val action = Action.UiAutomationAction(
            targetPackage = "com.example.app",
            targetLabel = "Example",
            overallTimeoutMillis = 90_000,
            steps = listOf(
                UiStep.LaunchApp("com.example.app", "Example"),
                UiStep.Wait(1_500),
                UiStep.WaitForElement(
                    UiSelector(text = "Search", matchMode = TextMatchMode.CONTAINS),
                    timeoutMillis = 12_000
                ),
                UiStep.ClickElement(
                    UiSelector(
                        viewId = "com.example:id/search",
                        className = "android.widget.Button"
                    )
                ),
                UiStep.LongClickElement(UiSelector(contentDescription = "Item", occurrence = 2)),
                UiStep.SetText(
                    UiSelector(viewId = "input"),
                    "Hello {{notification.title}}"
                ),
                UiStep.Scroll(UiSelector(className = "androidx.recyclerview.widget.RecyclerView"), ScrollDirection.BACKWARD),
                UiStep.GlobalBack,
                UiStep.RequireUserConfirmation("Send this message?", "Tap \"Send\"")
            )
        )

        val decoded = WorkflowJson.decodeActions(WorkflowJson.encodeActions(listOf(action)))

        assertEquals(listOf<Action>(action), decoded)
    }

    @Test
    fun `defaults survive minimal ui automation action`() {
        val action = Action.UiAutomationAction(
            targetPackage = "com.example.app",
            steps = listOf(UiStep.GlobalBack)
        )
        val decoded = WorkflowJson.decodeActions(WorkflowJson.encodeActions(listOf(action)))
        val restored = decoded.single() as Action.UiAutomationAction
        assertEquals(60_000, restored.overallTimeoutMillis)
        assertEquals("", restored.targetLabel)
    }

    @Test
    fun `selector without occurrence stays null`() {
        val action = Action.UiAutomationAction(
            targetPackage = "com.example.app",
            steps = listOf(UiStep.ClickElement(UiSelector(text = "Send")))
        )
        val decoded = WorkflowJson.decodeActions(WorkflowJson.encodeActions(listOf(action)))
        val step = (decoded.single() as Action.UiAutomationAction).steps.single()
        assertEquals(null, (step as UiStep.ClickElement).selector.occurrence)
    }

    @Test
    fun `unknown ui step type throws WorkflowJsonException`() {
        val raw = """
            [{"type":"ui_automation","targetPackage":"com.example.app",
              "steps":[{"type":"teleport"}]}]
        """.trimIndent()
        assertThrows(WorkflowJsonException::class.java) {
            WorkflowJson.decodeActions(raw)
        }
    }

    @Test
    fun `ui automation composes with other actions in one list`() {
        val actions = listOf(
            Action.DelayAction(1_000),
            Action.UiAutomationAction(
                targetPackage = "com.example.app",
                steps = listOf(UiStep.ClickElement(UiSelector(text = "Demo")))
            ),
            Action.ShowNotificationAction("Done", "Workflow finished")
        )
        assertEquals(actions, WorkflowJson.decodeActions(WorkflowJson.encodeActions(actions)))
    }
}
