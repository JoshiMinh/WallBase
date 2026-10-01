package com.joshiminh.wallbase.scraper.engine

import com.joshiminh.wallbase.domain.model.WallpaperItem

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

    suspend fun scrapeReddit(
        subreddit: String,
        query: String? = null,
        cursor: String? = null
    ): ScrapePage

    suspend fun scrapeImagesFromUrl(
        url: String,
        limit: Int = 30,
        cursor: String? = null
    ): ScrapePage
}


