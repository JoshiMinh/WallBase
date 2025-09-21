package com.joshiminh.wallbase.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.joshiminh.wallbase.data.entity.album.AlbumEntity
import com.joshiminh.wallbase.data.entity.album.AlbumWallpaperCrossRef
import com.joshiminh.wallbase.data.entity.album.AlbumWithWallpapers
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlbum(album: AlbumEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlbums(albums: List<AlbumEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(refs: List<AlbumWallpaperCrossRef>): List<Long>

    @Query("SELECT * FROM albums WHERE title = :title LIMIT 1")
    suspend fun findAlbumByTitle(title: String): AlbumEntity?

    @Transaction
    @Query("SELECT * FROM albums ORDER BY sort_order, title")
    fun observeAlbumsWithWallpapers(): Flow<List<AlbumWithWallpapers>>

    @Transaction
    @Query("SELECT * FROM albums WHERE album_id = :albumId LIMIT 1")
    fun observeAlbumWithWallpapers(albumId: Long): Flow<AlbumWithWallpapers?>
}