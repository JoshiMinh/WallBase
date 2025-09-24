@file:Suppress("TYPE_INTERSECTION_AS_REIFIED_WARNING",
    "NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS"
)

package com.joshiminh.wallbase.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.net.toFile
import androidx.documentfile.provider.DocumentFile
import androidx.sqlite.db.SupportSQLiteDatabase
import com.joshiminh.wallbase.data.entity.source.SourceKeys
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperEntity
import com.joshiminh.wallbase.data.repository.LocalStorageCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.io.InputStream

class DatabaseBackupManager(
    private val context: Context,
    private val localStorage: LocalStorageCoordinator,
    private val database: WallBaseDatabase = WallBaseDatabase.getInstance(context)
) {

    suspend fun exportBackup(destination: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            require(dbFile.exists()) { "Database file not found" }

            val sqliteDb = database.openHelper.writableDatabase
            runCatching {
                sqliteDb.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
                    while (cursor.moveToNext()) {
                        // exhaust the result set to ensure the checkpoint runs
                    }
                }
            }

            val tempFile = File.createTempFile("wallbase_export_", ".db", context.cacheDir)
            var usedVacuum: Boolean

            try {
                val quotedPath = tempFile.absolutePath.replace("'", "''")
                usedVacuum = runCatching {
                    sqliteDb.execSQL("VACUUM INTO '$quotedPath'")
                }.isSuccess

                val sourceFile = if (usedVacuum) tempFile else dbFile
                val wallpapersWithMedia = database.wallpaperDao()
                    .getWallpapersWithLocalMedia()

                context.contentResolver.openOutputStream(destination)?.use { output ->
                    ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                        zip.putNextEntry(ZipEntry(DATABASE_ENTRY))
                        sourceFile.inputStream().use { input -> input.copyTo(zip) }
                        zip.closeEntry()

                        if (wallpapersWithMedia.isNotEmpty()) {
                            val manifest = JSONArray()
                            wallpapersWithMedia.forEach { entity ->
                                val uri = entity.localUri?.let(Uri::parse) ?: return@forEach
                                val document = localStorage.documentFromUri(uri) ?: return@forEach
                                val fileName = document.name ?: "wallpaper-${entity.id}"
                                val sanitizedName = localStorage.sanitizeFileName(
                                    fileName,
                                    "wallpaper-${entity.id}"
                                )
                                val folderInfo = resolveFolderInfo(entity)
                                val entryName = "$LOCAL_MEDIA_DIR/${entity.id}/$sanitizedName"
                                val input = openDocumentInput(document) ?: return@forEach
                                zip.putNextEntry(ZipEntry(entryName))
                                input.use { stream -> stream.copyTo(zip) }
                                zip.closeEntry()

                                val item = JSONObject().apply {
                                    put("id", entity.id)
                                    put("fileName", sanitizedName)
                                    put("sourceFolder", folderInfo.sourceFolder)
                                    if (folderInfo.subFolder != null) {
                                        put("subFolder", folderInfo.subFolder)
                                    } else {
                                        put("subFolder", JSONObject.NULL)
                                    }
                                    val mime = document.type
                                    if (mime.isNullOrBlank()) {
                                        put("mimeType", JSONObject.NULL)
                                    } else {
                                        put("mimeType", mime)
                                    }
                                }
                                manifest.put(item)
                            }

                            if (manifest.length() > 0) {
                                zip.putNextEntry(ZipEntry(LOCAL_MANIFEST_ENTRY))
                                zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                                zip.closeEntry()
                            }
                        }
                    }
                } ?: error("Unable to open destination for backup")
            } finally {
                if (!tempFile.delete()) tempFile.deleteOnExit()
            }

            Result.success(Unit)
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    suspend fun importBackup(source: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        val extractedMedia = mutableMapOf<Long, ExtractedMedia>()
        try {
            val tempFile = File.createTempFile("wallbase_backup_", ".pkg", context.cacheDir)

            context.contentResolver.openInputStream(source)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: error("Unable to open backup source")

            val isZip = tempFile.inputStream().use { stream ->
                val header = ByteArray(2)
                val read = stream.read(header)
                read == 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
            }

            val extraction = if (isZip) extractBackupPackage(tempFile, extractedMedia) else null
            val databaseFile = extraction?.databaseFile ?: tempFile
            val manifestJson = extraction?.manifestJson

            val sqliteDb = database.openHelper.writableDatabase
            sqliteDb.execSQL("PRAGMA foreign_keys=OFF")
            var transactionStarted = false
            try {
                val backupPath = databaseFile.absolutePath.replace("'", "''")
                sqliteDb.execSQL("ATTACH DATABASE '$backupPath' AS backup")
                sqliteDb.beginTransaction()
                transactionStarted = true

                DATA_TABLES.forEach { table ->
                    sqliteDb.execSQL("DELETE FROM $table")
                    if (sqliteDb.hasTable("backup", table)) {
                        sqliteDb.execSQL("INSERT INTO $table SELECT * FROM backup.$table")
                    }
                }

                if (sqliteDb.hasTable("main", "sqlite_sequence")) {
                    sqliteDb.execSQL("DELETE FROM sqlite_sequence")
                }
                if (sqliteDb.hasTable("backup", "sqlite_sequence")) {
                    sqliteDb.execSQL("INSERT INTO sqlite_sequence SELECT * FROM backup.sqlite_sequence")
                }

                sqliteDb.setTransactionSuccessful()
            } finally {
                if (transactionStarted) {
                    sqliteDb.endTransaction()
                }
                runCatching { sqliteDb.execSQL("DETACH DATABASE backup") }
                sqliteDb.execSQL("PRAGMA foreign_keys=ON")
                if (databaseFile != tempFile) {
                    databaseFile.delete()
                }
                tempFile.delete()
            }

            restoreLocalWallpapers(sqliteDb, manifestJson, extractedMedia)

            Result.success(Unit)
        } catch (error: Throwable) {
            Result.failure(error)
        } finally {
            extractedMedia.values.forEach { media -> media.file.delete() }
        }
    }

    private fun extractBackupPackage(
        packageFile: File,
        extractedMedia: MutableMap<Long, ExtractedMedia>
    ): ExtractedPackage {
        var databaseFile: File? = null
        var manifestJson: String? = null

        ZipInputStream(BufferedInputStream(FileInputStream(packageFile))).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    entry.isDirectory -> {}
                    name == DATABASE_ENTRY -> {
                        val tempDb = File.createTempFile("wallbase_backup_db_", ".db", context.cacheDir)
                        FileOutputStream(tempDb).use { output -> zip.copyTo(output) }
                        databaseFile = tempDb
                    }

                    name == LOCAL_MANIFEST_ENTRY -> {
                        val buffer = ByteArrayOutputStream()
                        zip.copyTo(buffer)
                        manifestJson = buffer.toString(Charsets.UTF_8.name())
                    }

                    name.startsWith("$LOCAL_MEDIA_DIR/") -> {
                        val parts = name.split('/')
                        if (parts.size >= 3) {
                            val id = parts[1].toLongOrNull()
                            val fileName = parts.last()
                            if (id != null && fileName.isNotBlank()) {
                                val tempMedia = File.createTempFile(
                                    "wallbase_media_${id}_",
                                    ".tmp",
                                    context.cacheDir
                                )
                                FileOutputStream(tempMedia).use { output -> zip.copyTo(output) }
                                extractedMedia[id] = ExtractedMedia(tempMedia, fileName)
                            }
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val dbFile = databaseFile ?: error("Backup package is missing database contents")
        return ExtractedPackage(databaseFile = dbFile, manifestJson = manifestJson)
    }

    private fun SupportSQLiteDatabase.hasTable(databaseName: String, tableName: String): Boolean {
        query(
            "SELECT name FROM $databaseName.sqlite_master WHERE type='table' AND name=?",
            arrayOf(tableName)
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    private suspend fun restoreLocalWallpapers(
        sqliteDb: SupportSQLiteDatabase,
        manifestJson: String?,
        extractedMedia: Map<Long, ExtractedMedia>
    ) {
        if (manifestJson.isNullOrBlank() || extractedMedia.isEmpty()) return

        val entries = parseManifest(manifestJson)
        if (entries.isEmpty()) return

        localStorage.ensureStorageDirectory()
        val now = System.currentTimeMillis()

        entries.forEach { entry ->
            val media = extractedMedia[entry.id] ?: return@forEach
            val bytes = media.file.readBytes()
            val copy = localStorage.writeBytes(
                data = bytes,
                sourceFolder = entry.sourceFolder,
                subFolder = entry.subFolder,
                displayName = entry.fileName,
                mimeTypeHint = entry.mimeType
            )
            val uriString = copy.uri.toString()
            sqliteDb.execSQL(
                "UPDATE wallpapers SET image_url = ?, source_url = ?, local_uri = ?, " +
                    "is_downloaded = 1, file_size_bytes = ?, updated_at = ? WHERE wallpaper_id = ?",
                arrayOf(uriString, uriString, uriString, copy.sizeBytes, now, entry.id)
            )
        }
    }

    private fun parseManifest(json: String): List<ManifestEntry> {
        val array = JSONArray(json)
        if (array.length() == 0) return emptyList()
        val entries = mutableListOf<ManifestEntry>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val id = obj.optLong("id", -1L)
            if (id <= 0L) continue
            val fileName = obj.optString("fileName", "wallpaper-$id")
            val rawSourceFolder = if (obj.isNull("sourceFolder")) null else obj.optString("sourceFolder", null)
            val rawSubFolder = if (obj.isNull("subFolder")) null else obj.optString("subFolder", null)
            val rawMime = if (obj.isNull("mimeType")) null else obj.optString("mimeType", null)
            entries += ManifestEntry(
                id = id,
                fileName = fileName,
                sourceFolder = rawSourceFolder?.takeIf { it.isNotBlank() } ?: LOCAL_SOURCE_FOLDER,
                subFolder = rawSubFolder?.takeIf { it.isNotBlank() },
                mimeType = rawMime?.takeIf { it.isNotBlank() }
            )
        }
        return entries
    }

    private fun openDocumentInput(document: DocumentFile): InputStream? {
        val uri = document.uri
        return when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> context.contentResolver.openInputStream(uri)
            ContentResolver.SCHEME_FILE, null -> runCatching { uri.toFile().inputStream() }.getOrNull()
            else -> context.contentResolver.openInputStream(uri)
        }
    }

    private fun resolveFolderInfo(entity: WallpaperEntity): FolderInfo {
        val sourceKey = entity.sourceKey
        return if (sourceKey == SourceKeys.LOCAL) {
            val subFolder = entity.source.takeIf { it.isNotBlank() }
                ?.let { localStorage.sanitizeFolderName(it) }
            FolderInfo(LOCAL_SOURCE_FOLDER, subFolder)
        } else {
            val rawFolder = entity.source.takeIf { it.isNotBlank() }
                ?: sourceKey.substringBefore(':', sourceKey)
            val sanitizedFolder = localStorage.sanitizeFolderName(rawFolder)
            FolderInfo(sanitizedFolder, null)
        }
    }

    companion object {
        private const val DATABASE_NAME = "wallbase.db"
        private val DATA_TABLES = listOf(
            "sources",
            "wallpapers",
            "albums",
            "album_wallpaper_cross_ref"
        )
        private const val DATABASE_ENTRY = "database/wallbase.db"
        private const val LOCAL_MEDIA_DIR = "local_wallpapers"
        private const val LOCAL_MANIFEST_ENTRY = "$LOCAL_MEDIA_DIR/manifest.json"
        private const val LOCAL_SOURCE_FOLDER = "Local"
    }

    private data class FolderInfo(
        val sourceFolder: String,
        val subFolder: String?
    )

    private data class ExtractedPackage(
        val databaseFile: File,
        val manifestJson: String?
    )

    private data class ExtractedMedia(
        val file: File,
        val fileName: String
    )

    private data class ManifestEntry(
        val id: Long,
        val fileName: String,
        val sourceFolder: String,
        val subFolder: String?,
        val mimeType: String?
    )
}
