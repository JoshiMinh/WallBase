package com.joshiminh.wallbase.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.joshiminh.wallbase.data.entity.WallpaperItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WallpaperSelectionState(
    val wallpaper: WallpaperItem,
    val wallpapers: List<WallpaperItem> = listOf(wallpaper),
    val initialIndex: Int = 0,
    val enableSharedTransition: Boolean = true,
)

class WallpaperSelectionViewModel : ViewModel() {
    private val _selectedWallpaper = MutableStateFlow<WallpaperSelectionState?>(null)
    val selectedWallpaper: StateFlow<WallpaperSelectionState?> = _selectedWallpaper.asStateFlow()

    fun select(
        wallpaper: WallpaperItem,
        wallpapers: List<WallpaperItem> = listOf(wallpaper),
        initialIndex: Int = 0,
        enableSharedTransition: Boolean = true,
    ) {
        val list = if (wallpapers.isNotEmpty()) wallpapers else listOf(wallpaper)
        val index = if (initialIndex in list.indices) initialIndex else list.indexOfFirst { it.id == wallpaper.id }.coerceAtLeast(0)
        _selectedWallpaper.value = WallpaperSelectionState(
            wallpaper = wallpaper,
            wallpapers = list,
            initialIndex = index,
            enableSharedTransition = enableSharedTransition,
        )
    }

    fun clear() {
        _selectedWallpaper.value = null
    }
}


