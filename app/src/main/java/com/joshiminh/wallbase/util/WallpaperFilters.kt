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
 * Checks if a wallpaper matches horizontal wallpaper preferences.
 * Horizontal wallpapers are those with an aspect ratio > 1.2 (wider than tall).
 */
fun WallpaperItem.matchesHorizontalPreference(showHorizontal: Boolean): Boolean {
    if (showHorizontal) return true
    val ratio = aspectRatio ?: return true
    return ratio <= 1.2f
}

/**
 * Filters wallpapers based on aspect ratio and orientation preferences.
 * Horizontal wallpapers are those with an aspect ratio > 1.2 (wider than tall).
 */
fun List<WallpaperItem>.filterByHorizontalPreference(showHorizontal: Boolean): List<WallpaperItem> {
    if (showHorizontal) return this
    return filter { it.matchesHorizontalPreference(showHorizontal) }
}

/**
 * Minimum resolution filter options for wallpaper discovery.
 */
enum class MinResolution(
    val storageValue: String,
    val label: String,
    val minDimension: Int
) {
    ANY("any", "Any resolution", 0),
    HD_720P("720p", "HD (720p+)", 720),
    FHD_1080P("1080p", "Full HD (1080p+)", 1080),
    QHD_1440P("1440p", "2K QHD (1440p+)", 1440),
    UHD_4K("4k", "4K UHD (2160p+)", 2160);

    fun matches(width: Int?, height: Int?): Boolean {
        if (this == ANY) return true
        if (width == null || height == null || width <= 0 || height <= 0) return true
        val shorterSide = minOf(width, height)
        return shorterSide >= minDimension
    }

    companion object {
        fun fromStorage(value: String?): MinResolution = when (value?.lowercase()) {
            "720p", "hd" -> HD_720P
            "1080p", "fhd" -> FHD_1080P
            "1440p", "2k", "qhd" -> QHD_1440P
            "4k", "uhd" -> UHD_4K
            else -> ANY
        }
    }
}

/**
 * Checks if a wallpaper satisfies the minimum resolution requirement.
 */
fun WallpaperItem.matchesMinResolution(minResolution: MinResolution): Boolean {
    return minResolution.matches(width, height)
}

/**
 * Filters wallpapers based on minimum resolution.
 */
fun List<WallpaperItem>.filterByMinResolution(minResolution: MinResolution): List<WallpaperItem> {
    if (minResolution == MinResolution.ANY) return this
    return filter { it.matchesMinResolution(minResolution) }
}


