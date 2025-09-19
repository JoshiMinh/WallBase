package com.joshiminh.wallbase.data.wallpapers

/**
 * Simple representation of a wallpaper entry shown in the Explore screen.
 */
data class WallpaperItem(
    val id: String,
    val title: String,
    val imageUrl: String,
    val sourceUrl: String
)
