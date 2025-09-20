package com.joshiminh.wallbase.data.entity.album

data class AlbumItem(
    val id: Long,
    val title: String,
    val wallpaperCount: Int,
    val coverImageUrl: String?
)