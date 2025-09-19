package com.joshiminh.wallbase.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.joshiminh.wallbase.data.local.entity.AlbumEntity
import com.joshiminh.wallbase.data.local.entity.AlbumWallpaperCrossRef
import com.joshiminh.wallbase.data.local.entity.AlbumWithWallpapers
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlbum(album: AlbumEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlbums(albums: List<AlbumEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(refs: List<AlbumWallpaperCrossRef>)

    @Transaction
    @Query("SELECT * FROM albums ORDER BY sort_order, title")
    fun observeAlbumsWithWallpapers(): Flow<List<AlbumWithWallpapers>>
}
