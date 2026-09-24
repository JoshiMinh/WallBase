package com.joshiminh.wallbase.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "category_wallpaper_cross_ref",
    primaryKeys = ["category_id", "wallpaper_id"],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["category_id"],
            childColumns = ["category_id"],
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
data class CategoryWallpaperCrossRef(
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    @ColumnInfo(name = "wallpaper_id")
    val wallpaperId: Long
)
