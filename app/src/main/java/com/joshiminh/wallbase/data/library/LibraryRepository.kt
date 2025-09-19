package com.joshiminh.wallbase.data.library

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.joshiminh.wallbase.data.local.dao.WallpaperDao
import com.joshiminh.wallbase.data.local.entity.WallpaperEntity
import com.joshiminh.wallbase.data.source.SourceKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibraryRepository(
    private val wallpaperDao: WallpaperDao
) {
    suspend fun importLocalWallpapers(context: Context, uris: List<Uri>): ImportResult {
        if (uris.isEmpty()) return ImportResult(imported = 0, skipped = 0)

        return withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val now = System.currentTimeMillis()
            val existing = mutableSetOf<String>()
            val wallpapers = buildList {
                for (uri in uris) {
                    val uriString = uri.toString()
                    if (!existing.add(uriString)) continue

                    val metadata = resolver.queryMetadata(uri)
                    val displayName = metadata?.first ?: "Local Wallpaper"
                    val fileSize = metadata?.second

                    try {
                        resolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: SecurityException) {
                        // Ignore if we cannot persist the permission. We'll still try to save the URI.
                    }

                    add(
                        WallpaperEntity(
                            sourceKey = SourceKeys.LOCAL,
                            remoteId = null,
                            source = SourceKeys.LOCAL,
                            title = displayName.ifBlank { "Local Wallpaper" },
                            description = null,
                            imageUrl = uriString,
                            sourceUrl = null,
                            localUri = uriString,
                            width = null,
                            height = null,
                            colorPalette = null,
                            fileSizeBytes = fileSize,
                            isFavorite = false,
                            isDownloaded = true,
                            appliedAt = null,
                            addedAt = now,
                            updatedAt = now
                        )
                    )
                }
            }

            val result = if (wallpapers.isNotEmpty()) {
                val insertedIds = wallpaperDao.insertWallpapers(wallpapers)
                val imported = insertedIds.count { it != -1L }
                ImportResult(imported = imported, skipped = wallpapers.size - imported)
            } else {
                ImportResult(imported = 0, skipped = 0)
            }

            result
        }
    }

    private fun ContentResolver.queryMetadata(uri: Uri): Pair<String, Long?>? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = query(uri, projection, null, null, null) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
            val displayName = if (nameIndex >= 0) it.getString(nameIndex) else null
            val size = if (sizeIndex >= 0) it.getLong(sizeIndex) else null
            return (displayName ?: "Local Wallpaper") to size
        }
    }

    data class ImportResult(
        val imported: Int,
        val skipped: Int
    )
}
