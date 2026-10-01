package com.joshiminh.wallbase.core.common

import com.joshiminh.wallbase.domain.model.WallpaperItem
import java.util.Locale

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
        if (width == null || height == null || width <= 0 || height <= 0) return false
        val shorterSide = minOf(width, height)
        return shorterSide >= minDimension
    }

    companion object {
        fun fromStorage(value: String?): MinResolution = when (value?.lowercase(Locale.ROOT)) {
            "720p", "hd" -> HD_720P
            "1080p", "fhd" -> FHD_1080P
            "1440p", "2k", "qhd" -> QHD_1440P
            "4k", "uhd" -> UHD_4K
            else -> ANY
        }
    }
}

private val REGEX_TITLE_DIMENSION = Regex("""(?i)(?:\[|\(|\s|^)(\d{3,5})\s*[xX×]\s*(\d{3,5})(?:\]|\)|\s|$)""")
private val REGEX_URL_WH = Regex("""(?i)[/_&?](?:w|width)=(\d{3,5})[^\d].*?[/_&?](?:h|height)=(\d{3,5})""")
private val REGEX_URL_CROSS = Regex("""(?i)[/_&?](\d{3,5})\s*[xX]\s*(\d{3,5})""")
private val REGEX_ALPHA_THUMB = Regex("""thumb-([0-9]{3,5})-""")
private val REGEX_8K = Regex("""\b(8k|4320p)\b""", RegexOption.IGNORE_CASE)
private val REGEX_4K = Regex("""\b(4k|uhd|2160p|ultra\s*hd)\b""", RegexOption.IGNORE_CASE)
private val REGEX_2K = Regex("""\b(2k|1440p|qhd|wqhd)\b""", RegexOption.IGNORE_CASE)
private val REGEX_1080P = Regex("""\b(1080p|fhd|full\s*hd)\b""", RegexOption.IGNORE_CASE)
private val REGEX_720P = Regex("""\b(720p|hd)\b""", RegexOption.IGNORE_CASE)

/**
 * Inferred dimensions from title, URLs, or metadata if explicit width/height are null.
 */
fun WallpaperItem.inferDimensions(): Pair<Int, Int>? {
    // 1. Check explicit fields
    if (width != null && height != null && width > 0 && height > 0) {
        return Pair(width, height)
    }
    if (width != null && width > 0 && (height == null || height <= 0)) {
        return Pair(width, (width * 9) / 16)
    }
    if (height != null && height > 0 && (width == null || width <= 0)) {
        return Pair((height * 9) / 16, height)
    }

    // 2. Check title for explicit dimension patterns like "[3840x2160]" or "1920 x 1080"
    val titleMatch = REGEX_TITLE_DIMENSION.find(title)
    if (titleMatch != null) {
        val w = titleMatch.groupValues[1].toIntOrNull()
        val h = titleMatch.groupValues[2].toIntOrNull()
        if (w != null && h != null && w > 0 && h > 0) {
            return Pair(w, h)
        }
    }

    // 3. Check imageUrl / sourceUrl for dimension patterns (e.g., "w=3840&h=2160", "thumb-1920-", "/1920x1080/")
    val url = imageUrl
    val urlDimensionMatch = REGEX_URL_WH.find(url) ?: REGEX_URL_CROSS.find(url)
    if (urlDimensionMatch != null) {
        val w = urlDimensionMatch.groupValues[1].toIntOrNull()
        val h = urlDimensionMatch.groupValues[2].toIntOrNull()
        if (w != null && h != null && w > 0 && h > 0) {
            return Pair(w, h)
        }
    }

    // 4. Check AlphaCoders thumb pattern (e.g. thumb-1920-xxx.jpg)
    val alphaThumbMatch = REGEX_ALPHA_THUMB.find(url)
    if (alphaThumbMatch != null) {
        val w = alphaThumbMatch.groupValues[1].toIntOrNull()
        if (w != null && w > 0) {
            return Pair(w, (w * 9) / 16)
        }
    }

    // 5. Check keywords in title with strict word boundaries
    when {
        REGEX_8K.containsMatchIn(title) -> return Pair(7680, 4320)
        REGEX_4K.containsMatchIn(title) -> return Pair(3840, 2160)
        REGEX_2K.containsMatchIn(title) -> return Pair(2560, 1440)
        REGEX_1080P.containsMatchIn(title) -> return Pair(1920, 1080)
        REGEX_720P.containsMatchIn(title) -> return Pair(1280, 720)
    }

    // 6. Check Pinterest URLs
    if (url.contains("i.pinimg.com/originals/")) {
        return Pair(1080, 1920) // Original Pinterest upload baseline (satisfies FHD, doesn't falsely claim 2K/4K)
    }
    if (url.contains("i.pinimg.com/736x/")) return Pair(736, 1308)
    if (url.contains("i.pinimg.com/564x/")) return Pair(564, 1000)
    if (url.contains("i.pinimg.com/236x/") || url.contains("i.pinimg.com/237x/")) return Pair(236, 420)

    // 7. Check Unsplash / Pexels transformed URLs
    if (url.contains("unsplash.com") && (url.contains("auto=format") || url.contains("w=") || url.contains("q="))) {
        val wMatch = Regex("""[?&]w=(\d+)""").find(url)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val hMatch = Regex("""[?&]h=(\d+)""").find(url)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (wMatch != null && hMatch != null) return Pair(wMatch, hMatch)
        if (wMatch != null) return Pair(wMatch, (wMatch * 1.5f).toInt())
    }
    if (url.contains("pexels.com") && url.contains("h=2560")) {
        return Pair(1440, 2560)
    }

    return null
}

/**
 * Checks if a wallpaper satisfies the minimum resolution requirement.
 */
fun WallpaperItem.matchesMinResolution(minResolution: MinResolution): Boolean {
    if (minResolution == MinResolution.ANY) return true

    val dimensions = inferDimensions()
    if (dimensions != null) {
        val shorterSide = minOf(dimensions.first, dimensions.second)
        return shorterSide >= minResolution.minDimension
    }

    val lowerUrl = imageUrl.lowercase(Locale.ROOT)
    val isThumbnail = lowerUrl.contains("/236x/") ||
        lowerUrl.contains("/237x/") ||
        lowerUrl.contains("/564x/") ||
        lowerUrl.contains("thumb") ||
        lowerUrl.contains("preview") ||
        lowerUrl.contains("_s.jpg") ||
        lowerUrl.contains("_t.jpg") ||
        lowerUrl.contains("w=500")

    if (isThumbnail) {
        return false // Low-res thumbnail filtered out
    }

    return when (minResolution) {
        MinResolution.ANY -> true
        MinResolution.HD_720P -> true
        MinResolution.FHD_1080P -> {
            lowerUrl.contains("w.wallhaven.cc") ||
                lowerUrl.contains("images.unsplash.com") ||
                lowerUrl.contains("i.redd.it") ||
                lowerUrl.contains("/originals/") ||
                lowerUrl.contains("images.alphacoders.com")
        }
        MinResolution.QHD_1440P, MinResolution.UHD_4K -> false
    }
}

/**
 * Filters wallpapers based on minimum resolution.
 */
fun List<WallpaperItem>.filterByMinResolution(minResolution: MinResolution): List<WallpaperItem> {
    if (minResolution == MinResolution.ANY) return this
    return filter { it.matchesMinResolution(minResolution) }
}



