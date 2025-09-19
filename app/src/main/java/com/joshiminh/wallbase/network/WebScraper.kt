package com.joshiminh.wallbase.network

import com.joshiminh.wallbase.data.wallpapers.WallpaperItem

interface WebScraper {
    suspend fun scrapePinterest(query: String, limit: Int = 30): List<WallpaperItem>

    suspend fun scrapeImagesFromUrl(url: String, limit: Int = 30): List<WallpaperItem>
}
