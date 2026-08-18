package com.dhruw.autoflow.services.files

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import java.io.FileNotFoundException
import java.io.InputStream

/** SAF-backed [FileAccess]. Works only with URIs the user granted access to. */
class SafFileAccess(context: Context) : FileAccess {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    override fun listFolder(folderUri: String): List<FileInfo> {
        val tree = DocumentFile.fromTreeUri(appContext, Uri.parse(folderUri))
            ?: throw FileAccessException("Folder is no longer accessible")
        if (!tree.canRead()) throw FileAccessException("Permission to the folder was revoked")
        return try {
            tree.listFiles()
                .filter { it.isFile }
                .map { doc ->
                    FileInfo(
                        uri = doc.uri.toString(),
                        name = doc.name ?: "unknown",
                        sizeBytes = doc.length(),
                        lastModified = doc.lastModified()
                    )
                }
        } catch (e: SecurityException) {
            throw FileAccessException("Permission to the folder was revoked", e)
        }
    }

    override fun openInputStream(fileUri: String): InputStream = try {
        resolver.openInputStream(Uri.parse(fileUri))
            ?: throw FileAccessException("File is no longer accessible")
    } catch (e: FileNotFoundException) {
        throw FileAccessException("File no longer exists", e)
    } catch (e: SecurityException) {
        throw FileAccessException("Permission to the file was revoked", e)
    }

    override fun copyTo(fileUri: String, fileName: String, destFolderUri: String): String {
        val destTree = DocumentFile.fromTreeUri(appContext, Uri.parse(destFolderUri))
            ?: throw FileAccessException("Destination folder is no longer accessible")
        if (!destTree.canWrite()) {
            throw FileAccessException("No permission to write into the destination folder")
        }
        val finalName = dedupeName(destTree, fileName)
        val target = destTree.createFile(mimeFor(fileName), finalName)
            ?: throw FileAccessException("Could not create the destination file")
        try {
            openInputStream(fileUri).use { input ->
                (resolver.openOutputStream(target.uri)
                    ?: throw FileAccessException("Could not open the destination file")).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            }
        } catch (e: Exception) {
            target.delete()
            if (e is FileAccessException) throw e
            throw FileAccessException("Copy failed: ${e.message ?: "storage error"}", e)
        }
        return finalName
    }

    override fun moveTo(fileUri: String, fileName: String, destFolderUri: String): String {
        val finalName = copyTo(fileUri, fileName, destFolderUri)
        try {
            DocumentsContract.deleteDocument(resolver, Uri.parse(fileUri))
        } catch (e: Exception) {
            throw FileAccessException(
                "Copied to destination but could not remove the original file", e
            )
        }
        return finalName
    }

    override fun rename(fileUri: String, newName: String): String {
        val cleaned = newName.trim()
        if (cleaned.isEmpty()) throw FileAccessException("New file name is empty")
        return try {
            val renamed = DocumentsContract.renameDocument(resolver, Uri.parse(fileUri), cleaned)
                ?: throw FileAccessException("Rename was rejected by the storage provider")
            DocumentFile.fromSingleUri(appContext, renamed)?.name ?: cleaned
        } catch (e: FileAccessException) {
            throw e
        } catch (e: FileNotFoundException) {
            throw FileAccessException("File no longer exists", e)
        } catch (e: Exception) {
            throw FileAccessException("Rename failed: ${e.message ?: "storage error"}", e)
        }
    }

    /** "export.zip" → "export (1).zip" while the plain name is taken. */
    private fun dedupeName(folder: DocumentFile, name: String): String {
        val taken = folder.listFiles().mapNotNull { it.name }.toHashSet()
        if (name !in taken) return name
        val stem = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        for (i in 1..500) {
            val candidate = if (ext.isEmpty()) "$stem ($i)" else "$stem ($i).$ext"
            if (candidate !in taken) return candidate
        }
        throw FileAccessException("Too many files with the same name in the destination")
    }

    private fun mimeFor(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "application/octet-stream"
    }
}
