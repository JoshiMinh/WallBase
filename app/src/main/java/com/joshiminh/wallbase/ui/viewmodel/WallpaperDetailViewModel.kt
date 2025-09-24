package com.joshiminh.wallbase.ui.viewmodel

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.joshiminh.wallbase.data.repository.LibraryRepository
import com.joshiminh.wallbase.data.entity.source.SourceKeys
import com.joshiminh.wallbase.util.wallpapers.EditedWallpaper
import com.joshiminh.wallbase.util.wallpapers.PreviewData
import com.joshiminh.wallbase.util.wallpapers.WallpaperApplier
import com.joshiminh.wallbase.util.wallpapers.WallpaperAdjustments
import com.joshiminh.wallbase.util.wallpapers.WallpaperCrop
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem
import com.joshiminh.wallbase.util.wallpapers.WallpaperEditor
import com.joshiminh.wallbase.util.wallpapers.WallpaperFilter
import com.joshiminh.wallbase.util.wallpapers.WallpaperTarget
import com.joshiminh.wallbase.util.network.ServiceLocator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WallpaperDetailViewModel(
    application: Application,
    private val applier: WallpaperApplier = WallpaperApplier(application.applicationContext),
    private val libraryRepository: LibraryRepository = ServiceLocator.libraryRepository,
    private val editor: WallpaperEditor = WallpaperEditor(application.applicationContext)
) : AndroidViewModel(application) {

    private var originalBitmap: Bitmap? = null
    private var processedWallpaper: EditedWallpaper? = null
    private var cachedAdjustments: WallpaperAdjustments? = null
    private var loadJob: Job? = null
    private var previewJob: Job? = null

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
                message = null,
                adjustments = WallpaperAdjustments(),
                editedPreview = null,
                isEditorReady = false,
                isProcessingEdits = true
            )
        }

        loadWallpaperForEditing(wallpaper)

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

    private fun loadWallpaperForEditing(wallpaper: WallpaperItem) {
        previewJob?.cancel()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            processedWallpaper?.bitmap?.takeIf { !it.isRecycled }?.recycle()
            processedWallpaper = null
            cachedAdjustments = null
            originalBitmap?.takeIf { !it.isRecycled }?.recycle()
            originalBitmap = null

            val model: Any = wallpaper.localUri?.takeIf { it.isNotBlank() }
                ?.let { Uri.parse(it) }
                ?: wallpaper.imageUrl

            val loaded = runCatching { editor.loadOriginalBitmap(model) }
            loaded.onSuccess { bitmap ->
                originalBitmap = bitmap
                generatePreviewForAdjustments(_uiState.value.adjustments)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isProcessingEdits = false,
                        isEditorReady = false,
                        message = throwable.localizedMessage ?: "Unable to load wallpaper for editing"
                    )
                }
            }
        }
    }

    private fun generatePreviewForAdjustments(adjustments: WallpaperAdjustments) {
        val source = originalBitmap ?: run {
            _uiState.update { it.copy(isProcessingEdits = false, isEditorReady = false) }
            return
        }
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            _uiState.update { it.copy(isProcessingEdits = true) }
            val result = runCatching {
                withContext(Dispatchers.Default) {
                    editor.applyAdjustments(source, adjustments)
                }
            }
            result.onSuccess { edited ->
                updateProcessedWallpaper(edited, adjustments)
                _uiState.update {
                    it.copy(
                        editedPreview = edited.bitmap,
                        isProcessingEdits = false,
                        isEditorReady = true
                    )
                }
            }.onFailure { throwable ->
                if (throwable is CancellationException) return@launch
                _uiState.update {
                    it.copy(
                        isProcessingEdits = false,
                        isEditorReady = false,
                        message = throwable.localizedMessage ?: "Unable to process wallpaper adjustments"
                    )
                }
            }
        }
    }

    private fun updateProcessedWallpaper(result: EditedWallpaper, adjustments: WallpaperAdjustments) {
        processedWallpaper?.bitmap?.takeIf { it !== result.bitmap && !it.isRecycled }?.recycle()
        processedWallpaper = result
        cachedAdjustments = adjustments
    }

    private suspend fun prepareEditedWallpaper(): EditedWallpaper? {
        previewJob?.join()
        val adjustments = _uiState.value.adjustments
        processedWallpaper?.let { existing ->
            if (cachedAdjustments == adjustments) {
                return existing
            }
        }
        val source = originalBitmap ?: return null
        val result = runCatching {
            withContext(Dispatchers.Default) {
                editor.applyAdjustments(source, adjustments)
            }
        }
        return result.onSuccess { edited ->
            updateProcessedWallpaper(edited, adjustments)
        }.getOrElse { throwable ->
            if (throwable !is CancellationException) {
                _uiState.update {
                    it.copy(
                        message = throwable.localizedMessage ?: "Unable to process wallpaper adjustments"
                    )
                }
            }
            null
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
            val prepared = prepareEditedWallpaper()
            if (prepared == null) {
                _uiState.update {
                    it.copy(
                        isApplying = false,
                        message = "Unable to prepare wallpaper for preview"
                    )
                }
                return@launch
            }
            val previewResult = applier.createSystemPreview(prepared, target)
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

    fun updateBrightness(value: Float) {
        val clipped = value.coerceIn(-0.5f, 0.5f)
        val current = _uiState.value.adjustments
        if (current.brightness == clipped) return
        _uiState.update { it.copy(adjustments = current.copy(brightness = clipped)) }
        cachedAdjustments = null
        generatePreviewForAdjustments(_uiState.value.adjustments)
    }

    fun updateFilter(filter: WallpaperFilter) {
        val current = _uiState.value.adjustments
        if (current.filter == filter) return
        _uiState.update { it.copy(adjustments = current.copy(filter = filter)) }
        cachedAdjustments = null
        generatePreviewForAdjustments(_uiState.value.adjustments)
    }

    fun updateCrop(crop: WallpaperCrop) {
        val current = _uiState.value.adjustments
        if (current.crop == crop) return
        _uiState.update { it.copy(adjustments = current.copy(crop = crop)) }
        cachedAdjustments = null
        generatePreviewForAdjustments(_uiState.value.adjustments)
    }

    fun resetAdjustments() {
        _uiState.update { it.copy(adjustments = WallpaperAdjustments()) }
        cachedAdjustments = null
        generatePreviewForAdjustments(_uiState.value.adjustments)
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
            val prepared = prepareEditedWallpaper()
            if (prepared == null) {
                _uiState.update {
                    it.copy(
                        isApplying = false,
                        message = reason?.let { detail ->
                            "Preview unavailable ($detail). Unable to prepare wallpaper"
                        } ?: "Unable to prepare wallpaper"
                    )
                }
                return@launch
            }
            val result = applier.apply(prepared, target)
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
            val prepared = prepareEditedWallpaper()
            val result = if (prepared != null) {
                runCatching { libraryRepository.saveEditedWallpaper(wallpaper, prepared) }
            } else {
                runCatching { libraryRepository.downloadWallpapers(listOf(wallpaper)) }
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
        val adjustments: WallpaperAdjustments = WallpaperAdjustments(),
        val editedPreview: Bitmap? = null,
        val isEditorReady: Boolean = false,
        val isProcessingEdits: Boolean = false,
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

    override fun onCleared() {
        super.onCleared()
        loadJob?.cancel()
        previewJob?.cancel()
        processedWallpaper?.bitmap?.takeIf { !it.isRecycled }?.recycle()
        processedWallpaper = null
        originalBitmap?.takeIf { !it.isRecycled }?.recycle()
        originalBitmap = null
    }
}
