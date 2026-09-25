package com.joshiminh.wallbase.data.entity

import androidx.compose.runtime.Immutable

@Immutable
data class CategoryItem(
    val id: Long,
    val title: String,
    val wallpaperCount: Int = 0,
    val isPreset: Boolean = false,
    val sortOrder: Int = 0
)
