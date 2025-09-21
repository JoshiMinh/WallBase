package com.joshiminh.wallbase.ui.viewmodel

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
import com.joshiminh.wallbase.data.repository.LibraryRepository
import com.joshiminh.wallbase.data.entity.source.SourceKeys
import com.joshiminh.wallbase.util.wallpapers.PreviewData
import com.joshiminh.wallbase.util.wallpapers.WallpaperApplier
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem
import com.joshiminh.wallbase.util.wallpapers.WallpaperTarget
import com.joshiminh.wallbase.util.network.ServiceLocator
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
                isRemovingFromLibrary = false,
                isInLibrary = wallpaper.sourceKey == SourceKeys.LOCAL,
                isDownloading = false,
                isRemovingDownload = false,
                isDownloaded = wallpaper.isDownloaded && !wallpaper.localUri.isNullOrBlank(),
                showRemoveDownloadConfirmation = false,
                pendingPreview = null,
                pendingFallback = null,
                message = null
            )
        }

        val sourceKey = wallpaper.sourceKey
        if (sourceKey != null) {
            viewModelScope.launch {
                val libraryState = runCatching { libraryRepository.getWallpaperLibraryState(wallpaper) }
                    .getOrNull()
                if (libraryState != null) {
                    _uiState.update { current ->
                        if (current.wallpaper?.id == wallpaper.id) {
                            val updatedWallpaper = current.wallpaper.copy(
                                localUri = libraryState.localUri,
                                isDownloaded = libraryState.isDownloaded
                            )
                            current.copy(
                                wallpaper = updatedWallpaper,
                                isInLibrary = libraryState.isInLibrary,
                                isDownloaded = libraryState.isDownloaded
                            )
                        } else {
                            current
                        }
                    }
                }
            }
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
            _uiState.update {
                it.copy(
                    isApplying = true,
                    message = null,
                    pendingFallback = null
                )
            }
            val previewResult = applier.createSystemPreview(wallpaper.imageUrl, target)
            previewResult.fold(
                onSuccess = { preview ->
                    val packageManager = getApplication<Application>().packageManager
                    val canHandle = preview.intent.resolveActivity(packageManager) != null
                    if (!canHandle) {
                        applier.cleanupPreview(preview)
                        showPreviewFallback(
                            target,
                            IllegalStateException("No system activity available to preview wallpapers")
                        )
                    } else {
                        _uiState.update {
                            it.copy(
                                isApplying = false,
                                pendingPreview = WallpaperPreviewLaunch(preview, target),
                                pendingFallback = null
                            )
                        }
                    }
                },
                onFailure = { throwable ->
                    showPreviewFallback(target, throwable)
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
        showPreviewFallback(preview.target, throwable)
    }

    fun confirmApplyWithoutPreview() {
        val wallpaper = _uiState.value.wallpaper ?: return
        val fallback = _uiState.value.pendingFallback ?: return
        if (_uiState.value.isApplying) return

        viewModelScope.launch {
            val target = fallback.target
            val reason = fallback.reason
            _uiState.update {
                it.copy(
                    isApplying = true,
                    pendingFallback = null,
                    message = null
                )
            }
            val result = applier.apply(wallpaper.imageUrl, target)
            _uiState.update {
                it.copy(
                    isApplying = false,
                    message = result.fold(
                        onSuccess = {
                            reason?.let { detail ->
                                "Preview unavailable ($detail). Applied wallpaper to ${target.label}"
                            } ?: "Preview unavailable. Applied wallpaper to ${target.label}"
                        },
                        onFailure = { throwable ->
                            val failure = throwable.localizedMessage ?: "Failed to apply wallpaper"
                            reason?.let { detail ->
                                "Preview unavailable ($detail). $failure"
                            } ?: failure
                        }
                    )
                )
            }
        }
    }

    fun dismissPreviewFallback() {
        val fallback = _uiState.value.pendingFallback ?: return
        if (_uiState.value.isApplying) return

        val message = fallback.reason?.let { detail ->
            "Preview unavailable ($detail). Wallpaper not applied."
        } ?: "Preview unavailable. Wallpaper not applied."

        _uiState.update {
            it.copy(
                pendingFallback = null,
                message = message
            )
        }
    }

    fun addToLibrary() {
        val wallpaper = _uiState.value.wallpaper ?: return
        if (_uiState.value.isAddingToLibrary) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAddingToLibrary = true, message = null) }
            val result = runCatching { libraryRepository.addWallpaper(wallpaper) }
            _uiState.update {
                val isSuccess = result.isSuccess
                it.copy(
                    isAddingToLibrary = false,
                    isInLibrary = if (isSuccess) true else it.isInLibrary,
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

    fun downloadWallpaper() {
        val wallpaper = _uiState.value.wallpaper ?: return
        if (_uiState.value.isDownloading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true, message = null) }
            val result = runCatching { libraryRepository.downloadWallpapers(listOf(wallpaper)) }
            val libraryState = runCatching { libraryRepository.getWallpaperLibraryState(wallpaper) }
                .getOrNull()
            _uiState.update {
                val updatedWallpaper = if (libraryState != null && it.wallpaper?.id == wallpaper.id) {
                    it.wallpaper.copy(
                        localUri = libraryState.localUri,
                        isDownloaded = libraryState.isDownloaded
                    )
                } else {
                    it.wallpaper
                }
                it.copy(
                    isDownloading = false,
                    isDownloaded = libraryState?.isDownloaded ?: it.isDownloaded,
                    wallpaper = updatedWallpaper,
                    message = result.fold(
                        onSuccess = { summary ->
                            when {
                                summary.downloaded > 0 -> "Downloaded wallpaper"
                                summary.skipped > 0 -> "Wallpaper already saved locally"
                                else -> "Unable to download wallpaper"
                            }
                        },
                        onFailure = { throwable ->
                            throwable.localizedMessage ?: "Unable to download wallpaper"
                        }
                    )
                )
            }
        }
    }

    fun promptRemoveDownload() {
        val current = _uiState.value
        if (!current.isDownloaded || current.isRemovingDownload) return
        _uiState.update { it.copy(showRemoveDownloadConfirmation = true, message = null) }
    }

    fun dismissRemoveDownloadPrompt() {
        _uiState.update { it.copy(showRemoveDownloadConfirmation = false) }
    }

    fun removeDownload() {
        val wallpaper = _uiState.value.wallpaper ?: return
        val currentState = _uiState.value
        if (!currentState.isDownloaded || currentState.isRemovingDownload) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRemovingDownload = true, message = null) }
            val result = runCatching { libraryRepository.removeDownloads(listOf(wallpaper)) }
            val libraryState = runCatching { libraryRepository.getWallpaperLibraryState(wallpaper) }
                .getOrNull()
            _uiState.update {
                val updatedWallpaper = if (libraryState != null && it.wallpaper?.id == wallpaper.id) {
                    it.wallpaper.copy(
                        localUri = libraryState.localUri,
                        isDownloaded = libraryState.isDownloaded
                    )
                } else {
                    it.wallpaper
                }
                it.copy(
                    isRemovingDownload = false,
                    showRemoveDownloadConfirmation = false,
                    isDownloaded = libraryState?.isDownloaded ?: false,
                    wallpaper = updatedWallpaper,
                    message = result.fold(
                        onSuccess = { summary ->
                            when {
                                summary.removed > 0 && summary.failed > 0 ->
                                    "Removed downloaded copy (failed ${summary.failed})"
                                summary.removed > 0 -> "Removed downloaded copy"
                                summary.skipped > 0 -> "Wallpaper wasn't downloaded"
                                else -> "No downloads were removed"
                            }
                        },
                        onFailure = { throwable ->
                            throwable.localizedMessage ?: "Unable to remove download"
                        }
                    )
                )
            }
        }
    }

    fun removeFromLibrary() {
        val wallpaper = _uiState.value.wallpaper ?: return
        if (!_uiState.value.isInLibrary || _uiState.value.isRemovingFromLibrary) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRemovingFromLibrary = true, message = null) }
            val result = runCatching { libraryRepository.removeWallpaper(wallpaper) }
            _uiState.update {
                it.copy(
                    isRemovingFromLibrary = false,
                    isInLibrary = if (result.getOrDefault(false)) false else it.isInLibrary,
                    message = result.fold(
                        onSuccess = { removed ->
                            if (removed) "Removed wallpaper from your library"
                            else "Wallpaper not found in your library"
                        },
                        onFailure = { throwable ->
                            throwable.localizedMessage ?: "Unable to remove wallpaper from library"
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

    private fun showPreviewFallback(target: WallpaperTarget, error: Throwable?) {
        if (_uiState.value.wallpaper == null) return
        val detail = error?.localizedMessage?.takeIf { it.isNotBlank() }
        _uiState.update {
            it.copy(
                isApplying = false,
                pendingPreview = null,
                pendingFallback = WallpaperPreviewFallback(target, detail),
                message = null
            )
        }
    }

    data class WallpaperDetailUiState(
        val wallpaper: WallpaperItem? = null,
        val isApplying: Boolean = false,
        val isAddingToLibrary: Boolean = false,
        val isRemovingFromLibrary: Boolean = false,
        val isInLibrary: Boolean = false,
        val isDownloading: Boolean = false,
        val isDownloaded: Boolean = false,
        val isRemovingDownload: Boolean = false,
        val hasWallpaperPermission: Boolean = false,
        val showRemoveDownloadConfirmation: Boolean = false,
        val pendingPreview: WallpaperPreviewLaunch? = null,
        val pendingFallback: WallpaperPreviewFallback? = null,
        val message: String? = null
    )

    data class WallpaperPreviewLaunch(
        val preview: PreviewData,
        val target: WallpaperTarget
    )

    data class WallpaperPreviewFallback(
        val target: WallpaperTarget,
        val reason: String?
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
