package com.joshiminh.wallbase.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "wallpapers",
    foreignKeys = [
        ForeignKey(
            entity = SourceEntity::class,
            parentColumns = ["key"],
            childColumns = ["source_key"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["source_key"]),
        Index(value = ["added_at"]),
        Index(value = ["remote_id", "source_key"], unique = true),
        Index(value = ["local_uri"], unique = true)
    ]
)
data class WallpaperEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "wallpaper_id")
    val id: Long = 0,
    @ColumnInfo(name = "source_key")
    val sourceKey: String,
    @ColumnInfo(name = "remote_id")
    val remoteId: String? = null,
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "description")
    val description: String? = null,
    @ColumnInfo(name = "image_url")
    val imageUrl: String,
    @ColumnInfo(name = "source_url")
    val sourceUrl: String? = null,
    @ColumnInfo(name = "local_uri")
    val localUri: String? = null,
    @ColumnInfo(name = "width")
    val width: Int? = null,
    @ColumnInfo(name = "height")
    val height: Int? = null,
    @ColumnInfo(name = "color_palette")
    val colorPalette: String? = null,
    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long? = null,
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,
    @ColumnInfo(name = "is_downloaded")
    val isDownloaded: Boolean = false,
    @ColumnInfo(name = "applied_at")
    val appliedAt: Long? = null,
    @ColumnInfo(name = "added_at")
    val addedAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
