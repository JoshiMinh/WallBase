package com.joshiminh.wallbase.data.entity

data class AlbumItem(
    val id: Long,
    val title: String,
    val wallpaperCount: Int,
    val coverImageUrl: String?,
    val createdAt: Long
)

