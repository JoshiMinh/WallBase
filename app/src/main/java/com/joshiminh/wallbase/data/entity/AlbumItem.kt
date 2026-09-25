package com.joshiminh.wallbase.data.entity

import androidx.compose.runtime.Immutable

@Immutable
data class AlbumItem(
    val id: Long,
    val title: String,
    val wallpaperCount: Int,
    val coverImageUrl: String?,
    val createdAt: Long
)

