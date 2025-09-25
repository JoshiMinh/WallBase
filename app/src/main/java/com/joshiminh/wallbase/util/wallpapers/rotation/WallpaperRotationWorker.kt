package com.joshiminh.wallbase.util.wallpapers.rotation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.joshiminh.wallbase.R
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperEntity
import com.joshiminh.wallbase.util.network.ServiceLocator
import com.joshiminh.wallbase.util.wallpapers.WallpaperAdjustments
import com.joshiminh.wallbase.util.wallpapers.WallpaperApplier
import com.joshiminh.wallbase.util.wallpapers.WallpaperEditor

class WallpaperRotationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        ServiceLocator.initialize(applicationContext)
        val rotationRepository = ServiceLocator.rotationRepository
        val schedule = rotationRepository.getActiveSchedule() ?: return Result.success()
        val wallpapers = rotationRepository.fetchAlbumWallpapers(schedule.albumId)
        if (wallpapers.isEmpty()) {
            rotationRepository.disableSchedule(schedule.albumId)
            return Result.success()
        }
        val next = selectNextWallpaper(wallpapers, schedule.lastWallpaperId) ?: return Result.success()
        setForeground(createForegroundInfo())
        val model: Any = next.localUri?.takeIf { it.isNotBlank() }?.toUri() ?: next.imageUrl
        val editor = WallpaperEditor(applicationContext)
        val applier = WallpaperApplier(applicationContext)
        val original = runCatching { editor.loadOriginalBitmap(model) }.getOrElse {
            return Result.retry()
        }
        val processed = try {
            editor.applyAdjustments(original, WallpaperAdjustments())
        } finally {
            if (!original.isRecycled) {
                original.recycle()
            }
        }
        val applyResult = applier.apply(processed, schedule.target)
        processed.bitmap.recycle()
        return applyResult.fold(
            onSuccess = {
                rotationRepository.recordApplication(schedule.id, next.id, System.currentTimeMillis())
                Result.success()
            },
            onFailure = { throwable ->
                if (throwable is SecurityException) {
                    rotationRepository.disableSchedule(schedule.albumId)
                    Result.success()
                } else {
                    Result.retry()
                }
            }
        )
    }

    private fun selectNextWallpaper(
        wallpapers: List<WallpaperEntity>,
        lastId: Long?
    ): WallpaperEntity? {
        if (wallpapers.isEmpty()) return null
        if (lastId == null) return wallpapers.first()
        val index = wallpapers.indexOfFirst { it.id == lastId }
        if (index == -1) return wallpapers.first()
        val nextIndex = if (index == wallpapers.lastIndex) 0 else index + 1
        return wallpapers.getOrNull(nextIndex)
    }

    companion object {
        const val PERIODIC_WORK_NAME = "wallpaper_rotation_periodic"
        const val ONE_TIME_WORK_NAME = "wallpaper_rotation_once"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID_SUFFIX = "wallpaper_rotation"
    }


    private fun createForegroundInfo(): ForegroundInfo {
        val context = applicationContext
        val channelId = "${context.packageName}.$CHANNEL_ID_SUFFIX"
        val channelName = "Wallpaper rotation"
        val notificationTitle = "Wallpaper rotation running"
        val notificationText = "Applying scheduled wallpaper rotation."
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            description = notificationTitle
        }
        manager?.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_wallpaper_rotation)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }
}