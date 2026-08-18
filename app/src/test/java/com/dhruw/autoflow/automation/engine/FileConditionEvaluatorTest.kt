package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.Condition
import com.dhruw.autoflow.automation.model.TriggerPayload
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileConditionEvaluatorTest {

    private val evaluator = ConditionEvaluator()

    private fun file(
        name: String = "instagram_export.zip",
        extension: String = "zip",
        sizeBytes: Long = 5L * 1024 * 1024
    ) = TriggerPayload.FileEvent(
        uri = "content://test/$name",
        name = name,
        extension = extension,
        sizeBytes = sizeBytes,
        detectedAt = 0L
    )

    @Test
    fun `extension matches regardless of dot and case`() {
        val payload = file(extension = "zip")
        assertTrue(evaluator.evaluate(Condition.FileExtensionCondition("zip"), payload))
        assertTrue(evaluator.evaluate(Condition.FileExtensionCondition(".zip"), payload))
        assertTrue(evaluator.evaluate(Condition.FileExtensionCondition(" .ZIP "), payload))
    }

    @Test
    fun `extension mismatch fails`() {
        assertFalse(
            evaluator.evaluate(Condition.FileExtensionCondition("pdf"), file(extension = "zip"))
        )
    }

    @Test
    fun `name contains matches case-insensitively`() {
        val payload = file(name = "My-INSTAGRAM-data.zip")
        assertTrue(evaluator.evaluate(Condition.FileNameContainsCondition("instagram"), payload))
        assertTrue(evaluator.evaluate(Condition.FileNameContainsCondition("  Instagram "), payload))
    }

    @Test
    fun `name contains mismatch fails`() {
        assertFalse(
            evaluator.evaluate(
                Condition.FileNameContainsCondition("facebook"),
                file(name = "instagram.zip")
            )
        )
    }

    @Test
    fun `size conditions compare correctly`() {
        val mb = 1024L * 1024
        val payload = file(sizeBytes = 50 * mb)
        val less100 = Condition.FileSizeCondition(
            Condition.FileSizeCondition.Comparison.LESS_THAN, 100 * mb
        )
        val greater100 = Condition.FileSizeCondition(
            Condition.FileSizeCondition.Comparison.GREATER_THAN, 100 * mb
        )
        val greater10 = Condition.FileSizeCondition(
            Condition.FileSizeCondition.Comparison.GREATER_THAN, 10 * mb
        )
        assertTrue(evaluator.evaluate(less100, payload))
        assertFalse(evaluator.evaluate(greater100, payload))
        assertTrue(evaluator.evaluate(greater10, payload))
    }

    @Test
    fun `file conditions fail without a file payload`() {
        assertFalse(evaluator.evaluate(Condition.FileExtensionCondition("zip"), null))
        assertFalse(evaluator.evaluate(Condition.FileNameContainsCondition("x"), null))
        assertFalse(
            evaluator.evaluate(
                Condition.FileSizeCondition(Condition.FileSizeCondition.Comparison.LESS_THAN, 1),
                null
            )
        )
        // Always still passes without payload.
        assertTrue(evaluator.evaluate(Condition.AlwaysCondition, null))
    }
}
