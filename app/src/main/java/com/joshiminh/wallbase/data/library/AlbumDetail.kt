package com.joshiminh.wallbase.data.library

import com.joshiminh.wallbase.data.wallpapers.WallpaperItem

data class AlbumDetail(
    val id: Long,
    val title: String,
    val wallpaperCount: Int,
    val wallpapers: List<WallpaperItem>
)
