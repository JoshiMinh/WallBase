package com.joshiminh.wallbase.util

import com.joshiminh.wallbase.data.entity.WallpaperItem

/**
 * Tri-state filter for downloaded wallpapers
 * SHOW_ALL: Display all wallpapers (filter not applied)
 * SHOW_DOWNLOADED: Display only downloaded wallpapers
 * HIDE_DOWNLOADED: Display only non-downloaded wallpapers
 */
enum class DownloadedFilter {
    SHOW_ALL,
    SHOW_DOWNLOADED,
    HIDE_DOWNLOADED
}

/**
 * Filters wallpapers based on download status.
 *
 * @param filter The download filter state (SHOW_ALL, SHOW_DOWNLOADED, or HIDE_DOWNLOADED)
 * @return Filtered list of wallpapers based on the filter state
 */
fun List<WallpaperItem>.filterByDownloadStatus(filter: DownloadedFilter): List<WallpaperItem> {
    return when (filter) {
        DownloadedFilter.SHOW_ALL -> this
        DownloadedFilter.SHOW_DOWNLOADED -> filter { it.isDownloaded && !it.localUri.isNullOrBlank() }
        DownloadedFilter.HIDE_DOWNLOADED -> filter { !it.isDownloaded || it.localUri.isNullOrBlank() }
    }
}

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

