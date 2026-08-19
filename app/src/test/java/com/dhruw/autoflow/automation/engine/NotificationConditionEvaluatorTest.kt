package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.Condition
import com.dhruw.autoflow.automation.model.TextMatchMode
import com.dhruw.autoflow.automation.model.TriggerPayload
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationConditionEvaluatorTest {

    private val evaluator = ConditionEvaluator()

    private fun event(
        packageName: String = "com.whatsapp",
        title: String = "John",
        text: String = "Hi, there is a job opening in our company.",
        category: String = "msg"
    ) = TriggerPayload.NotificationEvent(
        packageName = packageName,
        appName = "WhatsApp",
        title = title,
        text = text,
        subText = "",
        timestamp = 1_000L,
        notificationKey = "key-1",
        category = category
    )

    // --- App condition ---

    @Test
    fun `app condition passes on matching package`() {
        val condition = Condition.NotificationAppCondition("com.whatsapp")
        assertTrue(evaluator.evaluate(condition, event()))
    }

    @Test
    fun `app condition fails on different package`() {
        val condition = Condition.NotificationAppCondition("com.google.android.gm")
        assertFalse(evaluator.evaluate(condition, event()))
    }

    @Test
    fun `app condition fails without notification payload`() {
        val condition = Condition.NotificationAppCondition("com.whatsapp")
        assertFalse(evaluator.evaluate(condition, null))
        assertFalse(
            evaluator.evaluate(
                condition,
                TriggerPayload.FileEvent("uri", "a.zip", "zip", 1L, 0L)
            )
        )
    }

    // --- Text condition ---

    @Test
    fun `text contains passes and is case-insensitive`() {
        val condition = Condition.NotificationTextCondition("JOB OPENING")
        assertTrue(evaluator.evaluate(condition, event()))
    }

    @Test
    fun `text contains fails on mismatch`() {
        val condition = Condition.NotificationTextCondition("vacancy")
        assertFalse(evaluator.evaluate(condition, event()))
    }

    @Test
    fun `text equals requires the full text`() {
        val condition = Condition.NotificationTextCondition("hello", TextMatchMode.EQUALS)
        assertTrue(evaluator.evaluate(condition, event(text = "Hello")))
        assertFalse(evaluator.evaluate(condition, event(text = "Hello there")))
    }

    @Test
    fun `text startsWith checks the prefix`() {
        val condition = Condition.NotificationTextCondition("hi,", TextMatchMode.STARTS_WITH)
        assertTrue(evaluator.evaluate(condition, event()))
        assertFalse(evaluator.evaluate(condition, event(text = "Say hi, please")))
    }

    @Test
    fun `blank text condition fails rather than matching everything`() {
        val condition = Condition.NotificationTextCondition("   ")
        assertFalse(evaluator.evaluate(condition, event()))
    }

    // --- Title condition ---

    @Test
    fun `title contains passes`() {
        val condition = Condition.NotificationTitleCondition("john")
        assertTrue(evaluator.evaluate(condition, event()))
    }

    @Test
    fun `title condition fails on mismatch`() {
        val condition = Condition.NotificationTitleCondition("recruiter")
        assertFalse(evaluator.evaluate(condition, event()))
    }

    // --- Category condition ---

    @Test
    fun `category condition matches exactly`() {
        assertTrue(evaluator.evaluate(Condition.NotificationCategoryCondition("msg"), event()))
        assertFalse(evaluator.evaluate(Condition.NotificationCategoryCondition("email"), event()))
        assertFalse(
            evaluator.evaluate(
                Condition.NotificationCategoryCondition("msg"),
                event(category = "")
            )
        )
    }

    // --- Composite conditions ---

    @Test
    fun `and condition requires all children`() {
        val condition = Condition.AndCondition(
            listOf(
                Condition.NotificationAppCondition("com.whatsapp"),
                Condition.NotificationTextCondition("job")
            )
        )
        assertTrue(evaluator.evaluate(condition, event()))
        assertFalse(evaluator.evaluate(condition, event(text = "hello")))
        assertFalse(evaluator.evaluate(condition, event(packageName = "other")))
    }

    @Test
    fun `or condition passes when any child passes`() {
        val condition = Condition.OrCondition(
            listOf(
                Condition.NotificationTextCondition("job"),
                Condition.NotificationTextCondition("vacancy"),
                Condition.NotificationTextCondition("opening")
            )
        )
        assertTrue(evaluator.evaluate(condition, event(text = "A vacancy appeared")))
        assertFalse(evaluator.evaluate(condition, event(text = "See you tomorrow")))
    }

    @Test
    fun `not condition inverts its child`() {
        val condition = Condition.NotCondition(Condition.NotificationTextCondition("job"))
        assertFalse(evaluator.evaluate(condition, event()))
        assertTrue(evaluator.evaluate(condition, event(text = "hello")))
    }

    @Test
    fun `nested composition matches the spec example`() {
        // App = WhatsApp AND (text has "job" OR "vacancy" OR "opening")
        val condition = Condition.AndCondition(
            listOf(
                Condition.NotificationAppCondition("com.whatsapp"),
                Condition.OrCondition(
                    listOf(
                        Condition.NotificationTextCondition("job"),
                        Condition.NotificationTextCondition("vacancy"),
                        Condition.NotificationTextCondition("opening")
                    )
                )
            )
        )
        assertTrue(evaluator.evaluate(condition, event(text = "New opening in our team")))
        assertFalse(evaluator.evaluate(condition, event(text = "Dinner tonight?")))
        assertFalse(
            evaluator.evaluate(
                condition,
                event(packageName = "org.telegram.messenger", text = "job for you")
            )
        )
    }

    @Test
    fun `empty and passes while empty or fails`() {
        assertTrue(evaluator.evaluate(Condition.AndCondition(emptyList()), event()))
        assertFalse(evaluator.evaluate(Condition.OrCondition(emptyList()), event()))
    }
}
