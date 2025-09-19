package com.joshiminh.wallbase.data.local.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class AlbumWithWallpapers(
    @Embedded val album: AlbumEntity,
    @Relation(
        parentColumn = "album_id",
        entityColumn = "wallpaper_id",
        associateBy = Junction(
            value = AlbumWallpaperCrossRef::class,
            parentColumn = "album_id",
            entityColumn = "wallpaper_id"
        )
    )
    val wallpapers: List<WallpaperEntity>
)
