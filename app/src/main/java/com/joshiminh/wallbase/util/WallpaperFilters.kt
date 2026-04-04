package com.joshiminh.wallbase.util

import com.joshiminh.wallbase.data.entity.WallpaperItem

/**
 * Filters wallpapers based on aspect ratio and orientation preferences.
 * Horizontal wallpapers are those with an aspect ratio > 1.2 (wider than tall).
 */
fun List<WallpaperItem>.filterByHorizontalPreference(showHorizontal: Boolean): List<WallpaperItem> {
    if (showHorizontal) return this

    return filter { wallpaper ->
        val aspectRatio = wallpaper.aspectRatio ?: return@filter true
        // Exclude horizontal wallpapers (aspect ratio > 1.2 means wider than tall)
        aspectRatio <= 1.2f
    }
}

