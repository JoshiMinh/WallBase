package com.joshiminh.wallbase.data.wallpapers.platform

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import com.joshiminh.wallbase.data.wallpapers.WallpaperTarget

internal object SamsungWallpaperHandler : WallpaperPlatformHandler {

    override fun isEligible(context: Context): Boolean {
        return Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }

    override fun buildPreviewIntent(context: Context, uri: Uri): Intent {
        return Intent(Intent.ACTION_ATTACH_DATA).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            setDataAndType(uri, "image/*")
            putExtra("mimeType", "image/*")
            putExtra("from_wallpaper", true)
        }
    }

    @SuppressLint("PrivateApi", "SoonBlockedPrivateApi", "DiscouragedPrivateApi")
    override fun applyWallpaper(
        context: Context,
        bitmap: Bitmap,
        target: WallpaperTarget
    ): Boolean {
        if (!isEligible(context)) return false

        return runCatching {
            val clazz = Class.forName("com.samsung.android.wallpaper.SemWallpaperManager")
            val getInstance = clazz.getMethod("getInstance", Context::class.java)
            val instance = getInstance.invoke(null, context)
            val homeFlag = clazz.getField("FLAG_HOME_SCREEN").getInt(null)
            val lockFlag = clazz.getField("FLAG_LOCK_SCREEN").getInt(null)
            val setBitmap = clazz.getMethod(
                "setBitmap",
                Bitmap::class.java,
                Int::class.javaPrimitiveType
            )

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
