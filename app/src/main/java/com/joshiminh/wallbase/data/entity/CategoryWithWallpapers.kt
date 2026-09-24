package com.joshiminh.wallbase.data.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class CategoryWithWallpapers(
    @Embedded val category: CategoryEntity,
    @Relation(
        parentColumn = "category_id",
        entityColumn = "wallpaper_id",
        associateBy = Junction(CategoryWallpaperCrossRef::class)
    )
    val wallpapers: List<WallpaperEntity>
) {
    fun toCategoryItem(): CategoryItem = CategoryItem(
        id = category.id,
        title = category.title,
        wallpaperCount = wallpapers.size,
        isPreset = category.isPreset,
        sortOrder = category.sortOrder
    )
}
