package com.joshiminh.wallbase.data.repository

import android.graphics.Bitmap
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.joshiminh.wallbase.data.dao.AlbumDao
import com.joshiminh.wallbase.data.dao.WallpaperDao
import com.joshiminh.wallbase.data.entity.album.AlbumDetail
import com.joshiminh.wallbase.data.entity.album.AlbumEntity
import com.joshiminh.wallbase.data.entity.album.AlbumItem
import com.joshiminh.wallbase.data.entity.album.AlbumWallpaperCrossRef
import com.joshiminh.wallbase.data.entity.album.AlbumWithWallpapers
import com.joshiminh.wallbase.data.entity.source.SourceKeys
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperEntity
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperWithAlbums
import com.joshiminh.wallbase.data.repository.LocalStorageCoordinator.CopyResult
import com.joshiminh.wallbase.util.wallpapers.EditedWallpaper
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.LinkedHashSet
import java.util.Locale
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LibraryRepository(
    private val wallpaperDao: WallpaperDao,
    private val albumDao: AlbumDao,
    private val localStorage: LocalStorageCoordinator
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

    suspend fun importLocalWallpapers(uris: List<Uri>): LocalImportResult {
        if (uris.isEmpty()) return LocalImportResult(0, 0)
        return withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val seen = mutableSetOf<String>()
            val entities = mutableListOf<WallpaperEntity>()
            var failed = 0
            for (uri in uris) {
                val key = uri.toString()
                if (!seen.add(key)) continue
                val copy = runCatching {
                    localStorage.copyFromUri(
                        uri = uri,
                        sourceFolder = LOCAL_SOURCE_FOLDER
                    )
                }.getOrElse { error ->
                    failed++
                    if (error is IllegalStateException) throw error
                    continue
                }
                entities += createLocalWallpaperEntity(copy, now, LOCAL_SOURCE_FOLDER)
            }
            if (entities.isEmpty()) {
                LocalImportResult(imported = 0, skipped = failed)
            } else {
                val insertResults = wallpaperDao.insertWallpapers(entities)
                val inserted = insertResults.count { it != -1L }
                val skipped = failed + insertResults.count { it == -1L }
                LocalImportResult(imported = inserted, skipped = skipped)
            }
        }
    }

    suspend fun importLocalFolder(folderUri: Uri): LocalFolderImportResult {
        return withContext(Dispatchers.IO) {
            val folder = localStorage.documentFromTree(folderUri)
                ?.takeIf { it.isDirectory }
                ?: throw IllegalArgumentException("Unable to access selected folder")
            val folderName = folder.name?.takeIf { it.isNotBlank() } ?: "Imported Folder"
            val now = System.currentTimeMillis()
            val images = folder.listFiles().filter { it.isImageFile() }
            if (images.isEmpty()) {
                return@withContext LocalFolderImportResult(
                    albumId = null,
                    albumTitle = folderName,
                    imported = 0,
                    skipped = 0
                )
            }
            val entities = mutableListOf<WallpaperEntity>()
            var failures = 0
            images.forEach { file ->
                val copy = runCatching {
                    localStorage.copyFromUri(
                        uri = file.uri,
                        sourceFolder = LOCAL_SOURCE_FOLDER,
                        subFolder = folderName,
                        displayName = file.name,
                        mimeTypeHint = file.type
                    )
                }.getOrElse { error ->
                    failures++
                    if (error is IllegalStateException) throw error
                    return@forEach
                }
                entities += createLocalWallpaperEntity(copy, now, folderName)
            }

            if (entities.isEmpty()) {
                return@withContext LocalFolderImportResult(
                    albumId = null,
                    albumTitle = folderName,
                    imported = 0,
                    skipped = failures
                )
            }

            val insertResults = wallpaperDao.insertWallpapers(entities)
            val wallpaperIds = buildList {
                entities.forEachIndexed { index, entity ->
                    val insertedId = insertResults.getOrNull(index)
                    when {
                        insertedId == null -> {}
                        insertedId == -1L -> {
                            wallpaperDao.findIdByImageUrl(SourceKeys.LOCAL, entity.imageUrl)?.let { add(it) }
                        }
                        else -> add(insertedId)
                    }
                }
            }

            val albumId = ensureAlbum(folderName, now).id
            if (wallpaperIds.isNotEmpty()) {
                val refs = wallpaperIds.map { AlbumWallpaperCrossRef(albumId, it) }
                albumDao.insertCrossRefs(refs)
            }
            val importedCount = wallpaperIds.size
            val skipped = failures + (entities.size - importedCount)
            LocalFolderImportResult(
                albumId = albumId,
                albumTitle = folderName,
                imported = importedCount,
                skipped = skipped
            )
        }
    }

    suspend fun downloadWallpapers(wallpapers: List<WallpaperItem>): DownloadResult {
        if (wallpapers.isEmpty()) return DownloadResult(0, 0, 0)
        return withContext(Dispatchers.IO) {
            var downloaded = 0
            var skipped = 0
            var failed = 0
            wallpapers.forEach { item ->
                val sourceKey = item.sourceKey
                if (sourceKey.isNullOrBlank() || sourceKey == SourceKeys.LOCAL) {
                    skipped++
                    return@forEach
                }
                val wallpaperId = resolveWallpaperId(item)
                if (wallpaperId == null) {
                    skipped++
                    return@forEach
                }
                val entity = wallpaperDao.getById(wallpaperId)
                if (entity != null && entity.isDownloaded && !entity.localUri.isNullOrBlank()) {
                    skipped++
                    return@forEach
                }
                val targetUrl = entity?.imageUrl ?: item.imageUrl
                val remote = downloadRemoteImage(targetUrl)
                if (remote == null) {
                    failed++
                    return@forEach
                }
                val folderName = wallpaperFolderName(item)
                val copy = runCatching {
                    localStorage.writeBytes(
                        data = remote.bytes,
                        sourceFolder = folderName,
                        displayName = item.title.ifBlank { item.remoteIdentifierWithinSource().orEmpty() },
                        mimeTypeHint = remote.mimeType
                    )
                }.getOrElse { error ->
                    failed++
                    if (error is IllegalStateException) throw error
                    return@forEach
                }
                val now = System.currentTimeMillis()
                wallpaperDao.updateDownloadState(
                    id = wallpaperId,
                    localUri = copy.uri.toString(),
                    isDownloaded = true,
                    fileSize = copy.sizeBytes,
                    updatedAt = now
                )
                downloaded++
            }
            DownloadResult(downloaded = downloaded, skipped = skipped, failed = failed)
        }
    }

    suspend fun saveEditedWallpaper(
        wallpaper: WallpaperItem,
        edited: EditedWallpaper
    ): DownloadResult {
        val sourceKey = wallpaper.sourceKey ?: return DownloadResult(0, 1, 0)
        return withContext(Dispatchers.IO) {
            val ensure = ensureWallpaperSaved(wallpaper)
            val wallpaperId = when (ensure) {
                is EnsureResult.Inserted -> ensure.id
                is EnsureResult.Existing -> ensure.id
                EnsureResult.Skipped, EnsureResult.Failed -> null
            }
            if (wallpaperId == null) {
                return@withContext DownloadResult(downloaded = 0, skipped = 1, failed = 0)
            }

            val bytes = ByteArrayOutputStream().use { stream ->
                if (!edited.bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                    return@withContext DownloadResult(downloaded = 0, skipped = 0, failed = 1)
                }
                stream.toByteArray()
            }
            val folderName = wallpaperFolderName(wallpaper)
            val displayName = wallpaper.title.ifBlank {
                wallpaper.remoteIdentifierWithinSource().orEmpty().ifBlank { "Wallpaper" }
            }
            val copy = localStorage.writeBytes(
                data = bytes,
                sourceFolder = folderName,
                displayName = displayName,
                mimeTypeHint = "image/jpeg"
            )
            val now = System.currentTimeMillis()
            wallpaperDao.updateDownloadState(
                id = wallpaperId,
                localUri = copy.uri.toString(),
                isDownloaded = true,
                fileSize = copy.sizeBytes,
                updatedAt = now
            )
            DownloadResult(downloaded = 1, skipped = 0, failed = 0)
        }
    }

    suspend fun removeDownloads(wallpapers: List<WallpaperItem>): DownloadRemovalResult {
        if (wallpapers.isEmpty()) return DownloadRemovalResult(0, 0, 0)
        return withContext(Dispatchers.IO) {
            var removed = 0
            var skipped = 0
            var failed = 0
            wallpapers.forEach { item ->
                val sourceKey = item.sourceKey
                if (sourceKey.isNullOrBlank() || sourceKey == SourceKeys.LOCAL) {
                    skipped++
                    return@forEach
                }
                val wallpaperId = resolveWallpaperId(item)
                if (wallpaperId == null) {
                    skipped++
                    return@forEach
                }
                val entity = wallpaperDao.getById(wallpaperId)
                if (entity == null || entity.localUri.isNullOrBlank() || !entity.isDownloaded) {
                    skipped++
                    return@forEach
                }
                val deleteResult = runCatching {
                    localStorage.deleteDocument(Uri.parse(entity.localUri))
                }.getOrElse { error ->
                    failed++
                    if (error is IllegalStateException) throw error
                    return@forEach
                }
                val now = System.currentTimeMillis()
                if (deleteResult) {
                    wallpaperDao.updateDownloadState(
                        id = wallpaperId,
                        localUri = null,
                        isDownloaded = false,
                        fileSize = null,
                        updatedAt = now
                    )
                    removed++
                } else {
                    failed++
                }
            }
            DownloadRemovalResult(removed = removed, skipped = skipped, failed = failed)
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
                    val localId = wallpaper.remoteIdentifierWithinSource()?.toLongOrNull()
                    if (localId != null) {
                        wallpaperDao.deleteById(localId) > 0
                    } else {
                        false
                    }
                }

                else -> {
                    val remoteId = wallpaper.remoteIdentifierWithinSource()
                        ?: return@withContext false
                    wallpaperDao.deleteBySourceKeyAndRemoteId(sourceKey, remoteId) > 0
                }
            }
        }
    }

    suspend fun removeWallpapers(wallpapers: List<WallpaperItem>): Int {
        if (wallpapers.isEmpty()) return 0
        return withContext(Dispatchers.IO) {
            val uniqueIds = LinkedHashSet<Long>()
            val entities = mutableListOf<WallpaperEntity>()

            wallpapers.forEach { wallpaper ->
                val id = resolveWallpaperId(wallpaper)
                if (id != null && uniqueIds.add(id)) {
                    val entity = wallpaperDao.getById(id)
                    if (entity != null) {
                        entities += entity
                    }
                }
            }

            var removed = 0
            entities.forEach { entity ->
                val localUri = entity.localUri
                if (!localUri.isNullOrBlank() && (entity.isDownloaded || entity.sourceKey == SourceKeys.LOCAL)) {
                    runCatching { localStorage.deleteDocument(Uri.parse(localUri)) }
                        .onFailure { error ->
                            if (error is IllegalStateException) throw error
                        }
                }

                if (wallpaperDao.deleteById(entity.id) > 0) {
                    removed++
                }
            }

            removed
        }
    }

    suspend fun isWallpaperInLibrary(wallpaper: WallpaperItem): Boolean {
        val sourceKey = wallpaper.sourceKey ?: return false
        if (sourceKey == SourceKeys.LOCAL) return true
        return withContext(Dispatchers.IO) {
            val remoteId = wallpaper.remoteIdentifierWithinSource()
            when {
                remoteId != null -> wallpaperDao.existsByRemoteId(sourceKey, remoteId)
                else -> wallpaperDao.existsByImageUrl(sourceKey, wallpaper.imageUrl)
            }
        }
    }

    suspend fun getWallpaperLibraryState(wallpaper: WallpaperItem): WallpaperLibraryState {
        val sourceKey = wallpaper.sourceKey ?: return WallpaperLibraryState(false, false, null)
        return withContext(Dispatchers.IO) {
            when (sourceKey) {
                SourceKeys.LOCAL -> {
                    val localId = wallpaper.remoteIdentifierWithinSource()?.toLongOrNull()
                    if (localId == null) {
                        WallpaperLibraryState(isInLibrary = false, isDownloaded = false, localUri = null)
                    } else {
                        val entity = wallpaperDao.getById(localId)
                        val localUri = entity?.localUri
                        WallpaperLibraryState(
                            isInLibrary = entity != null,
                            isDownloaded = entity?.isDownloaded == true && !localUri.isNullOrBlank(),
                            localUri = localUri
                        )
                    }
                }

                else -> {
                    val remoteId = wallpaper.remoteIdentifierWithinSource()
                    val entity = when {
                        remoteId != null -> wallpaperDao.getBySourceKeyAndRemoteId(sourceKey, remoteId)
                        else -> wallpaperDao.getBySourceKeyAndImageUrl(sourceKey, wallpaper.imageUrl)
                    }
                    val localUri = entity?.localUri
                    WallpaperLibraryState(
                        isInLibrary = entity != null,
                        isDownloaded = entity?.isDownloaded == true && !localUri.isNullOrBlank(),
                        localUri = localUri
                    )
                }
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
                coverImageUrl = null,
                createdAt = now
            )
        }
    }

    data class LocalImportResult(val imported: Int, val skipped: Int)

    data class LocalFolderImportResult(
        val albumId: Long?,
        val albumTitle: String,
        val imported: Int,
        val skipped: Int
    )

    data class DownloadResult(
        val downloaded: Int,
        val skipped: Int,
        val failed: Int
    )

    data class DownloadRemovalResult(
        val removed: Int,
        val skipped: Int,
        val failed: Int
    )

    data class BulkAddResult(val added: Int, val skipped: Int)

    data class AlbumAssociationResult(
        val addedToAlbum: Int,
        val alreadyPresent: Int,
        val skipped: Int
    )

    data class WallpaperLibraryState(
        val isInLibrary: Boolean,
        val isDownloaded: Boolean,
        val localUri: String?
    )

    private suspend fun ensureAlbum(title: String, now: Long): AlbumEntity {
        val normalized = title.trim().ifBlank { "Album" }
        albumDao.findAlbumByTitle(normalized)?.let { return it }
        val entity = AlbumEntity(
            title = normalized,
            description = null,
            coverWallpaperId = null,
            sortOrder = 0,
            isPinned = false,
            createdAt = now,
            updatedAt = now,
            syncToken = null
        )
        val inserted = albumDao.insertAlbums(listOf(entity)).firstOrNull()
        return if (inserted != null && inserted != -1L) {
            entity.copy(id = inserted)
        } else {
            albumDao.findAlbumByTitle(normalized)
                ?: throw IllegalStateException("Unable to create album")
        }
    }

    private fun createLocalWallpaperEntity(copy: CopyResult, timestamp: Long, folderName: String): WallpaperEntity {
        val uriString = copy.uri.toString()
        val title = copy.displayName.ifBlank { "Local Wallpaper" }
        return WallpaperEntity(
            sourceKey = SourceKeys.LOCAL,
            remoteId = null,
            source = folderName,
            title = title,
            description = null,
            imageUrl = uriString,
            sourceUrl = uriString,
            localUri = uriString,
            width = null,
            height = null,
            colorPalette = null,
            fileSizeBytes = copy.sizeBytes,
            isFavorite = false,
            isDownloaded = true,
            appliedAt = null,
            addedAt = timestamp,
            updatedAt = timestamp
        )
    }

    private suspend fun ensureWallpaperSaved(wallpaper: WallpaperItem): EnsureResult {
        val sourceKey = wallpaper.sourceKey ?: return EnsureResult.Skipped
        if (sourceKey == SourceKeys.LOCAL) return EnsureResult.Skipped

        val remoteId = wallpaper.remoteIdentifierWithinSource()
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

    private suspend fun resolveWallpaperId(wallpaper: WallpaperItem): Long? {
        val sourceKey = wallpaper.sourceKey ?: return null
        return when (sourceKey) {
            SourceKeys.LOCAL -> wallpaper.remoteIdentifierWithinSource()?.toLongOrNull()
            else -> {
                val remoteId = wallpaper.remoteIdentifierWithinSource()
                when {
                    remoteId != null -> wallpaperDao.findIdByRemoteId(sourceKey, remoteId)
                    else -> wallpaperDao.findIdByImageUrl(sourceKey, wallpaper.imageUrl)
                }
            }
        }
    }

    private suspend fun downloadRemoteImage(url: String): RemoteImage? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(url).openConnection()
                if (connection is HttpURLConnection) {
                    connection.connect()
                    if (connection.responseCode >= 400) {
                        connection.disconnect()
                        throw IOException("HTTP ${connection.responseCode}")
                    }
                    val bytes = connection.inputStream.use { it.readBytes() }
                    val type = connection.contentType
                    connection.disconnect()
                    RemoteImage(bytes, type)
                } else {
                    val bytes = connection.getInputStream().use { it.readBytes() }
                    RemoteImage(bytes, connection.contentType)
                }
            }.getOrNull()
        }
    }

    private fun wallpaperFolderName(wallpaper: WallpaperItem): String {
        val title = wallpaper.sourceName?.takeIf { it.isNotBlank() }
            ?: wallpaper.providerKey()?.takeIf { it.isNotBlank() }
            ?: "Remote"
        return localStorage.sanitizeFolderName(title)
    }

    private fun DocumentFile.isImageFile(): Boolean {
        if (!isFile) return false
        val type = type?.lowercase(Locale.ROOT)
        if (type != null) {
            return type.startsWith("image/")
        }
        val name = name?.lowercase(Locale.ROOT) ?: return false
        return name.endsWith(".jpg") ||
            name.endsWith(".jpeg") ||
            name.endsWith(".png") ||
            name.endsWith(".webp")
    }

    private data class RemoteImage(val bytes: ByteArray, val mimeType: String?)

    private sealed interface EnsureResult {
        data class Inserted(val id: Long) : EnsureResult
        data class Existing(val id: Long) : EnsureResult
        data object Skipped : EnsureResult
        data object Failed : EnsureResult
    }

    private companion object {
        private const val LOCAL_SOURCE_FOLDER = "Local"
    }
}

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
        height = height,
        addedAt = addedAt,
        localUri = localUri,
        isDownloaded = isDownloaded
    )
}

private fun AlbumWithWallpapers.toAlbumItem(): AlbumItem {
    val cover = wallpapers.firstOrNull()?.let { wallpaper ->
        wallpaper.localUri ?: wallpaper.imageUrl
    }
    return AlbumItem(
        id = album.id,
        title = album.title,
        wallpaperCount = wallpapers.size,
        coverImageUrl = cover,
        createdAt = album.createdAt
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
