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
}
