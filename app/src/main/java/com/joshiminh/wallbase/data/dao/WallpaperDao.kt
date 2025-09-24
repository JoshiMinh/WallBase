package com.joshiminh.wallbase.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperEntity
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperWithAlbums
import kotlinx.coroutines.flow.Flow

@Dao
interface WallpaperDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWallpapers(wallpapers: List<WallpaperEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWallpaper(wallpaper: WallpaperEntity): Long

    @Transaction
    @Query("SELECT * FROM wallpapers ORDER BY added_at DESC")
    fun observeWallpapersWithAlbums(): Flow<List<WallpaperWithAlbums>>

    @Query("SELECT EXISTS(SELECT 1 FROM wallpapers WHERE source_key = :sourceKey AND remote_id = :remoteId)")
    suspend fun existsByRemoteId(sourceKey: String, remoteId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM wallpapers WHERE source_key = :sourceKey AND image_url = :imageUrl)")
    suspend fun existsByImageUrl(sourceKey: String, imageUrl: String): Boolean

    @Query("SELECT wallpaper_id FROM wallpapers WHERE source_key = :sourceKey AND remote_id = :remoteId LIMIT 1")
    suspend fun findIdByRemoteId(sourceKey: String, remoteId: String): Long?

    @Query("SELECT wallpaper_id FROM wallpapers WHERE source_key = :sourceKey AND image_url = :imageUrl LIMIT 1")
    suspend fun findIdByImageUrl(sourceKey: String, imageUrl: String): Long?

    @Query("SELECT * FROM wallpapers WHERE source_key = :sourceKey AND remote_id = :remoteId LIMIT 1")
    suspend fun getBySourceKeyAndRemoteId(sourceKey: String, remoteId: String): WallpaperEntity?

    @Query("SELECT * FROM wallpapers WHERE source_key = :sourceKey AND image_url = :imageUrl LIMIT 1")
    suspend fun getBySourceKeyAndImageUrl(sourceKey: String, imageUrl: String): WallpaperEntity?

    @Query("SELECT * FROM wallpapers WHERE source_key = :sourceKey")
    suspend fun getWallpapersBySource(sourceKey: String): List<WallpaperEntity>

    @Query("SELECT * FROM wallpapers WHERE local_uri IS NOT NULL AND TRIM(local_uri) != ''")
    suspend fun getWallpapersWithLocalMedia(): List<WallpaperEntity>

    @Query(
        "SELECT w.* FROM wallpapers w " +
            "INNER JOIN album_wallpaper_cross_ref aw ON aw.wallpaper_id = w.wallpaper_id " +
            "WHERE aw.album_id = :albumId ORDER BY w.added_at"
    )
    suspend fun getWallpapersForAlbum(albumId: Long): List<WallpaperEntity>

    @Query("DELETE FROM wallpapers WHERE source_key = :sourceKey AND remote_id = :remoteId")
    suspend fun deleteBySourceKeyAndRemoteId(sourceKey: String, remoteId: String): Int

    @Query("DELETE FROM wallpapers WHERE wallpaper_id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM wallpapers WHERE source_key = :sourceKey AND image_url = :imageUrl")
    suspend fun deleteByImageUrl(sourceKey: String, imageUrl: String): Int

    @Query("DELETE FROM wallpapers WHERE source_key = :sourceKey")
    suspend fun deleteBySourceKey(sourceKey: String): Int

    @Query("SELECT * FROM wallpapers WHERE wallpaper_id = :id LIMIT 1")
    suspend fun getById(id: Long): WallpaperEntity?

    @Query(
        "UPDATE wallpapers SET local_uri = :localUri, is_downloaded = :isDownloaded, " +
            "file_size_bytes = :fileSize, updated_at = :updatedAt WHERE wallpaper_id = :id"
    )
    suspend fun updateDownloadState(
        id: Long,
        localUri: String?,
        isDownloaded: Boolean,
        fileSize: Long?,
        updatedAt: Long
    )
}
