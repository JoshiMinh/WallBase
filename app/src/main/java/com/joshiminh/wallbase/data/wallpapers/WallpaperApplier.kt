package com.joshiminh.wallbase.data.wallpapers

import android.Manifest
import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.graphics.createBitmap
import coil3.asDrawable

class WallpaperApplier(
    private val context: Context,
    private val imageLoader: ImageLoader = ImageLoader.Builder(context).build()
) {
    /**
     * Applies the wallpaper from [imageUrl] to the given [target].
     * Returns Result.success(Unit) on success, or Result.failure(e) on error.
     */
    suspend fun apply(imageUrl: String, target: WallpaperTarget): Result<Unit> =
        withContext(Dispatchers.IO) {
            // Runtime permission check to satisfy Lint and avoid SecurityException.
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.SET_WALLPAPER
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                return@withContext Result.failure(
                    SecurityException("android.permission.SET_WALLPAPER not granted")
                )
            }

            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .build()

                val result = imageLoader.execute(request)
                val image = (result as? SuccessResult)?.image
                    ?: error("Unable to load wallpaper preview")
                val drawable = image.asDrawable(context.resources)   // <-- replaces .drawable
                val bitmap = drawable.toSoftwareBitmap()

                // Try Samsung's manager first. If it returns false, fall back to the platform manager.
                if (!applyWithSamsungManager(bitmap, target)) {
                    applyWithWallpaperManager(bitmap, target)
                }
            }
        }

    @SuppressLint("MissingPermission") // Safe: we performed a runtime permission check in apply()
    private fun applyWithWallpaperManager(bitmap: Bitmap, target: WallpaperTarget) {
        val manager = WallpaperManager.getInstance(context)
        val flags = when (target) {
            WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
            WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
            WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        }
        manager.setBitmap(bitmap, null, true, flags)
    }

    // Uses Samsung’s SemWallpaperManager via reflection. Returns true if handled.
    @SuppressLint("PrivateApi", "SoonBlockedPrivateApi", "DiscouragedPrivateApi")
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
        val source = bitmap
        val cfg = source.config ?: Bitmap.Config.ARGB_8888
        return if (source.config == Bitmap.Config.HARDWARE
        ) {
            // Convert from hardware to software-config bitmap
            createBitmap(targetWidth, targetHeight).also { out ->
                Canvas(out).drawBitmap(source, 0f, 0f, null)
            }
        } else {
            // Ensure mutable
            source.copy(cfg, /* isMutable = */ true)
        }
    }

    return toBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
}