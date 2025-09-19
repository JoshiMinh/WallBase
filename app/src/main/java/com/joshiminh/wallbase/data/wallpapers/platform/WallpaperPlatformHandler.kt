package com.joshiminh.wallbase.data.wallpapers.platform

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import com.joshiminh.wallbase.data.wallpapers.WallpaperTarget

/**
 * Platform-specific hooks that allow devices to provide their own wallpaper
 * preview or application logic. Implementations should return `false` from
 * [applyWallpaper] when the request needs to be forwarded to the default
 * [android.app.WallpaperManager].
 */
internal interface WallpaperPlatformHandler {

    /**
     * @return `true` when the handler should be used on the current device.
     */
    fun isEligible(context: Context): Boolean

    /**
     * Optionally creates a platform preview intent for [uri]. Returning `null`
     * will fall back to the default preview flow.
     */
    fun buildPreviewIntent(context: Context, uri: Uri): Intent?

    /**
     * Applies the [bitmap] to the requested [target]. Returns `true` if the
     * platform consumed the request, or `false` to let the default manager run.
     */
    fun applyWallpaper(context: Context, bitmap: Bitmap, target: WallpaperTarget): Boolean
}
