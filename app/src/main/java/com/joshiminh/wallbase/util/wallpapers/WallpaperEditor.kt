package com.joshiminh.wallbase.util.wallpapers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.DisplayMetrics
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import coil3.ImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Lightweight bitmap editing pipeline that mirrors the adjustments offered in
 * [WallpaperDetailScreen]. It loads an image via Coil and applies the selected
 * [WallpaperAdjustments] on a background thread before returning the
 * [EditedWallpaper].
 */
class WallpaperEditor(
    private val context: Context,
    private val imageLoader: ImageLoader = ImageLoader.Builder(context).build(),
) {

    private val metrics: DisplayMetrics
        get() = context.resources.displayMetrics

    suspend fun loadOriginalBitmap(model: Any): Bitmap {
        val request = ImageRequest.Builder(context)
            .data(model)
            .allowHardware(false)
            .build()
        val result = imageLoader.execute(request)
        val drawable = (result as? SuccessResult)?.image
            ?: error("Unable to load wallpaper for editing")
        return drawable.asDrawable(context.resources).toBitmap().copy(Bitmap.Config.ARGB_8888, true)
    }

    fun applyAdjustments(source: Bitmap, adjustments: WallpaperAdjustments): EditedWallpaper {
        var working = source
        if (!working.isMutable) {
            working = working.copy(working.config ?: Bitmap.Config.ARGB_8888, true)
        }

        val cropped = applyCrop(working, adjustments.crop)
        val filtered = applyFilter(cropped, adjustments.filter)
        val adjusted = applyBrightness(filtered, adjustments.brightness)
        return EditedWallpaper(adjusted)
    }

    private fun applyCrop(bitmap: Bitmap, crop: WallpaperCrop): Bitmap {
        val desiredRatio = when (crop) {
            WallpaperCrop.AUTO -> metrics.widthPixels.toFloat() / metrics.heightPixels.toFloat()
            WallpaperCrop.ORIGINAL -> return bitmap
            WallpaperCrop.SQUARE -> 1f
        }
        if (desiredRatio <= 0f) return bitmap
        val currentRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        if (abs(currentRatio - desiredRatio) < 0.01f) return bitmap
        val cropWidth: Int
        val cropHeight: Int
        if (currentRatio > desiredRatio) {
            cropHeight = bitmap.height
            cropWidth = (cropHeight * desiredRatio).roundToInt().coerceIn(1, bitmap.width)
        } else {
            cropWidth = bitmap.width
            cropHeight = (cropWidth / desiredRatio).roundToInt().coerceIn(1, bitmap.height)
        }
        val offsetX = ((bitmap.width - cropWidth) / 2f).roundToInt().coerceIn(0, bitmap.width - cropWidth)
        val offsetY = ((bitmap.height - cropHeight) / 2f).roundToInt().coerceIn(0, bitmap.height - cropHeight)
        return Bitmap.createBitmap(bitmap, offsetX, offsetY, cropWidth, cropHeight)
    }

    private fun applyFilter(bitmap: Bitmap, filter: WallpaperFilter): Bitmap {
        val matrix = when (filter) {
            WallpaperFilter.NONE -> return bitmap
            WallpaperFilter.GRAYSCALE -> ColorMatrix().apply { setSaturation(0f) }
            WallpaperFilter.SEPIA -> ColorMatrix(
                floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
        }
        return bitmap.copyWithMatrix(matrix)
    }

    private fun applyBrightness(bitmap: Bitmap, brightness: Float): Bitmap {
        if (brightness == 0f) return bitmap
        val translate = (brightness * 255f).coerceIn(-255f, 255f)
        val matrix = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, translate,
                0f, 1f, 0f, 0f, translate,
                0f, 0f, 1f, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        return bitmap.copyWithMatrix(matrix)
    }

    private fun Bitmap.copyWithMatrix(matrix: ColorMatrix): Bitmap {
        val copy = createBitmap(width, height, config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(copy)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(this, 0f, 0f, paint)
        return copy
    }
}
