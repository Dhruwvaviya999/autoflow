package com.dhruw.autoflow.automation.processor

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ZipArchiveReaderTest {

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

    @Test
    fun `reads matching entries and skips others`() {
        val bytes = zipOf(
            "connections/followers_1.json" to "[1]",
            "media/photo.jpg" to "not json",
            "connections/following.json" to "[2]"
        )
        val entries = ZipArchiveReader.readMatching({ ByteArrayInputStream(bytes) }) {
            it.endsWith(".json")
        }
        assertEquals(
            listOf("connections/followers_1.json", "connections/following.json"),
            entries.map { it.name }
        )
        assertEquals("[1]", entries[0].bytes.decodeToString())
    }

    @Test
    fun `malformed bytes throw ZipReadException`() {
        val garbage = ByteArray(128) { (it * 7).toByte() }
        assertThrows(ZipReadException::class.java) {
            ZipArchiveReader.readMatching({ ByteArrayInputStream(garbage) }) { true }
        }
    }

    @Test
    fun `empty zip yields no entries`() {
        val bytes = zipOf()
        val entries = ZipArchiveReader.readMatching({ ByteArrayInputStream(bytes) }) { true }
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `unsafe entry names are never extracted`() {
        val bytes = zipOf(
            "../../evil.json" to "evil",
            "/absolute.json" to "evil",
            "safe/inner/../fine.json" to "evil", // contains .. segment → rejected
            "good.json" to "good"
        )
        val entries = ZipArchiveReader.readMatching({ ByteArrayInputStream(bytes) }) { true }
        assertEquals(listOf("good.json"), entries.map { it.name })
    }

    @Test
    fun `unsafe name detection covers traversal variants`() {
        assertFalse(ZipArchiveReader.isSafeEntryName("../x"))
        assertFalse(ZipArchiveReader.isSafeEntryName("a/../x"))
        assertFalse(ZipArchiveReader.isSafeEntryName("/etc/passwd"))
        assertFalse(ZipArchiveReader.isSafeEntryName("\\windows\\evil"))
        assertFalse(ZipArchiveReader.isSafeEntryName("C:/evil"))
        assertFalse(ZipArchiveReader.isSafeEntryName(""))
        assertTrue(ZipArchiveReader.isSafeEntryName("connections/followers_1.json"))
        assertTrue(ZipArchiveReader.isSafeEntryName("weird..name.json"))
    }
}
