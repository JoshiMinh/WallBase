package com.joshiminh.wallbase.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalStorageCoordinator(
    private val context: Context
) {

    private val resolver = context.contentResolver

    private fun baseDirectory(): File = File(context.filesDir, BASE_DIRECTORY_NAME)

    private fun ensureBaseDirectory(): File {
        val base = baseDirectory()
        if (base.exists()) {
            if (!base.isDirectory) {
                throw IOException("WallBase storage path is not a directory")
            }
            return base
        }
        if (!base.mkdirs() && !base.exists()) {
            throw IOException("Unable to create WallBase storage directory")
        }
        return base
    }

    fun currentBaseDirectory(): File? {
        return baseDirectory().takeIf { it.exists() && it.isDirectory }
    }

    fun ensureStorageDirectory(): File = ensureBaseDirectory()

    suspend fun cleanupLegacyEditorCache(): Boolean = withContext(Dispatchers.IO) {
        val base = baseDirectory()
        if (!base.exists() || !base.isDirectory) return@withContext false
        var cleaned = false
        listOf("Editor", "editor", "editor_cache").forEach { folderName ->
            val legacy = File(base, folderName)
            if (legacy.exists()) {
                legacy.deleteRecursively()
                cleaned = true
            }
        }
        cleaned
    }

    suspend fun copyFromUri(
        uri: Uri,
        sourceFolder: String,
        subFolder: String? = null,
        displayName: String? = null,
        mimeTypeHint: String? = null
    ): CopyResult = withContext(Dispatchers.IO) {
        val base = ensureBaseDirectory()
        copyIntoFolder(base, uri, sourceFolder, subFolder, displayName, mimeTypeHint)
    }

    suspend fun writeBytes(
        data: ByteArray,
        sourceFolder: String,
        subFolder: String? = null,
        displayName: String,
        mimeTypeHint: String? = null
    ): CopyResult = withContext(Dispatchers.IO) {
        val destination = prepareDestination(sourceFolder, subFolder, displayName, mimeTypeHint)
        FileOutputStream(destination.file).use { output ->
            output.write(data)
        }
        destination.toCopyResult()
    }

    suspend fun writeStream(
        input: InputStream,
        sourceFolder: String,
        subFolder: String? = null,
        displayName: String,
        mimeTypeHint: String? = null
    ): CopyResult = withContext(Dispatchers.IO) {
        val destination = prepareDestination(sourceFolder, subFolder, displayName, mimeTypeHint)
        input.use { stream ->
            FileOutputStream(destination.file).use { output ->
                stream.copyTo(output)
            }
        }
        destination.toCopyResult()
    }

    fun documentFromTree(uri: Uri): DocumentFile? {
        persistReadPermission(uri)
        return DocumentFile.fromTreeUri(context, uri)
    }

    fun documentFromUri(uri: Uri): DocumentFile? {
        return when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> {
                persistReadPermission(uri)
                DocumentFile.fromSingleUri(context, uri)
            }
            ContentResolver.SCHEME_FILE, null -> {
                val file = runCatching { uri.toFile() }.getOrNull()
                file?.let { DocumentFile.fromFile(it) }
            }
            else -> {
                if (uri.authority == "${context.packageName}.fileprovider") {
                    persistReadPermission(uri)
                    DocumentFile.fromSingleUri(context, uri)
                } else {
                    null
                }
            }
        }
    }

    suspend fun deleteDocument(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> {
                val document = documentFromUri(uri) ?: return@withContext false
                document.delete()
            }
            ContentResolver.SCHEME_FILE, null -> {
                val file = runCatching { uri.toFile() }.getOrNull() ?: return@withContext false
                if (!file.exists()) return@withContext false
                file.delete()
            }
            else -> {
                if (uri.authority == "${context.packageName}.fileprovider") {
                    val document = DocumentFile.fromSingleUri(context, uri) ?: return@withContext false
                    document.delete()
                } else {
                    false
                }
            }
        }
    }

    fun sanitizeFolderName(name: String, fallback: String = DEFAULT_FOLDER_NAME): String {
        val cleaned = name.trim().replace(INVALID_FOLDER_CHARS, "_")
        return cleaned.ifBlank { fallback }
    }

    fun sanitizeFileName(name: String, fallback: String = DEFAULT_FILE_NAME): String {
        val trimmed = name.trim().replace(INVALID_FILE_CHARS, "_")
        return trimmed.ifBlank { fallback }
    }

    fun guessMimeType(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension.isNotBlank()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)?.let { return it }
        }
        return DEFAULT_MIME_TYPE
    }

    private fun copyIntoFolder(
        baseFolder: File,
        uri: Uri,
        sourceFolder: String,
        subFolder: String?,
        displayName: String?,
        mimeTypeHint: String?
    ): CopyResult {
        val targetFolder = ensureTargetFolder(baseFolder, sourceFolder, subFolder)
        val mimeType = mimeTypeHint ?: resolver.getType(uri) ?: DEFAULT_MIME_TYPE
        val display = displayName ?: queryDisplayName(uri) ?: DEFAULT_FILE_NAME
        val sanitized = sanitizeFileName(display, DEFAULT_FILE_NAME)
        val withExtension = ensureExtension(sanitized, mimeType)
        val fileName = ensureUniqueFileName(targetFolder, withExtension)
        val destination = File(targetFolder, fileName)

        persistReadPermission(uri)
        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("Unable to open source stream")

        return CopyResult(
            destination.toUri(),
            destination.name ?: fileName,
            destination.length()
        )
    }

    private fun ensureExtension(name: String, mimeType: String): String {
        if (name.contains('.')) return name
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        return if (!extension.isNullOrBlank()) "$name.$extension" else "$name.$DEFAULT_EXTENSION"
    }

    private fun ensureTargetFolder(
        baseFolder: File,
        sourceFolder: String,
        subFolder: String?
    ): File {
        val source = ensureFolder(baseFolder, sanitizeFolderName(sourceFolder))
        return if (subFolder.isNullOrBlank()) {
            source
        } else {
            ensureFolder(source, sanitizeFolderName(subFolder))
        }
    }

    private fun ensureFolder(parent: File, name: String): File {
        val directory = File(parent, name)
        if (directory.exists()) {
            if (!directory.isDirectory) {
                throw IOException("Unable to create folder $name")
            }
            return directory
        }
        if (!directory.mkdirs() && !directory.exists()) {
            throw IOException("Unable to create folder $name")
        }
        return directory
    }

    private fun ensureUniqueFileName(folder: File, name: String): String {
        var candidate = name
        var counter = 1
        val (base, extension) = splitName(name)
        while (File(folder, candidate).exists()) {
            candidate = if (extension.isBlank()) {
                "$base ($counter)"
            } else {
                "$base ($counter).$extension"
            }
            counter++
        }
        return candidate
    }

    private fun splitName(name: String): Pair<String, String> {
        val index = name.lastIndexOf('.')
        return if (index <= 0 || index == name.lastIndex) {
            name to ""
        } else {
            name.substring(0, index) to name.substring(index + 1)
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        val cursor = resolver.query(uri, projection, null, null, null) ?: return null
        return cursor.use {
            if (!it.moveToFirst()) return null
            val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            val name = if (index >= 0) it.getString(index) else null
            // Fallback to lastPathSegment if DISPLAY_NAME isn't available
            name ?: uri.lastPathSegment
        }
    }

    private fun persistReadPermission(uri: Uri) {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Ignore if we cannot persist; temporary access is sufficient for copying.
        }
    }

    data class CopyResult(
        val uri: Uri,
        val displayName: String,
        val sizeBytes: Long
    )

    private data class Destination(
        val file: File
    ) {
        fun toCopyResult(): CopyResult = CopyResult(
            uri = file.toUri(),
            displayName = file.name,
            sizeBytes = file.length()
        )
    }

    private fun prepareDestination(
        sourceFolder: String,
        subFolder: String?,
        displayName: String,
        mimeTypeHint: String?
    ): Destination {
        val base = ensureBaseDirectory()
        val targetFolder = ensureTargetFolder(base, sourceFolder, subFolder)
        val mimeType = mimeTypeHint ?: guessMimeType(displayName)
        val sanitized = sanitizeFileName(displayName, DEFAULT_FILE_NAME)
        val named = ensureExtension(sanitized, mimeType)
        val fileName = ensureUniqueFileName(targetFolder, named)
        val destination = File(targetFolder, fileName)
        return Destination(destination)
    }

    private companion object {
        private const val BASE_DIRECTORY_NAME = "wallpapers"
        private const val DEFAULT_FOLDER_NAME = "WallBase"
        private const val DEFAULT_FILE_NAME = "wallpaper"
        private const val DEFAULT_EXTENSION = "jpg"
        private const val DEFAULT_MIME_TYPE = "image/jpeg"
        private val INVALID_FOLDER_CHARS = "[/:*?\"<>|]".toRegex()
        private val INVALID_FILE_CHARS = "[/:*?\"<>|]".toRegex()
    }
}