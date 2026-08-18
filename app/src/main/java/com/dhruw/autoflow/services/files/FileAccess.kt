package com.dhruw.autoflow.services.files

import java.io.InputStream

/** A file listed from user-granted storage. [uri] is opaque (SAF document URI). */
data class FileInfo(
    val uri: String,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long
) {
    val extension: String
        get() = name.substringAfterLast('.', "").lowercase()
}

class FileAccessException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Storage abstraction over Android's Storage Access Framework. Everything
 * operates on user-granted tree/document URIs — no broad filesystem
 * permissions, no raw paths. All methods throw [FileAccessException] with
 * a human-readable message on failure.
 */
interface FileAccess {

    /** Lists files (not directories) in a user-granted folder. */
    fun listFolder(folderUri: String): List<FileInfo>

    fun openInputStream(fileUri: String): InputStream

    /** Copies into [destFolderUri]; dedupes the name if taken. Returns new file name. */
    fun copyTo(fileUri: String, fileName: String, destFolderUri: String): String

    /** Copy + delete original. Returns new file name. */
    fun moveTo(fileUri: String, fileName: String, destFolderUri: String): String

    /** Renames in place; dedupes if taken. Returns the final name. */
    fun rename(fileUri: String, newName: String): String
}
