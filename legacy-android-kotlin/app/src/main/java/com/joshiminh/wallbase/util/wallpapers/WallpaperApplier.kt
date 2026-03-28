package com.joshiminh.wallbase.util.wallpapers

import android.Manifest
import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.joshiminh.wallbase.util.wallpapers.platform.PixelWallpaperHandler
import com.joshiminh.wallbase.util.wallpapers.platform.SamsungWallpaperHandler
import com.joshiminh.wallbase.util.wallpapers.platform.WallpaperPlatformHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class WallpaperApplier(
    val context: Context,
) {

    val platformHandlers: List<WallpaperPlatformHandler> = listOf(
        SamsungWallpaperHandler,
        PixelWallpaperHandler
    )

    /**
     * Applies the provided [EditedWallpaper] to the selected [target].
     * Returns Result.success(Unit) on success, or Result.failure(e) on error.
     */
    suspend fun apply(wallpaper: EditedWallpaper, target: WallpaperTarget): Result<Unit> =
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
                val bitmap = wallpaper.bitmap

                val handled = platformHandlers
                    .firstOrNull { handler -> handler.isEligible(context) && handler.applyWallpaper(context, bitmap, target) }

                if (handled == null) {
                    applyWithWallpaperManager(bitmap, target)
                }
            }
        }

    suspend fun createSystemPreview(
        wallpaper: EditedWallpaper,
        target: WallpaperTarget
    ): Result<PreviewData> = withContext(Dispatchers.IO) {
        runCatching {
            val file = createPreviewFile(wallpaper.bitmap)
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
        val hasLockPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SET_WALLPAPER_HINTS
        ) == PackageManager.PERMISSION_GRANTED

        val includeLock = hasLockPermission &&
            (target == WallpaperTarget.LOCK || target == WallpaperTarget.BOTH)
        val includeSystem = target == WallpaperTarget.HOME || target == WallpaperTarget.BOTH ||
            (!hasLockPermission && target == WallpaperTarget.LOCK)

        val flags = (if (includeSystem) WallpaperManager.FLAG_SYSTEM else 0) or
            (if (includeLock) WallpaperManager.FLAG_LOCK else 0)
        manager.setBitmap(bitmap, null, true, flags)
    }

}

data class PreviewData(val intent: Intent, val uri: Uri, val filePath: String)

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
