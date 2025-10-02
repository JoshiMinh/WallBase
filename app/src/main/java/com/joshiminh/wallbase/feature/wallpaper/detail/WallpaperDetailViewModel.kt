package com.joshiminh.wallbase.feature.wallpaper.detail

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.joshiminh.wallbase.data.entity.album.AlbumItem
import com.joshiminh.wallbase.data.entity.source.SourceKeys
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem
import com.joshiminh.wallbase.data.repository.LibraryRepository
import com.joshiminh.wallbase.data.repository.SettingsRepository
import com.joshiminh.wallbase.util.network.ServiceLocator
import com.joshiminh.wallbase.util.wallpapers.EditedWallpaper
import com.joshiminh.wallbase.util.wallpapers.PreviewData
import com.joshiminh.wallbase.util.wallpapers.WallpaperApplier
import com.joshiminh.wallbase.util.wallpapers.WallpaperEditor
import com.joshiminh.wallbase.util.wallpapers.WallpaperTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WallpaperDetailViewModel(
    application: Application,
    private val applier: WallpaperApplier = WallpaperApplier(application.applicationContext),
    private val libraryRepository: LibraryRepository = ServiceLocator.libraryRepository,
    private val bitmapLoader: WallpaperEditor = WallpaperEditor(application.applicationContext),
    private val settingsRepository: SettingsRepository = ServiceLocator.settingsRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        WallpaperDetailUiState(
            hasWallpaperPermission = hasSetWallpaperPermission(application)
        )
    )
    val uiState: StateFlow<WallpaperDetailUiState> = _uiState.asStateFlow()

    private var autoDownloadEnabled: Boolean = false
    private var storageLimitBytes: Long = 0L
    private var loadedBitmap: Bitmap? = null

    init {
        viewModelScope.launch {
            settingsRepository.preferences.collectLatest { prefs ->
                autoDownloadEnabled = prefs.autoDownload
                storageLimitBytes = prefs.storageLimitBytes
            }
        }
        viewModelScope.launch {
            libraryRepository.observeAlbums().collectLatest { albums ->
                _uiState.update { it.copy(albums = albums) }
            }
        }
    }

    fun setWallpaper(wallpaper: WallpaperItem) {
        if (_uiState.value.wallpaper?.id != wallpaper.id) {
            clearLoadedBitmap()
        }
        _uiState.update {
            it.copy(
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
                message = null,
                isAddingToAlbum = false
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
                                isDownloaded = libraryState.isDownloaded,
                                cropSettings = libraryState.cropSettings ?: current.wallpaper.cropSettings
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

            val bitmapResult = ensureBitmapLoaded(wallpaper)
            if (bitmapResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isApplying = false,
                        message = bitmapResult.exceptionOrNull()?.localizedMessage
                            ?: "Unable to prepare wallpaper preview"
                    )
                }
                return@launch
            }

            val bitmap = bitmapResult.getOrThrow()
            val previewResult = applier.createSystemPreview(EditedWallpaper(bitmap), target)
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

    fun confirmApplyWithoutPreview() {
        val wallpaper = _uiState.value.wallpaper ?: return
        val fallback = _uiState.value.pendingFallback ?: return
        if (_uiState.value.isApplying) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isApplying = true,
                    pendingFallback = null,
                    message = null
                )
            }

            val bitmapResult = ensureBitmapLoaded(wallpaper)
            if (bitmapResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isApplying = false,
                        message = fallback.reason?.let { detail ->
                            val base = bitmapResult.exceptionOrNull()?.localizedMessage
                                ?: "Unable to prepare wallpaper"
                            "Preview unavailable ($detail). $base"
                        } ?: bitmapResult.exceptionOrNull()?.localizedMessage
                        ?: "Unable to prepare wallpaper"
                    )
                }
                return@launch
            }

            val bitmap = bitmapResult.getOrThrow()
            val result = applier.apply(EditedWallpaper(bitmap), fallback.target)
            _uiState.update {
                it.copy(
                    isApplying = false,
                    message = result.fold(
                        onSuccess = {
                            fallback.reason?.let { detail ->
                                "Preview unavailable ($detail). Applied wallpaper to ${fallback.target.label}"
                            } ?: "Preview unavailable. Applied wallpaper to ${fallback.target.label}"
                        },
                        onFailure = { throwable ->
                            val failure = throwable.localizedMessage ?: "Failed to apply wallpaper"
                            fallback.reason?.let { detail ->
                                "Preview unavailable ($detail). $failure"
                            } ?: failure
                        }
                    )
                )
            }
        }
    }

    fun addToLibrary() {
        val wallpaper = _uiState.value.wallpaper ?: return
        if (_uiState.value.isAddingToLibrary) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAddingToLibrary = true, message = null) }
            val result = runCatching { libraryRepository.addWallpaper(wallpaper) }
            val added = result.getOrNull() == true
            _uiState.update {
                val isSuccess = result.isSuccess
                it.copy(
                    isAddingToLibrary = false,
                    isInLibrary = if (isSuccess) true else it.isInLibrary,
                    message = result.fold(
                        onSuccess = { inserted ->
                            if (inserted) "Added wallpaper to your library"
                            else "Wallpaper is already in your library"
                        },
                        onFailure = { throwable ->
                            throwable.localizedMessage ?: "Unable to add wallpaper to library"
                        }
                    )
                )
            }
            if (added && autoDownloadEnabled && wallpaper.sourceKey != null && wallpaper.sourceKey != SourceKeys.LOCAL) {
                downloadWallpaper(autoInitiated = true)
            }
        }
    }

    fun addToAlbum(albumId: Long) {
        val wallpaper = _uiState.value.wallpaper ?: return
        if (_uiState.value.isAddingToAlbum) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAddingToAlbum = true,
                    message = null
                )
            }

            val ensureResult = runCatching { libraryRepository.addWallpaper(wallpaper) }
            val addedToLibrary = ensureResult.getOrNull() == true
            if (ensureResult.isFailure) {
                val failure = ensureResult.exceptionOrNull()
                _uiState.update {
                    it.copy(
                        isAddingToAlbum = false,
                        message = failure?.localizedMessage ?: "Unable to add wallpaper to album"
                    )
                }
                return@launch
            }

            if (addedToLibrary && autoDownloadEnabled && wallpaper.sourceKey != null && wallpaper.sourceKey != SourceKeys.LOCAL) {
                downloadWallpaper(autoInitiated = true)
            }

            val association = runCatching {
                libraryRepository.addWallpapersToAlbum(albumId, listOf(wallpaper))
            }

            _uiState.update { current ->
                current.copy(
                    isAddingToAlbum = false,
                    isInLibrary = true,
                    message = association.fold(
                        onSuccess = { outcome ->
                            when {
                                outcome.addedToAlbum > 0 && (outcome.alreadyPresent > 0 || outcome.skipped > 0) -> {
                                    val skipped = outcome.alreadyPresent + outcome.skipped
                                    "Added ${outcome.addedToAlbum} wallpaper${if (outcome.addedToAlbum == 1) "" else "s"} (skipped $skipped others)"
                                }

                                outcome.addedToAlbum > 0 ->
                                    "Added ${outcome.addedToAlbum} wallpaper${if (outcome.addedToAlbum == 1) "" else "s"} to the album"

                                outcome.alreadyPresent > 0 || outcome.skipped > 0 ->
                                    "Wallpaper already in the album"

                                else -> "Wallpaper added to album"
                            }
                        },
                        onFailure = { throwable ->
                            throwable.localizedMessage ?: "Unable to add wallpaper to album"
                        }
                    )
                )
            }
        }
    }

    fun downloadWallpaper(autoInitiated: Boolean = false) {
        val wallpaper = _uiState.value.wallpaper ?: return
        if (_uiState.value.isDownloading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true, message = null) }
            val result = runCatching {
                libraryRepository.downloadWallpapers(listOf(wallpaper), storageLimitBytes)
            }
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
                            val base = when {
                                summary.downloaded > 0 && summary.blocked > 0 ->
                                    "Downloaded wallpaper (blocked ${summary.blocked} more by storage limit)"
                                summary.downloaded > 0 -> "Downloaded wallpaper"
                                summary.blocked > 0 -> "Storage limit reached. Download blocked."
                                summary.skipped > 0 -> "Wallpaper already saved locally"
                                summary.failed > 0 -> "Unable to download wallpaper"
                                else -> "No wallpapers were downloaded"
                            }
                            if (autoInitiated && summary.downloaded > 0) {
                                if (summary.blocked > 0) {
                                    "Auto-downloaded wallpaper (blocked ${summary.blocked} more by storage limit)"
                                } else {
                                    "Auto-downloaded wallpaper"
                                }
                            } else if (autoInitiated && summary.blocked > 0 && summary.downloaded == 0) {
                                "Auto-download blocked by storage limit"
                            } else {
                                base
                            }
                        },
                        onFailure = { throwable ->
                            val fallback = throwable.localizedMessage ?: "Unable to download wallpaper"
                            if (autoInitiated) {
                                "Auto-download failed: $fallback"
                            } else {
                                fallback
                            }
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

    private suspend fun ensureBitmapLoaded(wallpaper: WallpaperItem): Result<Bitmap> {
        val existing = loadedBitmap?.takeIf { !it.isRecycled }
        if (existing != null) return Result.success(existing)

        val model = wallpaper.localUri?.takeIf { wallpaper.isDownloaded && it.isNotBlank() }?.toUri()
            ?: wallpaper.imageUrl

        return runCatching {
            withContext(Dispatchers.IO) { bitmapLoader.loadOriginalBitmap(model) }
        }.onSuccess { bitmap ->
            loadedBitmap = bitmap
        }
    }

    private fun clearLoadedBitmap() {
        loadedBitmap?.takeIf { !it.isRecycled }?.recycle()
        loadedBitmap = null
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
        val message: String? = null,
        val albums: List<AlbumItem> = emptyList(),
        val isAddingToAlbum: Boolean = false
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
                ServiceLocator.ensureInitialized(application)
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

    override fun onCleared() {
        super.onCleared()
        clearLoadedBitmap()
    }
}
