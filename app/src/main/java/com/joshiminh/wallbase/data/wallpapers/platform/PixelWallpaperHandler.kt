package com.joshiminh.wallbase.data.wallpapers.platform

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import com.joshiminh.wallbase.data.wallpapers.WallpaperTarget

internal object PixelWallpaperHandler : WallpaperPlatformHandler {

    override fun isEligible(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER?.lowercase()
        val brand = Build.BRAND?.lowercase()
        val model = Build.MODEL?.lowercase()
        return manufacturer == "google" || brand == "google" || model?.contains("pixel") == true
    }

    override fun buildPreviewIntent(context: Context, uri: Uri): Intent {
        return Intent(Intent.ACTION_ATTACH_DATA).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            setDataAndType(uri, "image/*")
            putExtra("mimeType", "image/*")
            putExtra("from_wallpaper", true)
        }
    }

    override fun applyWallpaper(
        context: Context,
        bitmap: Bitmap,
        target: WallpaperTarget
    ): Boolean {
        // Pixels defer to the platform wallpaper manager for application.
        return false
    }
}
