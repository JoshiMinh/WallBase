package com.joshiminh.wallbase.data.entity.album

import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem

data class AlbumDetail(
    val id: Long,
    val title: String,
    val wallpaperCount: Int,
    val wallpapers: List<WallpaperItem>
)