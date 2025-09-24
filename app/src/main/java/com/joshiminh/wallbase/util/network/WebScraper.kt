package com.joshiminh.wallbase.util.network

import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem

data class ScrapePage(
    val wallpapers: List<WallpaperItem>,
    val nextCursor: String?
)

interface WebScraper {
    suspend fun scrapePinterest(
        query: String,
        limit: Int = 30,
        cursor: String? = null
    ): ScrapePage

    suspend fun scrapeImagesFromUrl(
        url: String,
        limit: Int = 30,
        cursor: String? = null
    ): ScrapePage
}
