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
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import coil3.ImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class WallpaperApplier(
    val context: Context,
    val imageLoader: ImageLoader = ImageLoader.Builder(context).build()
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
                val bitmap = loadWallpaperBitmap(imageUrl)

                // Try Samsung's manager first. If it returns false, fall back to the platform manager.
                if (!applyWithSamsungManager(bitmap, target)) {
                    applyWithWallpaperManager(bitmap, target)
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
            val intent = buildPreviewIntent(uri)
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

private fun WallpaperApplier.buildPreviewIntent(uri: Uri): Intent {
    val manager = WallpaperManager.getInstance(context)
    val cropIntent = runCatching { manager.getCropAndSetWallpaperIntent(uri) }.getOrNull()
    val intent = cropIntent ?: Intent(Intent.ACTION_ATTACH_DATA).apply {
        addCategory(Intent.CATEGORY_DEFAULT)
        setDataAndType(uri, "image/*")
        putExtra("mimeType", "image/*")
        putExtra("from_wallpaper", true)
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    intent.clipData = ClipData.newRawUri("wallpaper", uri)
    return intent
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