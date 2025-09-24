package com.joshiminh.wallbase.util.wallpapers

import android.graphics.Bitmap

/**
 * Encapsulates the user controlled edits applied to a wallpaper before it is
 * downloaded or set on the device.
 */
data class WallpaperAdjustments(
    val brightness: Float = 0f,
    val filter: WallpaperFilter = WallpaperFilter.NONE,
    val crop: WallpaperCrop = WallpaperCrop.AUTO,
) {
    val isIdentity: Boolean
        get() =
            brightness == 0f &&
                filter == WallpaperFilter.NONE &&
                crop == WallpaperCrop.AUTO
}

/**
 * Describes high level filter effects that can be composed through a
 * [android.graphics.ColorMatrix].
 */
enum class WallpaperFilter {
    NONE,
    GRAYSCALE,
    SEPIA,
}

/**
 * Simple crop presets that keep the workflow lightweight while offering a few
 * common aspect ratios.
 */
enum class WallpaperCrop {
    /**
     * Crop to the device's current screen aspect ratio. This mirrors the
     * previous behaviour of [WallpaperApplier] before editing was supported.
     */
    AUTO,

    /** Preserve the original bitmap dimensions. */
    ORIGINAL,

    /** Produce a centered square crop. */
    SQUARE,
}

/**
 * Result returned by [WallpaperEditor] after applying [WallpaperAdjustments].
 */
data class EditedWallpaper(
    val bitmap: Bitmap,
)
