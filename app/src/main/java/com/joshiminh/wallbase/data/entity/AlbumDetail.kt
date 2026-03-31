package com.joshiminh.wallbase.data.entity

import com.joshiminh.wallbase.data.entity.WallpaperItem

data class AlbumDetail(
    val id: Long,
    val title: String,
    val wallpaperCount: Int,
    val wallpapers: List<WallpaperItem>
)


