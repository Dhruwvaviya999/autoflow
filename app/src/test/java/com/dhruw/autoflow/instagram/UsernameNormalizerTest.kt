package com.dhruw.autoflow.instagram

import com.dhruw.autoflow.instagram.model.InstagramAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsernameNormalizerTest {

    @Test
    fun `strips leading at sign`() {
        assertEquals("user", UsernameNormalizer.display("@user"))
        assertEquals("user", UsernameNormalizer.normalizeKey("@user"))
    }

    @Test
    fun `plain username passes through`() {
        assertEquals("user", UsernameNormalizer.display("user"))
    }

    @Test
    fun `trims whitespace and lowercases the key but not the display`() {
        assertEquals("USER", UsernameNormalizer.display(" USER "))
        assertEquals("user", UsernameNormalizer.normalizeKey(" USER "))
    }

    @Test
    fun `empty and blank values are rejected`() {
        assertNull(UsernameNormalizer.display(""))
        assertNull(UsernameNormalizer.display("   "))
        assertNull(UsernameNormalizer.display("@"))
        assertNull(UsernameNormalizer.display(null))
    }

    @Test
    fun `values with inner whitespace are rejected`() {
        assertNull(UsernameNormalizer.display("not a username"))
    }

    @Test
    fun `overlong values are rejected`() {
        assertNull(UsernameNormalizer.display("a".repeat(65)))
        assertEquals("a".repeat(64), UsernameNormalizer.display("a".repeat(64)))
    }

    @Test
    fun `dedupe collapses case-variant duplicates keeping the first`() {
        val result = UsernameNormalizer.dedupe(
            listOf(
                InstagramAccount("UserA"),
                InstagramAccount("usera"),
                InstagramAccount("@UserA"),
                InstagramAccount("userb"),
                InstagramAccount("  ")
            )
        )
        assertEquals(listOf("UserA", "userb"), result.map { it.username })
    }
}
