package com.dhruw.autoflow.core.utils

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val dateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

/** "Today · 12:42 PM", "Yesterday · 9:05 AM", "Aug 18 · 4:00 PM". */
fun formatTimestamp(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val dateTime = Instant.ofEpochMilli(millis).atZone(zone)
    val date = dateTime.toLocalDate()
    val today = LocalDate.now(zone)
    val day = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> dateFormatter.format(date)
    }
    return "$day · ${timeFormatter.format(dateTime)}"
}

/** "0.4 s", "2.0 s", "1 m 12 s". */
fun formatDuration(startMillis: Long, endMillis: Long): String {
    val elapsed = (endMillis - startMillis).coerceAtLeast(0)
    val totalSeconds = elapsed / 1000.0
    return when {
        totalSeconds < 60 -> String.format(Locale.getDefault(), "%.1f s", totalSeconds)
        else -> {
            val minutes = elapsed / 60_000
            val seconds = (elapsed % 60_000) / 1000
            "$minutes m $seconds s"
        }
    }
}

/** Start of the current local day in epoch millis. */
fun startOfTodayMillis(): Long =
    LocalDate.now(ZoneId.systemDefault())
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
