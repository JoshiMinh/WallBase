package com.joshiminh.wallbase.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "album_wallpaper_cross_ref",
    primaryKeys = ["album_id", "wallpaper_id"],
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["album_id"],
            childColumns = ["album_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WallpaperEntity::class,
            parentColumns = ["wallpaper_id"],
            childColumns = ["wallpaper_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["wallpaper_id"])
    ]
)
data class AlbumWallpaperCrossRef(
    @ColumnInfo(name = "album_id")
    val albumId: Long,
    @ColumnInfo(name = "wallpaper_id")
    val wallpaperId: Long
)
