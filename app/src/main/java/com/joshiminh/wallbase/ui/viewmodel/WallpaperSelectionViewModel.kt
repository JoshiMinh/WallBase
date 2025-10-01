package com.joshiminh.wallbase.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WallpaperSelectionState(
    val wallpaper: WallpaperItem,
    val enableSharedTransition: Boolean,
)

class WallpaperSelectionViewModel : ViewModel() {
    private val _selectedWallpaper = MutableStateFlow<WallpaperSelectionState?>(null)
    val selectedWallpaper: StateFlow<WallpaperSelectionState?> = _selectedWallpaper.asStateFlow()

    fun select(wallpaper: WallpaperItem, enableSharedTransition: Boolean) {
        _selectedWallpaper.value = WallpaperSelectionState(
            wallpaper = wallpaper,
            enableSharedTransition = enableSharedTransition,
        )
    }

    fun clear() {
        _selectedWallpaper.value = null
    }
}
