package com.joshiminh.wallbase.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.joshiminh.wallbase.data.entity.CategoryEntity
import com.joshiminh.wallbase.data.entity.CategoryWallpaperCrossRef
import com.joshiminh.wallbase.data.entity.CategoryWithWallpapers
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<CategoryEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(refs: List<CategoryWallpaperCrossRef>): List<Long>

    @Query("DELETE FROM category_wallpaper_cross_ref WHERE category_id = :categoryId AND wallpaper_id = :wallpaperId")
    suspend fun deleteCrossRef(categoryId: Long, wallpaperId: Long): Int

    @Query("DELETE FROM category_wallpaper_cross_ref WHERE wallpaper_id = :wallpaperId")
    suspend fun deleteCrossRefsForWallpaper(wallpaperId: Long): Int

    @Query("DELETE FROM category_wallpaper_cross_ref WHERE wallpaper_id IN (:wallpaperIds)")
    suspend fun deleteCrossRefsForWallpapers(wallpaperIds: Collection<Long>): Int

    @Query("DELETE FROM category_wallpaper_cross_ref WHERE category_id = :categoryId")
    suspend fun deleteCrossRefsForCategory(categoryId: Long): Int

    @Query("DELETE FROM categories WHERE category_id = :categoryId")
    suspend fun deleteCategory(categoryId: Long): Int

    @Query("SELECT * FROM categories WHERE title = :title LIMIT 1")
    suspend fun findCategoryByTitle(title: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE category_id = :categoryId LIMIT 1")
    suspend fun getCategory(categoryId: Long): CategoryEntity?

    @Query("UPDATE categories SET title = :title, updated_at = :updatedAt WHERE category_id = :categoryId")
    suspend fun updateCategoryTitle(categoryId: Long, title: String, updatedAt: Long): Int

    @Query("UPDATE categories SET sort_order = :sortOrder WHERE category_id = :categoryId")
    suspend fun updateCategorySortOrder(categoryId: Long, sortOrder: Int): Int

    @Query("SELECT * FROM categories ORDER BY sort_order ASC, title ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Transaction
    @Query("SELECT * FROM categories ORDER BY sort_order ASC, title ASC")
    fun observeCategoriesWithWallpapers(): Flow<List<CategoryWithWallpapers>>

    @Transaction
    @Query("SELECT * FROM categories WHERE category_id = :categoryId LIMIT 1")
    fun observeCategoryWithWallpapers(categoryId: Long): Flow<CategoryWithWallpapers?>

    @Query("SELECT category_id FROM category_wallpaper_cross_ref WHERE wallpaper_id = :wallpaperId")
    fun observeCategoryIdsForWallpaper(wallpaperId: Long): Flow<List<Long>>

    @Query("SELECT category_id FROM category_wallpaper_cross_ref WHERE wallpaper_id = :wallpaperId")
    suspend fun getCategoryIdsForWallpaper(wallpaperId: Long): List<Long>
}
