package com.joshiminh.wallbase.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.joshiminh.wallbase.data.entity.album.AlbumDetail
import com.joshiminh.wallbase.data.entity.album.AlbumItem
import com.joshiminh.wallbase.data.dao.AlbumDao
import com.joshiminh.wallbase.data.dao.WallpaperDao
import com.joshiminh.wallbase.data.entity.album.AlbumEntity
import com.joshiminh.wallbase.data.entity.album.AlbumWallpaperCrossRef
import com.joshiminh.wallbase.data.entity.album.AlbumWithWallpapers
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperEntity
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperWithAlbums
import com.joshiminh.wallbase.data.entity.source.SourceKeys
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem
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

    fun observeAlbum(albumId: Long): Flow<AlbumDetail?> {
        return albumDao.observeAlbumWithWallpapers(albumId)
            .map { entry -> entry?.toAlbumDetail() }
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
        return withContext(Dispatchers.IO) {
            ensureWallpaperSaved(wallpaper) is EnsureResult.Inserted
        }
    }

    suspend fun addWallpapersToLibrary(wallpapers: List<WallpaperItem>): BulkAddResult {
        if (wallpapers.isEmpty()) return BulkAddResult(added = 0, skipped = 0)
        return withContext(Dispatchers.IO) {
            var added = 0
            var skipped = 0
            wallpapers.forEach { wallpaper ->
                when (ensureWallpaperSaved(wallpaper)) {
                    is EnsureResult.Inserted -> added++
                    is EnsureResult.Existing -> skipped++
                    EnsureResult.Skipped, EnsureResult.Failed -> skipped++
                }
            }
            BulkAddResult(added = added, skipped = skipped)
        }
    }

    suspend fun addWallpapersToAlbum(
        albumId: Long,
        wallpapers: List<WallpaperItem>
    ): AlbumAssociationResult {
        if (wallpapers.isEmpty()) return AlbumAssociationResult(0, 0, 0)
        return withContext(Dispatchers.IO) {
            val refs = mutableListOf<AlbumWallpaperCrossRef>()
            var skipped = 0
            wallpapers.forEach { wallpaper ->
                when (val result = ensureWallpaperSaved(wallpaper)) {
                    is EnsureResult.Inserted -> refs += AlbumWallpaperCrossRef(albumId, result.id)
                    is EnsureResult.Existing -> refs += AlbumWallpaperCrossRef(albumId, result.id)
                    EnsureResult.Skipped, EnsureResult.Failed -> skipped++
                }
            }

            if (refs.isEmpty()) {
                return@withContext AlbumAssociationResult(addedToAlbum = 0, alreadyPresent = 0, skipped = skipped)
            }

            val insertResults = albumDao.insertCrossRefs(refs)
            val added = insertResults.count { it != -1L }
            val alreadyPresent = insertResults.size - added
            AlbumAssociationResult(addedToAlbum = added, alreadyPresent = alreadyPresent, skipped = skipped)
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

    suspend fun removeWallpapers(wallpapers: List<WallpaperItem>): Int {
        if (wallpapers.isEmpty()) return 0
        return withContext(Dispatchers.IO) {
            var removed = 0
            wallpapers.forEach { wallpaper ->
                val sourceKey = wallpaper.sourceKey ?: return@forEach
                when (sourceKey) {
                    SourceKeys.LOCAL -> {
                        val localId = wallpaper.normalizeRemoteId(sourceKey)?.toLongOrNull()
                        if (localId != null && wallpaperDao.deleteById(localId) > 0) {
                            removed++
                        }
                    }

                    else -> {
                        val remoteId = wallpaper.normalizeRemoteId(sourceKey)
                        val deleted = when {
                            remoteId != null -> wallpaperDao.deleteBySourceKeyAndRemoteId(sourceKey, remoteId)
                            else -> wallpaperDao.deleteByImageUrl(sourceKey, wallpaper.imageUrl)
                        }
                        if (deleted > 0) {
                            removed += deleted
                        }
                    }
                }
            }
            removed
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

    data class BulkAddResult(val added: Int, val skipped: Int)

    data class AlbumAssociationResult(
        val addedToAlbum: Int,
        val alreadyPresent: Int,
        val skipped: Int
    )

    private suspend fun ensureWallpaperSaved(wallpaper: WallpaperItem): EnsureResult {
        val sourceKey = wallpaper.sourceKey ?: return EnsureResult.Skipped
        if (sourceKey == SourceKeys.LOCAL) return EnsureResult.Skipped

        val remoteId = wallpaper.normalizeRemoteId(sourceKey)
        val existingId = when {
            remoteId != null -> wallpaperDao.findIdByRemoteId(sourceKey, remoteId)
            else -> wallpaperDao.findIdByImageUrl(sourceKey, wallpaper.imageUrl)
        }
        if (existingId != null) {
            return EnsureResult.Existing(existingId)
        }

        val now = System.currentTimeMillis()
        val entity = WallpaperEntity(
            sourceKey = sourceKey,
            remoteId = remoteId,
            source = wallpaper.sourceName ?: sourceKey,
            title = wallpaper.title.ifBlank { "Wallpaper" },
            description = null,
            imageUrl = wallpaper.imageUrl,
            sourceUrl = wallpaper.sourceUrl,
            localUri = null,
            width = wallpaper.width,
            height = wallpaper.height,
            colorPalette = null,
            fileSizeBytes = null,
            isFavorite = false,
            isDownloaded = false,
            appliedAt = null,
            addedAt = now,
            updatedAt = now
        )
        val insertedId = wallpaperDao.insertWallpaper(entity)
        if (insertedId != -1L) {
            return EnsureResult.Inserted(insertedId)
        }

        val fallbackId = when {
            remoteId != null -> wallpaperDao.findIdByRemoteId(sourceKey, remoteId)
            else -> wallpaperDao.findIdByImageUrl(sourceKey, wallpaper.imageUrl)
        }
        return fallbackId?.let { EnsureResult.Existing(it) } ?: EnsureResult.Failed
    }

    private sealed interface EnsureResult {
        data class Inserted(val id: Long) : EnsureResult
        data class Existing(val id: Long) : EnsureResult
        data object Skipped : EnsureResult
        data object Failed : EnsureResult
    }
}

// File: LibraryRepository.kt (outside the class)
private fun WallpaperWithAlbums.toWallpaperItem(): WallpaperItem = wallpaper.toLibraryWallpaperItem()

private fun WallpaperEntity.toLibraryWallpaperItem(): WallpaperItem {
    val remoteId = remoteId ?: id.toString()
    val displayImageUrl = localUri ?: imageUrl
    val originalUrl = sourceUrl ?: localUri ?: imageUrl
    return WallpaperItem(
        id = "${sourceKey}:$remoteId",
        title = title,
        imageUrl = displayImageUrl,
        sourceUrl = originalUrl,
        sourceName = source,
        sourceKey = sourceKey,
        width = width,
        height = height
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

private fun AlbumWithWallpapers.toAlbumDetail(): AlbumDetail {
    return AlbumDetail(
        id = album.id,
        title = album.title,
        wallpaperCount = wallpapers.size,
        wallpapers = wallpapers.map { it.toLibraryWallpaperItem() }
    )
}

private fun WallpaperItem.normalizeRemoteId(sourceKey: String): String? {
    val key = libraryKey() ?: return null
    return key.substringAfter("$sourceKey:")
}