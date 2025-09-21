package com.joshiminh.wallbase.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

class LocalStorageCoordinator(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {

    private val resolver = context.contentResolver

    suspend fun configureBaseFolder(treeUri: Uri): DocumentFile = withContext(Dispatchers.IO) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            resolver.takePersistableUriPermission(treeUri, flags)
        } catch (_: SecurityException) {
            // Ignore if we cannot persist; best effort access is still attempted.
        }

        val tree = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalArgumentException("Invalid folder selection")
        val base = tree.findFile(DEFAULT_FOLDER_NAME)?.takeIf { it.isDirectory }
            ?: tree.createDirectory(DEFAULT_FOLDER_NAME)
            ?: throw IOException("Unable to create WallBase folder")

        settingsRepository.setLocalLibraryUri(base.uri.toString())
        base
    }

    suspend fun clearBaseFolder() {
        settingsRepository.setLocalLibraryUri(null)
    }

    suspend fun getBaseFolder(): DocumentFile? = withContext(Dispatchers.IO) {
        val stored = settingsRepository.getLocalLibraryUri()?.takeIf { it.isNotBlank() } ?: return@withContext null
        DocumentFile.fromTreeUri(context, Uri.parse(stored))?.takeIf { it.exists() && it.isDirectory }
    }

    suspend fun requireBaseFolder(): DocumentFile =
        getBaseFolder() ?: throw IllegalStateException("Local library folder is not configured")

    suspend fun copyFromUri(
        uri: Uri,
        sourceFolder: String,
        subFolder: String? = null,
        displayName: String? = null,
        mimeTypeHint: String? = null
    ): CopyResult = withContext(Dispatchers.IO) {
        val base = requireBaseFolder()
        copyIntoFolder(base, uri, sourceFolder, subFolder, displayName, mimeTypeHint)
    }

    suspend fun writeBytes(
        data: ByteArray,
        sourceFolder: String,
        subFolder: String? = null,
        displayName: String,
        mimeTypeHint: String? = null
    ): CopyResult = withContext(Dispatchers.IO) {
        val base = requireBaseFolder()
        val targetFolder = ensureTargetFolder(base, sourceFolder, subFolder)
        val mimeType = mimeTypeHint ?: guessMimeType(displayName)
        val sanitized = sanitizeFileName(displayName, DEFAULT_FILE_NAME)
        val named = ensureExtension(sanitized, mimeType)
        val fileName = ensureUniqueFileName(targetFolder, named)
        val destination = targetFolder.createFile(mimeType, fileName)
            ?: throw IOException("Unable to create destination file")
        resolver.openOutputStream(destination.uri, "w")?.use { output ->
            output.write(data)
        } ?: throw IOException("Unable to write destination file")
        CopyResult(destination.uri, destination.name ?: fileName, destination.length())
    }

    fun documentFromTree(uri: Uri): DocumentFile? {
        persistReadPermission(uri)
        return DocumentFile.fromTreeUri(context, uri)
    }

    fun documentFromUri(uri: Uri): DocumentFile? {
        persistReadPermission(uri)
        return DocumentFile.fromSingleUri(context, uri)
    }

    suspend fun deleteDocument(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val document = documentFromUri(uri) ?: return@withContext false
        document.delete()
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

    private suspend fun copyIntoFolder(
        baseFolder: DocumentFile,
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
        val destination = targetFolder.createFile(mimeType, fileName)
            ?: throw IOException("Unable to create destination file")

        persistReadPermission(uri)
        copyStreams(uri, destination.uri)

        CopyResult(destination.uri, destination.name ?: fileName, destination.length())
    }

    private fun ensureExtension(name: String, mimeType: String): String {
        if (name.contains('.')) return name
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        return if (!extension.isNullOrBlank()) "$name.$extension" else "$name.$DEFAULT_EXTENSION"
    }

    private fun ensureTargetFolder(
        baseFolder: DocumentFile,
        sourceFolder: String,
        subFolder: String?
    ): DocumentFile {
        val source = ensureFolder(baseFolder, sanitizeFolderName(sourceFolder))
        return if (subFolder.isNullOrBlank()) {
            source
        } else {
            ensureFolder(source, sanitizeFolderName(subFolder))
        }
    }

    private fun ensureFolder(parent: DocumentFile, name: String): DocumentFile {
        parent.findFile(name)?.takeIf { it.isDirectory }?.let { return it }
        return parent.createDirectory(name)
            ?: throw IOException("Unable to create folder $name")
    }

    private fun ensureUniqueFileName(folder: DocumentFile, name: String): String {
        var candidate = name
        var counter = 1
        val (base, extension) = splitName(name)
        while (folder.findFile(candidate) != null) {
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
        cursor.use {
            if (!it.moveToFirst()) return null
            val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0) it.getString(index) else null
        }
    }

    private fun copyStreams(source: Uri, destination: Uri) {
        val input = resolver.openInputStream(source)
            ?: throw IOException("Unable to open source stream")
        val output = resolver.openOutputStream(destination, "w")
            ?: run {
                input.close()
                throw IOException("Unable to open destination stream")
            }
        input.use { inStream ->
            output.use { outStream ->
                inStream.copyTo(outStream)
            }
        }
    }

    private fun persistReadPermission(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        try {
            resolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
            // Ignore if we cannot persist; temporary access is sufficient for copying.
        }
    }

    data class CopyResult(
        val uri: Uri,
        val displayName: String,
        val sizeBytes: Long
    )

    private companion object {
        private const val DEFAULT_FOLDER_NAME = "WallBase"
        private const val DEFAULT_FILE_NAME = "wallpaper"
        private const val DEFAULT_EXTENSION = "jpg"
        private const val DEFAULT_MIME_TYPE = "image/jpeg"
        private val INVALID_FOLDER_CHARS = "[\\/:*?\"<>|]".toRegex()
        private val INVALID_FILE_CHARS = "[\\/:*?\"<>|]".toRegex()
    }
}
