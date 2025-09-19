package com.joshiminh.wallbase.data.wallpapers

import android.Manifest
import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import coil3.ImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.joshiminh.wallbase.data.wallpapers.platform.PixelWallpaperHandler
import com.joshiminh.wallbase.data.wallpapers.platform.SamsungWallpaperHandler
import com.joshiminh.wallbase.data.wallpapers.platform.WallpaperPlatformHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class WallpaperApplier(
    val context: Context,
    val imageLoader: ImageLoader = ImageLoader.Builder(context).build()
) {

    private val platformHandlers: List<WallpaperPlatformHandler> = listOf(
        SamsungWallpaperHandler,
        PixelWallpaperHandler
    )

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
                val bitmap = loadWallpaperBitmap(imageUrl)
                val cropped = cropForApplication(bitmap)

                val handled = platformHandlers
                    .firstOrNull { handler -> handler.isEligible(context) && handler.applyWallpaper(context, cropped, target) }

                if (handled == null) {
                    applyWithWallpaperManager(cropped, target)
                }
            }
        }

    suspend fun createSystemPreview(
        imageUrl: String,
        target: WallpaperTarget
    ): Result<PreviewData> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = loadWallpaperBitmap(imageUrl)
            val file = createPreviewFile(bitmap)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = resolvePreviewIntent(uri)
            ensurePreviewHandlerAvailable(intent)
            grantPreviewPermissions(intent, uri)
            PreviewData(intent = intent, uri = uri, filePath = file.absolutePath)
        }
    }

    fun cleanupPreview(preview: PreviewData) {
        context.revokeUriPermission(preview.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val file = File(preview.filePath)
        if (file.exists()) {
            file.delete()
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

}

data class PreviewData(val intent: Intent, val uri: Uri, val filePath: String)

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

private fun WallpaperApplier.resolvePreviewIntent(uri: Uri): Intent {
    val handlerIntent = platformHandlers
        .firstOrNull { it.isEligible(context) }
        ?.buildPreviewIntent(context, uri)

    val baseIntent = handlerIntent ?: run {
        val manager = WallpaperManager.getInstance(context)
        runCatching { manager.getCropAndSetWallpaperIntent(uri) }.getOrNull()
            ?: Intent(Intent.ACTION_ATTACH_DATA).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                setDataAndType(uri, "image/*")
                putExtra("mimeType", "image/*")
                putExtra("from_wallpaper", true)
            }
    }

    return baseIntent.apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri("wallpaper", uri)
    }
}

@SuppressLint("QueryPermissionsNeeded")
private fun WallpaperApplier.ensurePreviewHandlerAvailable(intent: Intent) {
    val resolveInfos = context.packageManager.queryIntentActivities(
        intent,
        PackageManager.MATCH_DEFAULT_ONLY
    )
    if (resolveInfos.isEmpty()) {
        throw ActivityNotFoundException("No activity found to handle wallpaper preview")
    }
}

@SuppressLint("QueryPermissionsNeeded")
private fun WallpaperApplier.grantPreviewPermissions(intent: Intent, uri: Uri) {
    val resolveInfos = context.packageManager.queryIntentActivities(
        intent,
        PackageManager.MATCH_DEFAULT_ONLY
    )
    resolveInfos.forEach { info ->
        context.grantUriPermission(
            info.activityInfo.packageName,
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
}

private fun WallpaperApplier.createPreviewFile(bitmap: Bitmap): File {
    val directory = File(context.cacheDir, "wallpaper_previews").apply {
        if (!exists()) {
            mkdirs()
        }
    }
    val file = File.createTempFile("preview_", ".jpg", directory)
    FileOutputStream(file).use { output ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
        output.flush()
    }
    return file
}

private suspend fun WallpaperApplier.loadWallpaperBitmap(imageUrl: String): Bitmap {
    val request = ImageRequest.Builder(context)
        .data(imageUrl)
        .allowHardware(false)
        .build()

    val result = imageLoader.execute(request)
    val image = (result as? SuccessResult)?.image
        ?: error("Unable to load wallpaper preview")
    val drawable = image.asDrawable(context.resources)
    return drawable.toSoftwareBitmap()
}

private fun WallpaperApplier.cropForApplication(bitmap: Bitmap): Bitmap {
    val metrics = context.resources.displayMetrics
    val targetWidth = max(metrics.widthPixels, 1)
    val targetHeight = max(metrics.heightPixels, 1)

    return bitmap.centerCropToAspectRatio(targetWidth, targetHeight)
}

private fun Bitmap.centerCropToAspectRatio(targetWidth: Int, targetHeight: Int): Bitmap {
    if (targetWidth <= 0 || targetHeight <= 0) return this
    val sourceWidth = width
    val sourceHeight = height
    if (sourceWidth <= 0 || sourceHeight <= 0) return this

    val desiredRatio = targetWidth.toFloat() / targetHeight.toFloat()
    val currentRatio = sourceWidth.toFloat() / sourceHeight.toFloat()
    if (abs(currentRatio - desiredRatio) < 0.01f) return this

    val cropWidth: Int
    val cropHeight: Int
    if (currentRatio > desiredRatio) {
        cropHeight = sourceHeight
        cropWidth = (sourceHeight * desiredRatio).roundToInt().coerceIn(1, sourceWidth)
    } else {
        cropWidth = sourceWidth
        cropHeight = (sourceWidth / desiredRatio).roundToInt().coerceIn(1, sourceHeight)
    }

    val offsetX = ((sourceWidth - cropWidth) / 2f).roundToInt().coerceIn(0, max(sourceWidth - cropWidth, 0))
    val offsetY = ((sourceHeight - cropHeight) / 2f).roundToInt().coerceIn(0, max(sourceHeight - cropHeight, 0))

    val safeWidth = min(cropWidth, sourceWidth - offsetX)
    val safeHeight = min(cropHeight, sourceHeight - offsetY)
    if (safeWidth <= 0 || safeHeight <= 0) return this

    return Bitmap.createBitmap(this, offsetX, offsetY, safeWidth, safeHeight)
}