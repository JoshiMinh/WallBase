package com.joshiminh.wallbase.data.wallpapers

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WallpaperApplier(
    private val context: Context,
    private val imageLoader: ImageLoader = ImageLoader.Builder(context).build()
) {
    suspend fun apply(imageUrl: String, target: WallpaperTarget): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .build()
            val result = imageLoader.execute(request)
            val drawable = (result as? SuccessResult)?.drawable
                ?: throw IllegalStateException("Unable to load wallpaper preview")
            val bitmap = drawable.toSoftwareBitmap()

            if (!applyWithSamsungManager(bitmap, target)) {
                applyWithWallpaperManager(bitmap, target)
            }
        }
    }

    private fun applyWithWallpaperManager(bitmap: Bitmap, target: WallpaperTarget) {
        val manager = WallpaperManager.getInstance(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val flags = when (target) {
                WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
                WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
                WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            }
            manager.setBitmap(bitmap, null, true, flags)
        } else {
            manager.setBitmap(bitmap)
        }
    }

    private fun applyWithSamsungManager(bitmap: Bitmap, target: WallpaperTarget): Boolean {
        if (!Build.MANUFACTURER.equals("samsung", ignoreCase = true)) return false
        return runCatching {
            val clazz = Class.forName("com.samsung.android.wallpaper.SemWallpaperManager")
            val getInstance = clazz.getMethod("getInstance", Context::class.java)
            val instance = getInstance.invoke(null, context)
            val homeFlag = clazz.getField("FLAG_HOME_SCREEN").getInt(null)
            val lockFlag = clazz.getField("FLAG_LOCK_SCREEN").getInt(null)
            val setBitmap = clazz.getMethod("setBitmap", Bitmap::class.java, Int::class.javaPrimitiveType)

            when (target) {
                WallpaperTarget.HOME -> setBitmap.invoke(instance, bitmap, homeFlag)
                WallpaperTarget.LOCK -> setBitmap.invoke(instance, bitmap, lockFlag)
                WallpaperTarget.BOTH -> {
                    setBitmap.invoke(instance, bitmap, homeFlag)
                    setBitmap.invoke(instance, bitmap, lockFlag)
                }
            }
            true
        }.getOrElse { false }
    }
}

private fun Drawable.toSoftwareBitmap(): Bitmap {
    val targetWidth = intrinsicWidth.takeIf { it > 0 } ?: 1
    val targetHeight = intrinsicHeight.takeIf { it > 0 } ?: 1

    if (this is BitmapDrawable) {
        val sourceBitmap = bitmap
        val defaultConfig = sourceBitmap.config ?: Bitmap.Config.ARGB_8888
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && sourceBitmap.config == Bitmap.Config.HARDWARE) {
            Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).also { output ->
                Canvas(output).drawBitmap(sourceBitmap, 0f, 0f, null)
            }
        } else {
            sourceBitmap.copy(defaultConfig, true)
        }
    }

    return toBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
}
