@file:Suppress("unused", "UnusedVariable", "AssignedValueIsNeverRead")

package com.joshiminh.wallbase.data.repository

import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.joshiminh.wallbase.data.local.AlbumDao
import com.joshiminh.wallbase.data.local.WallpaperDao
import com.joshiminh.wallbase.domain.model.AlbumDetail
import com.joshiminh.wallbase.data.local.AlbumEntity
import com.joshiminh.wallbase.domain.model.AlbumItem
import com.joshiminh.wallbase.data.local.AlbumWallpaperCrossRef
import com.joshiminh.wallbase.data.local.AlbumWithWallpapers
import com.joshiminh.wallbase.domain.model.SourceKeys
import com.joshiminh.wallbase.data.local.WallpaperEntity
import com.joshiminh.wallbase.domain.model.WallpaperItem
import com.joshiminh.wallbase.data.local.WallpaperWithAlbums
import com.joshiminh.wallbase.data.local.LocalStorageCoordinator
import com.joshiminh.wallbase.data.local.LocalStorageCoordinator.CopyResult
import com.joshiminh.wallbase.domain.wallpaper.EditedWallpaper
import com.joshiminh.wallbase.domain.wallpaper.WallpaperAdjustments
import com.joshiminh.wallbase.domain.wallpaper.WallpaperAdjustmentsJson
import com.joshiminh.wallbase.domain.wallpaper.WallpaperCrop
import com.joshiminh.wallbase.domain.wallpaper.WallpaperCropSettings
import java.util.LinkedHashSet
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(
    private val wallpaperDao: WallpaperDao,
    private val albumDao: AlbumDao,
    private val localStorage: LocalStorageCoordinator,
    private val remoteImageFetcher: RemoteImageFetcher
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
                    when (insertedId) {
                        null -> {}
                        -1L -> {
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

    suspend fun addDirectWallpaper(url: String): DirectAddResult {
        val normalized = url.trim()
        require(normalized.isNotEmpty()) { "Wallpaper URL cannot be blank" }

        val requestUrl = normalized
        val canonicalSourceUrl = normalized

        val parsedUri = requestUrl.toUri()
        val scheme = parsedUri.scheme?.lowercase(Locale.ROOT)
        if (scheme == null || scheme !in DIRECT_LINK_SCHEMES || parsedUri.host.isNullOrBlank()) {
            return DirectAddResult.Failure(
                reason = "Enter a valid HTTP or HTTPS image link."
            )
        }

        return withContext(Dispatchers.IO) {
            val existing = wallpaperDao.getBySourceKeyAndSourceUrl(SourceKeys.LOCAL, canonicalSourceUrl)
            if (existing != null) {
                return@withContext DirectAddResult.AlreadyExists(existing.toLibraryWallpaperItem())
            }

            val remote = remoteImageFetcher.fetch(requestUrl)
                ?: return@withContext DirectAddResult.Failure(
                    reason = "Unable to download image. Check the link and try again."
                )

            val mimeType = remote.mimeType?.lowercase(Locale.ROOT)
            val looksLikeDirectImage = remoteImageFetcher.looksLikeImageUrl(requestUrl) ||
                (canonicalSourceUrl != requestUrl && remoteImageFetcher.looksLikeImageUrl(canonicalSourceUrl))
            val isImageByMime = mimeType?.startsWith("image/") == true
            if (!isImageByMime && !remote.isLikelyImage && !looksLikeDirectImage) {
                return@withContext DirectAddResult.Failure(
                    reason = "The provided link does not point to an image."
                )
            }

            if (remote.bytes.isEmpty()) {
                return@withContext DirectAddResult.Failure(
                    reason = "Downloaded image is empty."
                )
            }

            val displayName = displayNameFromUrl(requestUrl)
            val copy = runCatching {
                localStorage.writeBytes(
                    data = remote.bytes,
                    sourceFolder = DIRECT_SOURCE_FOLDER,
                    displayName = displayName,
                    mimeTypeHint = remote.mimeType
                )
            }.getOrElse { error ->
                if (error is IllegalStateException) throw error
                return@withContext DirectAddResult.Failure(
                    reason = error.localizedMessage ?: "Unable to save wallpaper"
                )
            }

            val folderName = parsedUri.host?.takeIf { it.isNotBlank() }
                ?: DIRECT_SOURCE_FOLDER
            val now = System.currentTimeMillis()
            val baseEntity = createLocalWallpaperEntity(copy, now, folderName)
            val entity = baseEntity.copy(sourceUrl = canonicalSourceUrl)
            val insertedId = wallpaperDao.insertWallpaper(entity)
            if (insertedId == -1L) {
                runCatching { localStorage.deleteDocument(copy.uri) }
                val existingEntity = wallpaperDao
                    .getBySourceKeyAndImageUrl(SourceKeys.LOCAL, entity.imageUrl)
                return@withContext DirectAddResult.AlreadyExists(existingEntity?.toLibraryWallpaperItem())
            }

            val saved = entity.copy(id = insertedId)
            DirectAddResult.Success(saved.toLibraryWallpaperItem())
        }
    }

    suspend fun downloadWallpapers(
        wallpapers: List<WallpaperItem>,
        storageLimitBytes: Long? = null
    ): DownloadResult {
        if (wallpapers.isEmpty()) return DownloadResult(0, 0, 0, 0, 0)

        return withContext(Dispatchers.IO) {
            var downloaded = 0
            var skipped = 0
            var failed = 0
            var blocked = 0
            var totalBytes = 0L
            val limit = storageLimitBytes?.takeIf { it > 0 }
            var usage = limit?.let { wallpaperDao.totalDownloadedBytes() } ?: 0L

            wallpapers.forEach { item ->
                val sourceKey = item.sourceKey
                if (sourceKey.isNullOrBlank() || sourceKey == SourceKeys.LOCAL) {
                    skipped++
                    return@forEach
                }

                val wallpaperId = when (val ensure = ensureWallpaperSaved(item)) {
                    is EnsureResult.Inserted -> ensure.id
                    is EnsureResult.Existing -> ensure.id
                    EnsureResult.Skipped, EnsureResult.Failed -> resolveWallpaperId(item)
                }
                if (wallpaperId == null) {
                    skipped++
                    return@forEach
                }

                val entity = wallpaperDao.getById(wallpaperId)
                if (entity != null && entity.isDownloaded && !entity.localUri.isNullOrBlank()) {
                    skipped++
                    return@forEach
                }

                if (limit != null && usage >= limit) {
                    blocked++
                    return@forEach
                }

                val targetUrl = entity?.imageUrl ?: item.imageUrl
                val remote = remoteImageFetcher.fetch(targetUrl)
                if (remote == null) {
                    failed++
                    return@forEach
                }

                if (limit != null) {
                    val prospective = usage + remote.bytes.size.toLong()
                    if (prospective > limit) {
                        blocked++
                        return@forEach
                    }
                }

                val folderName = wallpaperFolderName(item)

                // Build a safe display name: prefer non-blank title, then a source-specific id, else a default.
                val displayName =
                    item.title.takeIf { it.isNotBlank() }
                        ?: item.remoteIdentifierWithinSource()?.takeIf { it.isNotBlank() }
                        ?: "Wallpaper"

                val copy = runCatching {
                    localStorage.writeBytes(
                        data = remote.bytes,
                        sourceFolder = folderName,
                        displayName = displayName,
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

                usage += copy.sizeBytes
                totalBytes += copy.sizeBytes
                downloaded++
            }

            DownloadResult(
                downloaded = downloaded,
                skipped = skipped,
                failed = failed,
                blocked = blocked,
                totalBytes = totalBytes
            )
        }
    }

    suspend fun downloadAlbums(
        albumIds: Collection<Long>,
        storageLimitBytes: Long? = null
    ): DownloadResult {
        if (albumIds.isEmpty()) return DownloadResult(0, 0, 0, 0, 0)

        return withContext(Dispatchers.IO) {
            val wallpapers = mutableListOf<WallpaperItem>()
            for (albumId in albumIds) {
                val album = albumDao.getAlbumWithWallpapers(albumId)
                if (album != null) {
                    wallpapers.addAll(album.wallpapers.map { it.toLibraryWallpaperItem() })
                }
            }
            downloadWallpapers(wallpapers, storageLimitBytes)
        }
    }

    suspend fun updateAdjustments(
        wallpaper: WallpaperItem,
        adjustments: WallpaperAdjustments?
    ) {
        withContext(Dispatchers.IO) {
            val id = resolveWallpaperId(wallpaper) ?: return@withContext
            val sanitized = adjustments?.sanitized()
            val normalized = sanitized?.takeUnless { it.isIdentity }
            val cropSettings = normalized?.normalizedCropSettings()?.encodeToString()
            val editSettings = normalized?.let { WallpaperAdjustmentsJson.encode(it) }
            wallpaperDao.updateEditSettings(
                id = id,
                cropSettings = cropSettings,
                editSettings = editSettings,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    suspend fun saveEditedWallpaper(
        wallpaper: WallpaperItem,
        edited: EditedWallpaper,
        storageLimitBytes: Long? = null
    ): DownloadResult {
        val sourceKey = wallpaper.sourceKey ?: return DownloadResult(0, 1, 0, 0, 0)
        return withContext(Dispatchers.IO) {
            val ensure = ensureWallpaperSaved(wallpaper)
            val wallpaperId = when (ensure) {
                is EnsureResult.Inserted -> ensure.id
                is EnsureResult.Existing -> ensure.id
                EnsureResult.Skipped, EnsureResult.Failed -> null
            }
            if (wallpaperId == null) {
                return@withContext DownloadResult(downloaded = 0, skipped = 1, failed = 0, blocked = 0, totalBytes = 0)
            }

            val limit = storageLimitBytes?.takeIf { it > 0 }
            var usage = limit?.let { wallpaperDao.totalDownloadedBytes() } ?: 0L
            if (limit != null && usage >= limit) {
                return@withContext DownloadResult(downloaded = 0, skipped = 0, failed = 0, blocked = 1, totalBytes = 0)
            }

            val bytes = ByteArrayOutputStream().use { stream ->
                if (!edited.bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                    return@withContext DownloadResult(downloaded = 0, skipped = 0, failed = 1, blocked = 0, totalBytes = 0)
                }
                stream.toByteArray()
            }

            if (limit != null) {
                val prospective = usage + bytes.size.toLong()
                if (prospective > limit) {
                    return@withContext DownloadResult(downloaded = 0, skipped = 0, failed = 0, blocked = 1, totalBytes = 0)
                }
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
            usage += copy.sizeBytes
            DownloadResult(
                downloaded = 1,
                skipped = 0,
                failed = 0,
                blocked = 0,
                totalBytes = copy.sizeBytes
            )
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
                    localStorage.deleteDocument(entity.localUri.toUri())
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

    suspend fun removeAllDownloads(): DownloadRemovalResult {
        return withContext(Dispatchers.IO) {
            val downloaded = wallpaperDao.getWallpapersWithLocalMedia()
            if (downloaded.isEmpty()) {
                DownloadRemovalResult(removed = 0, skipped = 0, failed = 0)
            } else {
                val items = downloaded.map { it.toLibraryWallpaperItem() }
                removeDownloads(items)
            }
        }
    }

    suspend fun addWallpaper(wallpaper: WallpaperItem): Boolean {
        return withContext(Dispatchers.IO) {
            ensureWallpaperSaved(wallpaper) is EnsureResult.Inserted
        }
    }

    suspend fun addWallpapersToLibrary(wallpapers: List<WallpaperItem>): BulkAddResult {
        if (wallpapers.isEmpty()) return BulkAddResult(added = 0, skipped = 0, addedWallpapers = emptyList())
        return withContext(Dispatchers.IO) {
            var added = 0
            var skipped = 0
            val inserted = mutableListOf<WallpaperItem>()
            wallpapers.forEach { wallpaper ->
                when (ensureWallpaperSaved(wallpaper)) {
                    is EnsureResult.Inserted -> {
                        added++
                        inserted += wallpaper
                    }
                    is EnsureResult.Existing -> skipped++
                    EnsureResult.Skipped, EnsureResult.Failed -> skipped++
                }
            }
            BulkAddResult(added = added, skipped = skipped, addedWallpapers = inserted)
        }
    }

    suspend fun getAlbumIdsForWallpaper(wallpaper: WallpaperItem): Set<Long> {
        return withContext(Dispatchers.IO) {
            val wallpaperId = resolveWallpaperId(wallpaper) ?: return@withContext emptySet()
            albumDao.getAlbumIdsForWallpaper(wallpaperId).toSet()
        }
    }

    suspend fun setWallpaperAlbums(wallpaper: WallpaperItem, albumIds: Set<Long>) {
        withContext(Dispatchers.IO) {
            ensureWallpaperSaved(wallpaper)
            val wallpaperId = resolveWallpaperId(wallpaper) ?: return@withContext
            val currentIds = albumDao.getAlbumIdsForWallpaper(wallpaperId).toSet()
            val toRemove = currentIds - albumIds
            val toAdd = albumIds - currentIds

            for (albumId in toRemove) {
                albumDao.deleteCrossRef(albumId, wallpaperId)
            }
            if (toAdd.isNotEmpty()) {
                val refs = toAdd.map { AlbumWallpaperCrossRef(it, wallpaperId) }
                albumDao.insertCrossRefs(refs)
            }
        }
    }

    suspend fun addWallpapersToAlbums(
        albumIds: Set<Long>,
        wallpapers: List<WallpaperItem>
    ): AlbumAssociationResult {
        if (albumIds.isEmpty() || wallpapers.isEmpty()) return AlbumAssociationResult(0, 0, 0)
        return withContext(Dispatchers.IO) {
            var added = 0
            var alreadyPresent = 0
            var skipped = 0
            for (albumId in albumIds) {
                val res = addWallpapersToAlbum(albumId, wallpapers)
                added += res.addedToAlbum
                alreadyPresent += res.alreadyPresent
                skipped += res.skipped
            }
            AlbumAssociationResult(added, alreadyPresent, skipped)
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
            val entity = when (sourceKey) {
                SourceKeys.LOCAL -> {
                    val localId = wallpaper.remoteIdentifierWithinSource()?.toLongOrNull()
                    if (localId != null) wallpaperDao.getById(localId) else null
                }

                else -> {
                    val remoteId = wallpaper.remoteIdentifierWithinSource()
                    when {
                        remoteId != null -> wallpaperDao.getBySourceKeyAndRemoteId(sourceKey, remoteId)
                        wallpaper.imageUrl.isNotBlank() ->
                            wallpaperDao.getBySourceKeyAndImageUrl(sourceKey, wallpaper.imageUrl)

                        else -> null
                    }
                }
            }

            entity?.let { deleteWallpaperEntity(it) } ?: false
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
                if (deleteWallpaperEntity(entity)) {
                    removed++
                }
            }

            removed
        }
    }

    private suspend fun deleteWallpaperEntity(entity: WallpaperEntity): Boolean {
        val localUri = entity.localUri
        if (!localUri.isNullOrBlank()) {
            runCatching { localStorage.deleteDocument(localUri.toUri()) }
                .onFailure { error ->
                    if (error is IllegalStateException) throw error
                }
        }

        albumDao.deleteCrossRefsForWallpaper(entity.id)
        albumDao.clearCoverWallpaper(entity.id)

        return wallpaperDao.deleteById(entity.id) > 0
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
        val sourceKey = wallpaper.sourceKey ?: return WallpaperLibraryState(
            isInLibrary = false,
            isDownloaded = false,
            localUri = null,
            cropSettings = null,
            adjustments = null
        )
        return withContext(Dispatchers.IO) {
            when (sourceKey) {
                SourceKeys.LOCAL -> {
                    val localId = wallpaper.remoteIdentifierWithinSource()?.toLongOrNull()
                    if (localId == null) {
                        WallpaperLibraryState(
                            isInLibrary = false,
                            isDownloaded = false,
                            localUri = null,
                            cropSettings = null,
                            adjustments = null
                        )
                    } else {
                        val entity = wallpaperDao.getById(localId)
                        val localUri = entity?.localUri
                        val adjustments = entity?.editSettings?.let(WallpaperAdjustmentsJson::decode)
                        val normalized = adjustments?.sanitized()
                        val crop = normalized?.normalizedCropSettings()
                            ?: WallpaperCropSettings.fromString(entity?.cropSettings)
                        WallpaperLibraryState(
                            isInLibrary = entity != null,
                            isDownloaded = entity?.isDownloaded == true && !localUri.isNullOrBlank(),
                            localUri = localUri,
                            cropSettings = crop,
                            adjustments = normalized
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
                    val adjustments = entity?.editSettings?.let(WallpaperAdjustmentsJson::decode)
                    val normalized = adjustments?.sanitized()
                    val crop = normalized?.normalizedCropSettings()
                        ?: WallpaperCropSettings.fromString(entity?.cropSettings)
                    WallpaperLibraryState(
                        isInLibrary = entity != null,
                        isDownloaded = entity?.isDownloaded == true && !localUri.isNullOrBlank(),
                        localUri = localUri,
                        cropSettings = crop,
                        adjustments = normalized
                    )
                }
            }
        }
    }

    suspend fun renameWallpaper(wallpaper: WallpaperItem, customTitle: String?): Boolean {
        return withContext(Dispatchers.IO) {
            val trimmed = customTitle?.trim()?.takeIf { it.isNotBlank() }
            val ensureResult = ensureWallpaperSaved(wallpaper)
            val wallpaperId = when (ensureResult) {
                is EnsureResult.Existing -> ensureResult.id
                is EnsureResult.Inserted -> ensureResult.id
                EnsureResult.Failed, EnsureResult.Skipped -> resolveWallpaperId(wallpaper)
            } ?: return@withContext false

            wallpaperDao.updateCustomTitle(
                id = wallpaperId,
                customTitle = trimmed,
                updatedAt = System.currentTimeMillis()
            ) > 0
        }
    }

    suspend fun rescanLibrary(): Boolean = withContext(Dispatchers.IO) {
        val downloaded = wallpaperDao.getWallpapersWithLocalMedia()
        val now = System.currentTimeMillis()
        for (entity in downloaded) {
            val uriStr = entity.localUri ?: continue
            val uri = uriStr.toUri()
            val exists = runCatching {
                if (uri.scheme == "file") {
                    java.io.File(uri.path ?: "").exists()
                } else {
                    localStorage.documentFromUri(uri)?.exists() == true
                }
            }.getOrDefault(false)

            if (!exists) {
                if (entity.sourceKey == SourceKeys.LOCAL) {
                    deleteWallpaperEntity(entity)
                } else {
                    wallpaperDao.updateDownloadState(
                        id = entity.id,
                        localUri = null,
                        isDownloaded = false,
                        fileSize = null,
                        updatedAt = now
                    )
                }
            }
        }
        true
    }

    suspend fun createAlbum(title: String): AlbumItem {
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotEmpty()) { "Album name cannot be blank" }

        return withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val maxOrder = albumDao.getMaxSortOrder() ?: -1
            val entity = AlbumEntity(
                title = normalizedTitle,
                description = null,
                coverWallpaperId = null,
                sortOrder = maxOrder + 1,
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
                createdAt = now,
                sortOrder = entity.sortOrder
            )
        }
    }

    suspend fun reorderAlbums(orderedAlbumIds: List<Long>) {
        withContext(Dispatchers.IO) {
            orderedAlbumIds.forEachIndexed { index, albumId ->
                albumDao.updateAlbumSortOrder(albumId, index)
            }
        }
    }

    suspend fun renameAlbum(albumId: Long, title: String): AlbumItem {
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotEmpty()) { "Album name cannot be blank" }

        return withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val existing = albumDao.getAlbum(albumId) ?: throw IllegalStateException("Album not found")
            val duplicate = albumDao.findAlbumByTitle(normalizedTitle)
            if (duplicate != null && duplicate.id != albumId) {
                throw IllegalStateException("Album already exists")
            }
            albumDao.updateAlbumTitle(albumId, normalizedTitle, now)
            val detail = albumDao.getAlbumWithWallpapers(albumId)
            detail?.toAlbumItem() ?: AlbumItem(
                id = albumId,
                title = normalizedTitle,
                wallpaperCount = 0,
                coverImageUrl = null,
                createdAt = existing.createdAt
            )
        }
    }

    suspend fun deleteAlbums(albumIds: Collection<Long>): Int {
        if (albumIds.isEmpty()) return 0
        return withContext(Dispatchers.IO) {
            albumDao.deleteAlbums(albumIds)
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
        val failed: Int,
        val blocked: Int,
        val totalBytes: Long
    )

    data class DownloadRemovalResult(
        val removed: Int,
        val skipped: Int,
        val failed: Int
    )

    data class BulkAddResult(val added: Int, val skipped: Int, val addedWallpapers: List<WallpaperItem>)

    data class AlbumAssociationResult(
        val addedToAlbum: Int,
        val alreadyPresent: Int,
        val skipped: Int
    )

    data class WallpaperLibraryState(
        val isInLibrary: Boolean,
        val isDownloaded: Boolean,
        val localUri: String?,
        val cropSettings: WallpaperCropSettings? = null,
        val adjustments: WallpaperAdjustments? = null,
    )

    sealed class DirectAddResult {
        data class Success(val wallpaper: WallpaperItem) : DirectAddResult()
        data class AlreadyExists(val wallpaper: WallpaperItem?) : DirectAddResult()
        data class Failure(val reason: String) : DirectAddResult()
    }

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
        val initialCrop = wallpaper.cropSettings?.sanitized()
        val initialAdjustments = initialCrop?.let {
            WallpaperAdjustments(crop = WallpaperCrop.Custom(it))
        }
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
            cropSettings = initialCrop?.encodeToString(),
            editSettings = initialAdjustments?.let { WallpaperAdjustmentsJson.encode(it) },
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

    private fun wallpaperFolderName(wallpaper: WallpaperItem): String {
        val title = wallpaper.sourceName?.takeIf { it.isNotBlank() }
            ?: wallpaper.providerKey()?.takeIf { it.isNotBlank() }
            ?: "Remote"
        return localStorage.sanitizeFolderName(title)
    }

    private fun displayNameFromUrl(url: String): String {
        val candidate = url.substringAfterLast('/')
            .substringBefore('?')
            .substringBefore('#')
        return localStorage.sanitizeFileName(candidate, DIRECT_FILE_FALLBACK)
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

    private sealed interface EnsureResult {
        data class Inserted(val id: Long) : EnsureResult
        data class Existing(val id: Long) : EnsureResult
        data object Skipped : EnsureResult
        data object Failed : EnsureResult
    }

    private companion object {
        private const val LOCAL_SOURCE_FOLDER = "Local"
        private const val DIRECT_SOURCE_FOLDER = "Direct"
        private const val DIRECT_FILE_FALLBACK = "Wallpaper"
        private val DIRECT_LINK_SCHEMES = setOf("http", "https")
    }
}

private fun WallpaperWithAlbums.toWallpaperItem(): WallpaperItem = wallpaper.toLibraryWallpaperItem()

private fun WallpaperEntity.toLibraryWallpaperItem(): WallpaperItem {
    val remoteId = remoteId ?: id.toString()
    val displayImageUrl = localUri ?: imageUrl
    val originalUrl = sourceUrl ?: localUri ?: imageUrl
    val adjustments = editSettings?.let(WallpaperAdjustmentsJson::decode)
    val crop = adjustments?.normalizedCropSettings()
        ?: WallpaperCropSettings.fromString(cropSettings)
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
        isDownloaded = isDownloaded,
        isFavorite = isFavorite,
        cropSettings = crop,
        customTitle = customTitle
    )
}

private fun AlbumWithWallpapers.toAlbumItem(): AlbumItem {
    val coverWallpaper = wallpapers.firstOrNull()
    val cover = coverWallpaper?.let { wallpaper -> wallpaper.localUri ?: wallpaper.imageUrl }
    val previewUrls = wallpapers.take(3).mapNotNull { it.localUri ?: it.imageUrl }
    val coverAspectRatio = coverWallpaper?.let { wallpaper ->
        val width = wallpaper.width?.toFloat()
        val height = wallpaper.height?.toFloat()
        if (width != null && height != null && width > 0f && height > 0f) width / height else null
    }
    return AlbumItem(
        id = album.id,
        title = album.title,
        wallpaperCount = wallpapers.size,
        coverImageUrl = cover,
        previewImageUrls = previewUrls,
        coverAspectRatio = coverAspectRatio,
        createdAt = album.createdAt,
        sortOrder = album.sortOrder
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


