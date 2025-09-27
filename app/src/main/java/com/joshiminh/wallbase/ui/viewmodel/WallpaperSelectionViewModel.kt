package com.joshiminh.wallbase.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WallpaperSelectionViewModel : ViewModel() {
    private val _selectedWallpaper = MutableStateFlow<WallpaperItem?>(null)
    val selectedWallpaper: StateFlow<WallpaperItem?> = _selectedWallpaper.asStateFlow()

    fun select(wallpaper: WallpaperItem) {
        _selectedWallpaper.value = wallpaper
    }

    fun clear() {
        _selectedWallpaper.value = null
    }
}
