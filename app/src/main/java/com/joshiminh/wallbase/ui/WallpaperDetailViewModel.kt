package com.joshiminh.wallbase.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.joshiminh.wallbase.data.wallpapers.WallpaperApplier
import com.joshiminh.wallbase.data.wallpapers.WallpaperItem
import com.joshiminh.wallbase.data.wallpapers.WallpaperTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WallpaperDetailViewModel(
    application: Application,
    private val applier: WallpaperApplier = WallpaperApplier(application.applicationContext)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(WallpaperDetailUiState())
    val uiState: StateFlow<WallpaperDetailUiState> = _uiState.asStateFlow()

    fun setWallpaper(wallpaper: WallpaperItem) {
        _uiState.update { current ->
            if (current.wallpaper?.id == wallpaper.id) current
            else current.copy(wallpaper = wallpaper)
        }
    }

    fun updatePreviewTarget(target: WallpaperTarget) {
        _uiState.update { it.copy(previewTarget = target) }
    }

    fun applyWallpaper(target: WallpaperTarget) {
        val wallpaper = _uiState.value.wallpaper ?: return
        if (_uiState.value.isApplying) return

        viewModelScope.launch {
            _uiState.update { it.copy(isApplying = true, message = null) }
            val result = applier.apply(wallpaper.imageUrl, target)
            _uiState.update {
                it.copy(
                    isApplying = false,
                    message = result.fold(
                        onSuccess = { "Applied wallpaper to ${target.label}" },
                        onFailure = { throwable ->
                            throwable.localizedMessage ?: "Failed to apply wallpaper"
                        }
                    )
                )
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    data class WallpaperDetailUiState(
        val wallpaper: WallpaperItem? = null,
        val previewTarget: WallpaperTarget = WallpaperTarget.HOME,
        val isApplying: Boolean = false,
        val message: String? = null
    )

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = this[androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                WallpaperDetailViewModel(application)
            }
        }
    }
}
