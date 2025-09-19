package com.joshiminh.wallbase.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.joshiminh.wallbase.data.local.entity.WallpaperEntity
import com.joshiminh.wallbase.data.local.entity.WallpaperWithAlbums
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

    @Query("DELETE FROM wallpapers WHERE source_key = :sourceKey AND remote_id = :remoteId")
    suspend fun deleteBySourceKeyAndRemoteId(sourceKey: String, remoteId: String): Int

    @Query("DELETE FROM wallpapers WHERE wallpaper_id = :id")
    suspend fun deleteById(id: Long): Int
}
