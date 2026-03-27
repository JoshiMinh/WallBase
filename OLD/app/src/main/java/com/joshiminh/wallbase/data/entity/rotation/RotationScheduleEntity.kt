package com.joshiminh.wallbase.data.entity.rotation

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.joshiminh.wallbase.data.entity.album.AlbumEntity

@Entity(
    tableName = "rotation_schedules",
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["album_id"],
            childColumns = ["album_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["album_id"], unique = true),
        Index(value = ["is_enabled"])
    ]
)
data class RotationScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "schedule_id")
    val id: Long = 0,
    @ColumnInfo(name = "album_id")
    val albumId: Long,
    @ColumnInfo(name = "interval_minutes")
    val intervalMinutes: Long,
    @ColumnInfo(name = "target")
    val target: String,
    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean,
    @ColumnInfo(name = "last_applied_at")
    val lastAppliedAt: Long? = null,
    @ColumnInfo(name = "last_wallpaper_id")
    val lastWallpaperId: Long? = null
)
