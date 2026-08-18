package com.dhruw.autoflow.instagram

import com.dhruw.autoflow.instagram.model.InstagramAccount
import com.dhruw.autoflow.instagram.model.InstagramFollowData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramFollowAnalyzerTest {

    private val analyzer = InstagramFollowAnalyzer(clock = { 0L })

    private fun data(following: List<String>, followers: List<String>) = InstagramFollowData(
        followers = followers.map { InstagramAccount(it) },
        following = following.map { InstagramAccount(it) },
        source = "test.zip"
    )

    @Test
    fun `following minus followers finds who does not follow back`() {
        val result = analyzer.analyze(
            data(following = listOf("A", "B", "C", "D"), followers = listOf("A", "C"))
        )
        assertEquals(listOf("B", "D"), result.notFollowingBack.map { it.username })
        assertEquals(2, result.notFollowingBackCount)
        assertEquals(2, result.mutualCount)
        assertEquals(0, result.followersOnlyCount)
        assertEquals(2, result.followersCount)
        assertEquals(4, result.followingCount)
    }

    @Test
    fun `empty following yields empty result`() {
        val result = analyzer.analyze(data(following = emptyList(), followers = listOf("A", "B")))
        assertEquals(0, result.notFollowingBackCount)
        assertEquals(2, result.followersOnlyCount)
    }

    @Test
    fun `empty followers means nobody follows back`() {
        val result = analyzer.analyze(data(following = listOf("A", "B"), followers = emptyList()))
        assertEquals(listOf("A", "B"), result.notFollowingBack.map { it.username })
    }

    @Test
    fun `identical lists yield zero non-followers`() {
        val result = analyzer.analyze(
            data(following = listOf("A", "B"), followers = listOf("b", "a"))
        )
        assertEquals(0, result.notFollowingBackCount)
        assertEquals(2, result.mutualCount)
    }

    @Test
    fun `duplicate usernames produce no duplicate results`() {
        val result = analyzer.analyze(
            data(
                following = listOf("A", "a", "@A", "B", "B"),
                followers = listOf("C")
            )
        )
        assertEquals(listOf("A", "B"), result.notFollowingBack.map { it.username })
        assertEquals(2, result.followingCount)
    }

    @Test
    fun `case differences do not create false positives`() {
        val result = analyzer.analyze(
            data(following = listOf("UserOne"), followers = listOf("userone"))
        )
        assertEquals(0, result.notFollowingBackCount)
    }

    @Test
    fun `large synthetic dataset stays fast and correct`() {
        val following = (0 until 200_000).map { "user_$it" }
        val followers = (0 until 200_000 step 2).map { "user_$it" } // evens follow back
        val start = System.nanoTime()
        val result = analyzer.analyze(data(following = following, followers = followers))
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertEquals(100_000, result.notFollowingBackCount)
        assertEquals(100_000, result.mutualCount)
        assertTrue(result.notFollowingBack.all { it.username.removePrefix("user_").toInt() % 2 == 1 })
        // O(n) sets: generous bound to avoid flaky CI, still far below O(n²) territory.
        assertTrue("comparison took ${elapsedMs}ms", elapsedMs < 10_000)
    }
}
