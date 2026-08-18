package com.dhruw.autoflow.automation.processor

import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

class ZipReadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Streaming, bounded ZIP reading. Entries are decompressed straight into
 * memory (never extracted to disk), so ZIP path traversal cannot reach the
 * filesystem; hostile entry names are additionally skipped outright. Size
 * and count caps keep ZIP bombs from exhausting memory.
 */
object ZipArchiveReader {

    class ZipEntryData(val name: String, val bytes: ByteArray)

    private const val MAX_ENTRIES = 50_000
    private const val MAX_BYTES_PER_ENTRY = 64L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 256L * 1024 * 1024

    /**
     * Reads every entry whose (safe) name passes [selector] fully into
     * memory. Malformed archives throw [ZipReadException]; unsafe entry
     * names are skipped, never surfaced.
     */
    fun readMatching(
        openStream: () -> InputStream,
        selector: (entryName: String) -> Boolean
    ): List<ZipEntryData> {
        // ZipInputStream silently treats non-ZIP bytes as an empty archive,
        // so validate the "PK" signature up front to fail loudly instead.
        try {
            openStream().use { probe ->
                val header = ByteArray(2)
                var read = 0
                while (read < 2) {
                    val n = probe.read(header, read, 2 - read)
                    if (n == -1) break
                    read += n
                }
                if (read < 2 || header[0] != 'P'.code.toByte() || header[1] != 'K'.code.toByte()) {
                    throw ZipReadException("Not a valid ZIP file")
                }
            }
        } catch (e: IOException) {
            throw ZipReadException("Could not read the ZIP file", e)
        }

        val result = mutableListOf<ZipEntryData>()
        var entryCount = 0
        var totalBytes = 0L
        try {
            ZipInputStream(openStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    if (entryCount > MAX_ENTRIES) {
                        throw ZipReadException("Archive has too many entries")
                    }
                    val name = entry.name
                    if (entry.isDirectory || !isSafeEntryName(name) || !selector(name)) {
                        zip.closeEntry()
                        continue
                    }
                    val bytes = readBounded(zip, MAX_BYTES_PER_ENTRY)
                    totalBytes += bytes.size
                    if (totalBytes > MAX_TOTAL_BYTES) {
                        throw ZipReadException("Archive contents are too large to process")
                    }
                    result += ZipEntryData(name, bytes)
                    zip.closeEntry()
                }
            }
        } catch (e: ZipReadException) {
            throw e
        } catch (e: ZipException) {
            throw ZipReadException("Not a valid ZIP file", e)
        } catch (e: IOException) {
            throw ZipReadException("Could not read the ZIP file", e)
        }
        return result
    }

    /** Rejects traversal ("..") segments, absolute paths, and drive-letter tricks. */
    fun isSafeEntryName(name: String): Boolean {
        if (name.isBlank()) return false
        if (name.startsWith("/") || name.startsWith("\\")) return false
        if (name.length >= 2 && name[1] == ':') return false
        val segments = name.split('/', '\\')
        return segments.none { it == ".." }
    }

    private fun readBounded(stream: InputStream, maxBytes: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = stream.read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) {
                throw ZipReadException("A file inside the archive is too large to process")
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
