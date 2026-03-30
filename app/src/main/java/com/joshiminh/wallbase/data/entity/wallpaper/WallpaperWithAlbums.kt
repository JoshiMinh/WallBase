package com.joshiminh.wallbase.data.entity.wallpaper

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.joshiminh.wallbase.data.entity.album.AlbumEntity
import com.joshiminh.wallbase.data.entity.album.AlbumWallpaperCrossRef

data class WallpaperWithAlbums(
    @Embedded val wallpaper: WallpaperEntity,
    @Relation(
        parentColumn = "wallpaper_id",
        entityColumn = "album_id",
        associateBy = Junction(
            value = AlbumWallpaperCrossRef::class,
            parentColumn = "wallpaper_id",
            entityColumn = "album_id"
        )
    )
    val albums: List<AlbumEntity>
)