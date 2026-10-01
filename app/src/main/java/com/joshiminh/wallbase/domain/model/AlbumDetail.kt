package com.joshiminh.wallbase.domain.model

import com.joshiminh.wallbase.domain.model.WallpaperItem

data class AlbumDetail(
    val id: Long,
    val title: String,
    val wallpaperCount: Int,
    val wallpapers: List<WallpaperItem>
)


