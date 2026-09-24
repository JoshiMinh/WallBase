package com.joshiminh.wallbase.data.entity

data class CategoryItem(
    val id: Long,
    val title: String,
    val wallpaperCount: Int = 0,
    val isPreset: Boolean = false,
    val sortOrder: Int = 0
)
