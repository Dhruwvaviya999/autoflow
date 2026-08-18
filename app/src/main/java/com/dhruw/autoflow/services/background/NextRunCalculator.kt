package com.dhruw.autoflow.services.background

import java.time.Instant
import java.time.ZoneId

/**
 * Pure time math for the scheduler, extracted so it is unit-testable without
 * WorkManager. A TimeTrigger carries only hour/minute, so "next occurrence"
 * means today if that time is still ahead, otherwise tomorrow.
 */
object NextRunCalculator {

    fun delayUntilNextOccurrenceMillis(
        hour: Int,
        minute: Int,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return next.toInstant().toEpochMilli() - nowMillis
    }
}
