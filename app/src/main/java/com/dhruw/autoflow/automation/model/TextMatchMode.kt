package com.dhruw.autoflow.automation.model

import java.util.Locale

/**
 * How a text filter compares against a notification field. Matching is
 * case-insensitive; the compared value itself is never modified — only
 * lowercased copies are used for the comparison.
 */
enum class TextMatchMode {
    CONTAINS,
    EQUALS,
    STARTS_WITH;

    /** A blank [pattern] matches everything (the filter is "off"). */
    fun matches(value: String, pattern: String): Boolean {
        val needle = pattern.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty()) return true
        val haystack = value.lowercase(Locale.ROOT)
        return when (this) {
            CONTAINS -> haystack.contains(needle)
            EQUALS -> haystack == needle
            STARTS_WITH -> haystack.startsWith(needle)
        }
    }
}

val TextMatchMode.label: String
    get() = when (this) {
        TextMatchMode.CONTAINS -> "Contains"
        TextMatchMode.EQUALS -> "Equals"
        TextMatchMode.STARTS_WITH -> "Starts with"
    }
