package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.TriggerPayload

/**
 * Suppresses repeated deliveries of the same logical notification. Android
 * re-posts a notification whenever the owning app updates it (progress,
 * icon, re-sort), which would otherwise re-run automations.
 *
 * A notification is "the same" when its stable key AND its content hashes
 * are unchanged; an update that changes the visible text (a genuinely new
 * message in the same notification slot) is treated as a new event. Content
 * is fingerprinted by hash, so no notification text is retained here.
 *
 * The cache is deliberately in-memory and bounded: at most [maxEntries]
 * fingerprints, each valid for [windowMillis]. If the process is recreated
 * the cache starts empty — worst case one extra run for a re-posted
 * notification, which is acceptable and avoids persisting anything.
 *
 * Known limitation: apps that re-post identical text under a fresh
 * notification key (some chat apps do on repeated identical messages)
 * cannot be distinguished from new messages; those fire again by design.
 */
class NotificationDeduplicator(
    private val maxEntries: Int = 128,
    private val windowMillis: Long = 60_000,
    private val clock: () -> Long = System::currentTimeMillis
) {

    /** fingerprint → last time it was accepted. Insertion-ordered for LRU-style trimming. */
    private val seen = LinkedHashMap<String, Long>()

    /** Returns true exactly once per distinct notification within the window. */
    @Synchronized
    fun shouldProcess(event: TriggerPayload.NotificationEvent): Boolean {
        val now = clock()
        val fingerprint = fingerprint(event)
        val acceptedAt = seen[fingerprint]
        if (acceptedAt != null && now - acceptedAt <= windowMillis) return false

        // Re-insert so the entry moves to the end (newest) position.
        seen.remove(fingerprint)
        seen[fingerprint] = now
        val iterator = seen.entries.iterator()
        while (seen.size > maxEntries && iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
        return true
    }

    private fun fingerprint(event: TriggerPayload.NotificationEvent): String =
        "${event.packageName}|${event.notificationKey}|" +
            "${event.title.hashCode()}|${event.text.hashCode()}"
}
