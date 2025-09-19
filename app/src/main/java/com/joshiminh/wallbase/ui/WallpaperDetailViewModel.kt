package com.joshiminh.wallbase.ui

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.joshiminh.wallbase.data.library.LibraryRepository
import com.joshiminh.wallbase.data.wallpapers.PreviewData
import com.joshiminh.wallbase.data.wallpapers.WallpaperApplier
import com.joshiminh.wallbase.data.wallpapers.WallpaperItem
import com.joshiminh.wallbase.data.wallpapers.WallpaperTarget
import com.joshiminh.wallbase.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WallpaperDetailViewModel(
    application: Application,
    private val applier: WallpaperApplier = WallpaperApplier(application.applicationContext),
    private val libraryRepository: LibraryRepository = ServiceLocator.libraryRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        WallpaperDetailUiState(
            hasWallpaperPermission = hasSetWallpaperPermission(application)
        )
    )
    val uiState: StateFlow<WallpaperDetailUiState> = _uiState.asStateFlow()

    fun setWallpaper(wallpaper: WallpaperItem) {
        _uiState.update { current ->
            if (current.wallpaper?.id == wallpaper.id) current
            else current.copy(
                wallpaper = wallpaper,
                isApplying = false,
                isAddingToLibrary = false,
                message = null
            )
        }
    }

    fun applyWallpaper(target: WallpaperTarget) {
        val wallpaper = _uiState.value.wallpaper ?: return
        if (_uiState.value.isApplying) return

        if (!_uiState.value.hasWallpaperPermission) {
            _uiState.update {
                it.copy(
                    message = "Wallpaper access is required. Grant permission to continue."
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isApplying = true, message = null) }
            val previewResult = applier.createSystemPreview(wallpaper.imageUrl, target)
            previewResult.fold(
                onSuccess = { preview ->
                    val packageManager = getApplication<Application>().packageManager
                    val canHandle = preview.intent.resolveActivity(packageManager) != null
                    if (!canHandle) {
                        applier.cleanupPreview(preview)
                        applyDirectWithFallback(
                            wallpaper,
                            target,
                            IllegalStateException("No system activity available to preview wallpapers")
                        )
                    } else {
                        _uiState.update {
                            it.copy(
                                isApplying = false,
                                pendingPreview = WallpaperPreviewLaunch(preview, target)
                            )
                        }
                    }
                },
                onFailure = { throwable ->
                    applyDirectWithFallback(wallpaper, target, throwable)
                }
            )
        }
    }

    fun onPreviewLaunched() {
        _uiState.update { it.copy(pendingPreview = null) }
    }

    fun onPreviewResult(preview: WallpaperPreviewLaunch, resultCode: Int) {
        applier.cleanupPreview(preview.preview)
        val message = if (resultCode == Activity.RESULT_OK) {
            "Applied wallpaper to ${preview.target.label}"
        } else {
            "Wallpaper preview canceled"
        }
        _uiState.update { it.copy(message = message) }
    }

    fun onPreviewLaunchFailed(preview: WallpaperPreviewLaunch, throwable: Throwable) {
        applier.cleanupPreview(preview.preview)
        val wallpaper = _uiState.value.wallpaper ?: return
        viewModelScope.launch {
            applyDirectWithFallback(wallpaper, preview.target, throwable)
        }
    }

    fun addToLibrary() {
        val wallpaper = _uiState.value.wallpaper ?: return
        if (_uiState.value.isAddingToLibrary) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAddingToLibrary = true, message = null) }
            val result = runCatching { libraryRepository.addWallpaper(wallpaper) }
            _uiState.update {
                it.copy(
                    isAddingToLibrary = false,
                    message = result.fold(
                        onSuccess = { added ->
                            if (added) "Added wallpaper to your library"
                            else "Wallpaper is already in your library"
                        },
                        onFailure = { throwable ->
                            throwable.localizedMessage ?: "Unable to add wallpaper to library"
                        }
                    )
                )
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun onWallpaperPermissionResult(granted: Boolean) {
        _uiState.update {
            it.copy(
                hasWallpaperPermission = granted,
                message = if (granted) null else "Wallpaper access denied. The app can't apply wallpapers without it."
            )
        }
    }

    private suspend fun applyDirectWithFallback(
        wallpaper: WallpaperItem,
        target: WallpaperTarget,
        previewError: Throwable?
    ) {
        val result = applier.apply(wallpaper.imageUrl, target)
        _uiState.update {
            it.copy(
                isApplying = false,
                message = result.fold(
                    onSuccess = {
                        previewError?.let { error ->
                            val detail = error.localizedMessage?.takeIf { msg -> msg.isNotBlank() }
                            if (detail != null) {
                                "Preview unavailable ($detail). Applied wallpaper to ${target.label}"
                            } else {
                                "Preview unavailable. Applied wallpaper to ${target.label}"
                            }
                        } ?: "Applied wallpaper to ${target.label}"
                    },
                    onFailure = { throwable ->
                        throwable.localizedMessage ?: "Failed to apply wallpaper"
                    }
                )
            )
        }
    }

    data class WallpaperDetailUiState(
        val wallpaper: WallpaperItem? = null,
        val isApplying: Boolean = false,
        val isAddingToLibrary: Boolean = false,
        val hasWallpaperPermission: Boolean = false,
        val pendingPreview: WallpaperPreviewLaunch? = null,
        val message: String? = null
    )

    data class WallpaperPreviewLaunch(
        val preview: PreviewData,
        val target: WallpaperTarget
    )

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                ServiceLocator.initialize(application)
                WallpaperDetailViewModel(
                    application = application,
                    applier = WallpaperApplier(application.applicationContext),
                    libraryRepository = ServiceLocator.libraryRepository
                )
            }
        }

        private fun hasSetWallpaperPermission(application: Application): Boolean {
            val granted = ContextCompat.checkSelfPermission(
                application.applicationContext,
                Manifest.permission.SET_WALLPAPER
            ) == PackageManager.PERMISSION_GRANTED
            return granted
        }
    }
}
