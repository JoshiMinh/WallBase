package com.joshiminh.wallbase.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.joshiminh.wallbase.data.entity.rotation.RotationScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RotationScheduleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(schedule: RotationScheduleEntity): Long

    @Query("SELECT * FROM rotation_schedules WHERE album_id = :albumId LIMIT 1")
    fun observeSchedule(albumId: Long): Flow<RotationScheduleEntity?>

    @Query("SELECT * FROM rotation_schedules WHERE album_id = :albumId LIMIT 1")
    suspend fun getSchedule(albumId: Long): RotationScheduleEntity?

    @Query("UPDATE rotation_schedules SET is_enabled = :enabled WHERE schedule_id = :scheduleId")
    suspend fun updateEnabled(scheduleId: Long, enabled: Boolean)

    @Query("UPDATE rotation_schedules SET interval_minutes = :interval WHERE schedule_id = :scheduleId")
    suspend fun updateInterval(scheduleId: Long, interval: Long)

    @Query("UPDATE rotation_schedules SET target = :target WHERE schedule_id = :scheduleId")
    suspend fun updateTarget(scheduleId: Long, target: String)

    @Query("UPDATE rotation_schedules SET last_applied_at = :appliedAt, last_wallpaper_id = :wallpaperId WHERE schedule_id = :scheduleId")
    suspend fun updateLastApplied(scheduleId: Long, appliedAt: Long?, wallpaperId: Long?)

    @Query("UPDATE rotation_schedules SET is_enabled = 0")
    suspend fun disableAll()

    @Query("SELECT * FROM rotation_schedules WHERE is_enabled = 1 LIMIT 1")
    suspend fun getActiveSchedule(): RotationScheduleEntity?
}
