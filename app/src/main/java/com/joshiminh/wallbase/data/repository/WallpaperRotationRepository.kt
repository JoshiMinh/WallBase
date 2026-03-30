package com.joshiminh.wallbase.data.repository

import androidx.room.withTransaction
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.joshiminh.wallbase.data.WallBaseDatabase
import com.joshiminh.wallbase.data.dao.RotationScheduleDao
import com.joshiminh.wallbase.data.dao.WallpaperDao
import com.joshiminh.wallbase.data.entity.rotation.RotationScheduleEntity
import com.joshiminh.wallbase.util.wallpapers.WallpaperTarget
import com.joshiminh.wallbase.util.wallpapers.rotation.WallpaperRotationDefaults
import com.joshiminh.wallbase.util.wallpapers.rotation.WallpaperRotationWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WallpaperRotationRepository(
    private val database: WallBaseDatabase,
    private val workManager: WorkManager
) {

    private val scheduleDao: RotationScheduleDao = database.rotationScheduleDao()
    private val wallpaperDao: WallpaperDao = database.wallpaperDao()

    fun observeSchedule(albumId: Long): Flow<WallpaperRotationSchedule?> {
        return scheduleDao.observeSchedule(albumId).map { entity -> entity?.toSchedule() }
    }

    suspend fun enableSchedule(albumId: Long, intervalMinutes: Long, target: WallpaperTarget): WallpaperRotationSchedule {
        val sanitized = intervalMinutes.coerceAtLeast(WallpaperRotationDefaults.MIN_INTERVAL_MINUTES)
        val entity = database.withTransaction {
            scheduleDao.disableAll()
            val existing = scheduleDao.getSchedule(albumId)
            val updated = (existing ?: RotationScheduleEntity(
                albumId = albumId,
                intervalMinutes = sanitized,
                target = target.name,
                isEnabled = true
            )).copy(
                intervalMinutes = sanitized,
                target = target.name,
                isEnabled = true
            )
            val id = scheduleDao.upsert(updated)
            if (updated.id == 0L) {
                updated.copy(id = id)
            } else {
                updated
            }
        }
        enqueuePeriodicWork(entity.intervalMinutes, entity.lastAppliedAt)
        return entity.toSchedule()
    }

    suspend fun disableSchedule(albumId: Long) {
        val shouldCancel = database.withTransaction {
            val existing = scheduleDao.getSchedule(albumId) ?: return@withTransaction false
            scheduleDao.updateEnabled(existing.id, false)
            scheduleDao.getActiveSchedule() == null
        }
        if (shouldCancel) {
            workManager.cancelUniqueWork(WallpaperRotationWorker.PERIODIC_WORK_NAME)
        }
    }

    suspend fun updateInterval(albumId: Long, intervalMinutes: Long) {
        val sanitized = intervalMinutes.coerceAtLeast(WallpaperRotationDefaults.MIN_INTERVAL_MINUTES)
        val schedule = database.withTransaction {
            val existing = scheduleDao.getSchedule(albumId)
            val entity = existing?.copy(intervalMinutes = sanitized)
                ?: RotationScheduleEntity(
                    albumId = albumId,
                    intervalMinutes = sanitized,
                    target = WallpaperRotationDefaults.DEFAULT_TARGET.name,
                    isEnabled = false
                )
            val id = scheduleDao.upsert(entity)
            if (entity.id == 0L) entity.copy(id = id) else entity
        }
        if (schedule.isEnabled) {
            enqueuePeriodicWork(schedule.intervalMinutes, schedule.lastAppliedAt)
        }
    }

    suspend fun updateTarget(albumId: Long, target: WallpaperTarget) {
        database.withTransaction {
            val existing = scheduleDao.getSchedule(albumId)
            val entity = existing?.copy(target = target.name)
                ?: RotationScheduleEntity(
                    albumId = albumId,
                    intervalMinutes = WallpaperRotationDefaults.DEFAULT_INTERVAL_MINUTES,
                    target = target.name,
                    isEnabled = false
                )
            scheduleDao.upsert(entity)
        }
    }

    fun triggerRotationNow() {
        val request = OneTimeWorkRequestBuilder<WallpaperRotationWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueueUniqueWork(
            WallpaperRotationWorker.ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    suspend fun getActiveSchedule(): WallpaperRotationSchedule? {
        return scheduleDao.getActiveSchedule()?.toSchedule()
    }

    suspend fun recordApplication(scheduleId: Long, wallpaperId: Long?, timestamp: Long) {
        scheduleDao.updateLastApplied(scheduleId, timestamp, wallpaperId)
    }

    suspend fun fetchAlbumWallpapers(albumId: Long) = wallpaperDao.getWallpapersForAlbum(albumId)

    private fun enqueuePeriodicWork(intervalMinutes: Long, lastAppliedAt: Long?) {
        val sanitized = intervalMinutes.coerceAtLeast(WallpaperRotationDefaults.MIN_INTERVAL_MINUTES)
        val builder = PeriodicWorkRequestBuilder<WallpaperRotationWorker>(sanitized, TimeUnit.MINUTES)
        val intervalMillis = TimeUnit.MINUTES.toMillis(sanitized)
        if (lastAppliedAt != null) {
            val elapsed = System.currentTimeMillis() - lastAppliedAt
            val remaining = intervalMillis - elapsed
            if (remaining > 0) {
                val initialDelay = remaining.coerceAtMost(intervalMillis)
                builder.setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            }
        }
        val request = builder.build()
        workManager.enqueueUniquePeriodicWork(
            WallpaperRotationWorker.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request
        )
    }
}

data class WallpaperRotationSchedule(
    val id: Long,
    val albumId: Long,
    val intervalMinutes: Long,
    val target: WallpaperTarget,
    val isEnabled: Boolean,
    val lastAppliedAt: Long?,
    val lastWallpaperId: Long?
)

private fun RotationScheduleEntity.toSchedule(): WallpaperRotationSchedule {
    return WallpaperRotationSchedule(
        id = id,
        albumId = albumId,
        intervalMinutes = intervalMinutes,
        target = WallpaperTarget.valueOf(target),
        isEnabled = isEnabled,
        lastAppliedAt = lastAppliedAt,
        lastWallpaperId = lastWallpaperId
    )
}