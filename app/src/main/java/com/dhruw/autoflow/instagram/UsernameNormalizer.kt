package com.dhruw.autoflow.instagram

import com.dhruw.autoflow.instagram.model.InstagramAccount
import java.util.Locale

/**
 * Username hygiene in one place. Display values stay as close to the export
 * as possible (only stripped of "@" and whitespace); comparisons always use
 * the lowercase [normalizeKey] form because Instagram usernames are
 * case-insensitive.
 */
object UsernameNormalizer {

    private const val MAX_LENGTH = 64

    /** Cleaned display form, or null when the value is unusable. */
    fun display(raw: String?): String? {
        val cleaned = raw?.trim()?.removePrefix("@")?.trim() ?: return null
        if (cleaned.isEmpty() || cleaned.length > MAX_LENGTH) return null
        if (cleaned.any { it.isWhitespace() }) return null
        return cleaned
    }

    /** Lowercase comparison key, or null when the value is unusable. */
    fun normalizeKey(raw: String?): String? = display(raw)?.lowercase(Locale.ROOT)

    /** Drops unusable entries and collapses duplicates (first occurrence wins). */
    fun dedupe(accounts: List<InstagramAccount>): List<InstagramAccount> {
        val seen = HashSet<String>(accounts.size)
        val result = ArrayList<InstagramAccount>(accounts.size)
        for (account in accounts) {
            val display = display(account.username) ?: continue
            val key = display.lowercase(Locale.ROOT)
            if (seen.add(key)) {
                result += account.copy(username = display)
            }
        }
        return result
    }
}
