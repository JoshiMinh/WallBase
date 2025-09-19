package com.joshiminh.wallbase.data.library

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.joshiminh.wallbase.data.local.dao.AlbumDao
import com.joshiminh.wallbase.data.local.dao.WallpaperDao
import com.joshiminh.wallbase.data.local.entity.AlbumEntity
import com.joshiminh.wallbase.data.local.entity.AlbumWithWallpapers
import com.joshiminh.wallbase.data.local.entity.WallpaperEntity
import com.joshiminh.wallbase.data.local.entity.WallpaperWithAlbums
import com.joshiminh.wallbase.data.source.SourceKeys
import com.joshiminh.wallbase.data.wallpapers.WallpaperItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LibraryRepository(
    private val wallpaperDao: WallpaperDao,
    private val albumDao: AlbumDao
) {
    fun observeSavedWallpapers(): Flow<List<WallpaperItem>> {
        return wallpaperDao.observeWallpapersWithAlbums()
            .map { entries -> entries.map { it.toWallpaperItem() } }
    }

    fun observeAlbums(): Flow<List<AlbumItem>> {
        return albumDao.observeAlbumsWithWallpapers()
            .map { albums -> albums.map { it.toAlbumItem() } }
    }

    suspend fun importLocalWallpapers(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return

        withContext(Dispatchers.IO) {
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

            if (wallpapers.isNotEmpty()) {
                wallpaperDao.insertWallpapers(wallpapers)
            }
        }
    }

    suspend fun addWallpaper(wallpaper: WallpaperItem): Boolean {
        val sourceKey = wallpaper.sourceKey
            ?: throw IllegalArgumentException("Wallpaper is missing a source key")

        if (sourceKey == SourceKeys.LOCAL) {
            return false
        }

        return withContext(Dispatchers.IO) {
            val remoteId = wallpaper.normalizeRemoteId(sourceKey)
            if (remoteId != null) {
                if (wallpaperDao.existsByRemoteId(sourceKey, remoteId)) {
                    return@withContext false
                }
            } else if (wallpaperDao.existsByImageUrl(sourceKey, wallpaper.imageUrl)) {
                return@withContext false
            }

            val now = System.currentTimeMillis()
            val result = wallpaperDao.insertWallpaper(
                WallpaperEntity(
                    sourceKey = sourceKey,
                    remoteId = remoteId,
                    source = wallpaper.sourceName ?: sourceKey,
                    title = wallpaper.title.ifBlank { "Wallpaper" },
                    description = null,
                    imageUrl = wallpaper.imageUrl,
                    sourceUrl = wallpaper.sourceUrl,
                    localUri = null,
                    width = null,
                    height = null,
                    colorPalette = null,
                    fileSizeBytes = null,
                    isFavorite = false,
                    isDownloaded = false,
                    appliedAt = null,
                    addedAt = now,
                    updatedAt = now
                )
            )
            result != -1L
        }
    }

    suspend fun removeWallpaper(wallpaper: WallpaperItem): Boolean {
        val sourceKey = wallpaper.sourceKey
            ?: throw IllegalArgumentException("Wallpaper is missing a source key")

        return withContext(Dispatchers.IO) {
            when (sourceKey) {
                SourceKeys.LOCAL -> {
                    val localId = wallpaper.normalizeRemoteId(sourceKey)?.toLongOrNull()
                    if (localId != null) {
                        wallpaperDao.deleteById(localId) > 0
                    } else {
                        false
                    }
                }

                else -> {
                    val remoteId = wallpaper.normalizeRemoteId(sourceKey)
                        ?: return@withContext false
                    wallpaperDao.deleteBySourceKeyAndRemoteId(sourceKey, remoteId) > 0
                }
            }
        }
    }

    suspend fun isWallpaperInLibrary(wallpaper: WallpaperItem): Boolean {
        val sourceKey = wallpaper.sourceKey ?: return false
        if (sourceKey == SourceKeys.LOCAL) return true
        return withContext(Dispatchers.IO) {
            val remoteId = wallpaper.normalizeRemoteId(sourceKey)
            when {
                remoteId != null -> wallpaperDao.existsByRemoteId(sourceKey, remoteId)
                else -> wallpaperDao.existsByImageUrl(sourceKey, wallpaper.imageUrl)
            }
        }
    }

    suspend fun createAlbum(title: String): AlbumItem {
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotEmpty()) { "Album name cannot be blank" }

        return withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val entity = AlbumEntity(
                title = normalizedTitle,
                description = null,
                coverWallpaperId = null,
                sortOrder = 0,
                isPinned = false,
                createdAt = now,
                updatedAt = now,
                syncToken = null
            )

            val result = albumDao.insertAlbums(listOf(entity)).firstOrNull() ?: -1L
            if (result == -1L) {
                throw IllegalStateException("Album already exists")
            }

            AlbumItem(
                id = result,
                title = normalizedTitle,
                wallpaperCount = 0,
                coverImageUrl = null
            )
        }
    }

    // File: LibraryRepository.kt (outside the class)
    private fun WallpaperWithAlbums.toWallpaperItem(): WallpaperItem {
        val entity = wallpaper
        val remoteId = entity.remoteId ?: entity.id.toString()
        val displayImageUrl = entity.localUri ?: entity.imageUrl
        val originalUrl = entity.sourceUrl ?: entity.localUri ?: entity.imageUrl
        return WallpaperItem(
            id = "${entity.sourceKey}:$remoteId",
            title = entity.title,
            imageUrl = displayImageUrl,
            sourceUrl = originalUrl,
            sourceName = entity.source,
            sourceKey = entity.sourceKey
        )
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

    private fun AlbumWithWallpapers.toAlbumItem(): AlbumItem {
        val cover = wallpapers.firstOrNull()?.let { wallpaper ->
            wallpaper.localUri ?: wallpaper.imageUrl
        }
        return AlbumItem(
            id = album.id,
            title = album.title,
            wallpaperCount = wallpapers.size,
            coverImageUrl = cover
        )
    }

    private fun WallpaperItem.normalizeRemoteId(sourceKey: String): String? {
        val prefix = "$sourceKey:"
        return if (id.startsWith(prefix)) {
            id.removePrefix(prefix)
        } else {
            id
        }
    }
}