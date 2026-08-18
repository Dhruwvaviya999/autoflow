package com.dhruw.autoflow.instagram

import com.dhruw.autoflow.automation.processor.ProcessingResult
import com.dhruw.autoflow.automation.processor.ProcessorInput
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramDataProcessorTest {

    private val processor = InstagramDataProcessor()

    private fun followerJson(vararg users: String): String =
        users.joinToString(prefix = "[", postfix = "]") { user ->
            """{"title":"","media_list_data":[],"string_list_data":[
                {"href":"https://www.instagram.com/$user","value":"$user","timestamp":1}]}"""
        }

    private fun followingJson(vararg users: String): String =
        """{"relationships_following":${followerJson(*users)}}"""

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun input(name: String, bytes: ByteArray) = ProcessorInput(
        name = name,
        sizeBytes = bytes.size.toLong(),
        openStream = { ByteArrayInputStream(bytes) }
    )

    @Test
    fun `valid json export parses both sides including multi-part followers`() = runTest {
        val zip = zipOf(
            "connections/followers_and_following/followers_1.json" to followerJson("a", "c"),
            "connections/followers_and_following/followers_2.json" to followerJson("e"),
            "connections/followers_and_following/following.json" to followingJson("a", "b", "c", "d")
        )
        val result = processor.process(input("instagram-export.zip", zip))
        result as ProcessingResult.Success
        assertEquals(listOf("a", "c", "e"), result.data.followers.map { it.username })
        assertEquals(listOf("a", "b", "c", "d"), result.data.following.map { it.username })
        assertEquals("https://www.instagram.com/a", result.data.followers.first().profileUrl)
    }

    @Test
    fun `html export parses via profile links`() = runTest {
        val html = { users: List<String> ->
            users.joinToString(
                prefix = "<html><body>",
                postfix = "</body></html>"
            ) { """<a href="https://www.instagram.com/$it/">$it</a>""" }
        }
        val zip = zipOf(
            "connections/followers_and_following/followers_1.html" to html(listOf("a", "c")),
            "connections/followers_and_following/following.html" to html(listOf("a", "b"))
        )
        val result = processor.process(input("export.zip", zip))
        result as ProcessingResult.Success
        assertEquals(listOf("a", "c"), result.data.followers.map { it.username })
        assertEquals(listOf("a", "b"), result.data.following.map { it.username })
    }

    @Test
    fun `missing following file is a clear failure`() = runTest {
        val zip = zipOf("connections/followers_1.json" to followerJson("a"))
        val result = processor.process(input("export.zip", zip))
        result as ProcessingResult.Failure
        assertTrue(result.message.contains("no following data"))
    }

    @Test
    fun `missing followers file is a clear failure`() = runTest {
        val zip = zipOf("connections/following.json" to followingJson("a"))
        val result = processor.process(input("export.zip", zip))
        result as ProcessingResult.Failure
        assertTrue(result.message.contains("no followers data"))
    }

    @Test
    fun `empty export reports missing data without crashing`() = runTest {
        val zip = zipOf("media/nothing.txt" to "hello")
        val result = processor.process(input("export.zip", zip))
        result as ProcessingResult.Failure
        assertTrue(result.message.contains("Could not find followers/following data"))
    }

    @Test
    fun `malformed zip is a structured failure`() = runTest {
        val result = processor.process(input("export.zip", ByteArray(64) { 1 }))
        assertTrue(result is ProcessingResult.Failure)
    }

    @Test
    fun `malformed json inside export does not crash`() = runTest {
        val zip = zipOf(
            "followers_1.json" to "{not valid json",
            "following.json" to followingJson("a")
        )
        val result = processor.process(input("export.zip", zip))
        result as ProcessingResult.Failure
        assertTrue(result.message.contains("no followers data"))
    }

    @Test
    fun `request and unfollowed files are not confused with follower data`() = runTest {
        val zip = zipOf(
            "connections/follow_requests_received.json" to followerJson("spam1"),
            "connections/recently_unfollowed_profiles.json" to followerJson("spam2"),
            "connections/following_hashtags.json" to followingJson("nothashtag"),
            "connections/followers_1.json" to followerJson("a"),
            "connections/following.json" to followingJson("a", "b")
        )
        val result = processor.process(input("export.zip", zip))
        result as ProcessingResult.Success
        assertEquals(listOf("a"), result.data.followers.map { it.username })
        assertEquals(listOf("a", "b"), result.data.following.map { it.username })
    }

    @Test
    fun `unsupported extension is rejected with guidance`() = runTest {
        val result = processor.process(input("export.pdf", ByteArray(4)))
        result as ProcessingResult.Failure
        assertTrue(result.message.contains("Unsupported file type"))
    }
}
