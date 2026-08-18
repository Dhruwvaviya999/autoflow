package com.dhruw.autoflow.services.background

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class NextRunCalculatorTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    private fun at(hour: Int, minute: Int, second: Int = 0): Long =
        ZonedDateTime.of(2026, 8, 18, hour, minute, second, 0, zone)
            .toInstant()
            .toEpochMilli()

    @Test
    fun `time later today schedules for today`() {
        val now = at(10, 0)
        val delay = NextRunCalculator.delayUntilNextOccurrenceMillis(18, 0, now, zone)
        assertEquals(8L * 60 * 60 * 1000, delay)
    }

    @Test
    fun `time already passed schedules for tomorrow`() {
        val now = at(19, 0)
        val delay = NextRunCalculator.delayUntilNextOccurrenceMillis(18, 0, now, zone)
        assertEquals(23L * 60 * 60 * 1000, delay)
    }

    @Test
    fun `exact current minute schedules for tomorrow, never zero`() {
        val now = at(18, 0)
        val delay = NextRunCalculator.delayUntilNextOccurrenceMillis(18, 0, now, zone)
        assertEquals(24L * 60 * 60 * 1000, delay)
    }

    @Test
    fun `seconds within the current minute are respected`() {
        val now = at(17, 59, 30)
        val delay = NextRunCalculator.delayUntilNextOccurrenceMillis(18, 0, now, zone)
        assertEquals(30_000L, delay)
    }

    @Test
    fun `midnight trigger from just before midnight`() {
        val now = at(23, 59)
        val delay = NextRunCalculator.delayUntilNextOccurrenceMillis(0, 0, now, zone)
        assertEquals(60_000L, delay)
    }
}
