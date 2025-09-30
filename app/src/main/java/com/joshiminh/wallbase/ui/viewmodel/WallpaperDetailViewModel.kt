@file:Suppress("unused", "UnusedVariable")

package com.joshiminh.wallbase.ui.viewmodel

import android.Manifest
import android.annotation.SuppressLint
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
import com.joshiminh.wallbase.util.wallpapers.WallpaperAdjustments
import com.joshiminh.wallbase.util.wallpapers.WallpaperApplier
import com.joshiminh.wallbase.util.wallpapers.WallpaperCrop
import com.joshiminh.wallbase.util.wallpapers.WallpaperCropSettings
import com.joshiminh.wallbase.util.wallpapers.WallpaperEditor
import com.joshiminh.wallbase.util.wallpapers.WallpaperFilter
import com.joshiminh.wallbase.util.wallpapers.WallpaperTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WallpaperDetailViewModel(
    application: Application,
    private val applier: WallpaperApplier = WallpaperApplier(application.applicationContext),
    private val libraryRepository: LibraryRepository = ServiceLocator.libraryRepository,
    private val editor: WallpaperEditor = WallpaperEditor(application.applicationContext),
    private val settingsRepository: SettingsRepository = ServiceLocator.settingsRepository
) : AndroidViewModel(application) {

    private var originalBitmap: Bitmap? = null
    private var processedWallpaper: EditedWallpaper? = null
    private var cachedAdjustments: WallpaperAdjustments? = null
    private var previewJob: Job? = null
    private var persistAdjustmentsJob: Job? = null
    private var autoDownloadEnabled: Boolean = false
    private var storageLimitBytes: Long = 0L

    private val _uiState = MutableStateFlow(
        WallpaperDetailUiState(
            hasWallpaperPermission = hasSetWallpaperPermission(application)
        )
    )
    val uiState: StateFlow<WallpaperDetailUiState> = _uiState.asStateFlow()

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
        val sanitizedCrop = wallpaper.cropSettings?.sanitized()
        val normalizedWallpaper = if (sanitizedCrop != null && sanitizedCrop != wallpaper.cropSettings) {
            wallpaper.copy(cropSettings = sanitizedCrop)
        } else {
            wallpaper
        }
        val initialCrop = sanitizedCrop?.let { WallpaperCrop.Custom(it) } ?: WallpaperCrop.Auto
        if (_uiState.value.wallpaper?.id != normalizedWallpaper.id) {
            resetEditorState()
            persistAdjustmentsJob?.cancel()
        }
        _uiState.update { current ->
            if (current.wallpaper?.id == normalizedWallpaper.id) current
            else current.copy(
                wallpaper = normalizedWallpaper,
                isApplying = false,
                isAddingToLibrary = false,
                isRemovingFromLibrary = false,
                isInLibrary = normalizedWallpaper.sourceKey == SourceKeys.LOCAL,
                isDownloading = false,
                isRemovingDownload = false,
                isDownloaded = normalizedWallpaper.isDownloaded && !normalizedWallpaper.localUri.isNullOrBlank(),
                showRemoveDownloadConfirmation = false,
                pendingPreview = null,
                pendingFallback = null,
                message = null,
                adjustments = WallpaperAdjustments(crop = initialCrop),
                editedPreview = null,
                isEditorReady = false,
                isProcessingEdits = false,
                isAddingToAlbum = false
            )
        }

        val sourceKey = normalizedWallpaper.sourceKey
        if (sourceKey != null) {
            viewModelScope.launch {
                val libraryState = runCatching { libraryRepository.getWallpaperLibraryState(normalizedWallpaper) }
                    .getOrNull()
                if (libraryState != null) {
                    var shouldRefreshPreview = false
                    _uiState.update { current ->
                        if (current.wallpaper?.id == normalizedWallpaper.id) {
                            val storedAdjustments = libraryState.adjustments?.sanitized()
                            val storedCrop = storedAdjustments?.normalizedCropSettings()
                            val legacyCrop = libraryState.cropSettings?.sanitized()
                            val resolvedCrop = storedCrop ?: legacyCrop
                            val updatedWallpaper = current.wallpaper.copy(
                                localUri = libraryState.localUri,
                                isDownloaded = libraryState.isDownloaded,
                                cropSettings = resolvedCrop ?: current.wallpaper.cropSettings
                            )
                            val updatedAdjustments = when {
                                storedAdjustments != null -> {
                                    if (current.adjustments != storedAdjustments) {
                                        shouldRefreshPreview = true
                                    }
                                    storedAdjustments
                                }
                                resolvedCrop != null && current.adjustments.crop == WallpaperCrop.Auto -> {
                                    shouldRefreshPreview = true
                                    current.adjustments.copy(crop = WallpaperCrop.Custom(resolvedCrop))
                                }
                                resolvedCrop == null && current.adjustments.crop is WallpaperCrop.Custom -> {
                                    shouldRefreshPreview = true
                                    current.adjustments.copy(crop = WallpaperCrop.Auto)
                                }
                                current.adjustments.crop is WallpaperCrop.Custom && resolvedCrop != null -> {
                                    val existing = (current.adjustments.crop as WallpaperCrop.Custom).settings
                                    if (existing != resolvedCrop) {
                                        shouldRefreshPreview = true
                                        current.adjustments.copy(crop = WallpaperCrop.Custom(resolvedCrop))
                                    } else {
                                        current.adjustments
                                    }
                                }
                                else -> current.adjustments
                            }
                            current.copy(
                                wallpaper = updatedWallpaper,
                                isInLibrary = libraryState.isInLibrary,
                                isDownloaded = libraryState.isDownloaded,
                                adjustments = updatedAdjustments
                            )
                        } else {
                            current
                        }
                    }
                    if (shouldRefreshPreview) {
                        cachedAdjustments = null
                        generatePreviewForAdjustments(_uiState.value.adjustments)
                    }
                }
            }
        }
    }

    fun prepareEditor() {
        if (_uiState.value.isProcessingEdits) return
        viewModelScope.launch {
            ensureEditorLoaded(force = false)
        }
    }

    private suspend fun ensureEditorLoaded(force: Boolean = false): Boolean {
        val wallpaper = _uiState.value.wallpaper ?: return false
        val existing = originalBitmap?.takeIf { !it.isRecycled }
        if (!force && existing != null) {
            return true
        }

        recycleProcessedWallpaper()
        if (force) {
            originalBitmap?.takeIf { !it.isRecycled }?.recycle()
            originalBitmap = null
        }

        _uiState.update { it.copy(isProcessingEdits = true, isEditorReady = false) }
        val model: Any = wallpaper.localUri?.takeIf { it.isNotBlank() }?.toUri()
            ?: wallpaper.imageUrl
        val loaded = runCatching {
            withContext(Dispatchers.IO) { editor.loadOriginalBitmap(model) }
        }
        return loaded.fold(
            onSuccess = { bitmap ->
                originalBitmap = bitmap
                generatePreviewForAdjustments(_uiState.value.adjustments)
                true
            },
            onFailure = { throwable ->
                _uiState.update {
                    it.copy(
                        isProcessingEdits = false,
                        isEditorReady = false,
                        message = throwable.localizedMessage ?: "Unable to load wallpaper for editing"
                    )
                }
                false
            }
        )
    }

    private fun resetEditorState() {
        previewJob?.cancel()
        recycleProcessedWallpaper()
        originalBitmap?.takeIf { !it.isRecycled }?.recycle()
        originalBitmap = null
        cachedAdjustments = null
    }

    private fun recycleProcessedWallpaper() {
        processedWallpaper?.bitmap?.takeIf { !it.isRecycled }?.recycle()
        processedWallpaper = null
        cachedAdjustments = null
    }

    private fun schedulePersistAdjustments(immediate: Boolean = false) {
        val currentWallpaper = _uiState.value.wallpaper ?: return
        val adjustments = _uiState.value.adjustments
        persistAdjustmentsJob?.cancel()
        persistAdjustmentsJob = viewModelScope.launch {
            if (!immediate) {
                delay(400)
            }
            runCatching { libraryRepository.updateAdjustments(currentWallpaper, adjustments) }
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
        if (!ensureEditorLoaded()) {
            return null
        }
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

    @SuppressLint("QueryPermissionsNeeded")
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
            schedulePersistAdjustments(immediate = true)
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
        schedulePersistAdjustments()
    }

    fun updateFilter(filter: WallpaperFilter) {
        val current = _uiState.value.adjustments
        if (current.filter == filter) return
        _uiState.update { it.copy(adjustments = current.copy(filter = filter)) }
        cachedAdjustments = null
        generatePreviewForAdjustments(_uiState.value.adjustments)
        schedulePersistAdjustments()
    }

    fun updateCrop(crop: WallpaperCrop) {
        val normalized = when (crop) {
            is WallpaperCrop.Custom -> WallpaperCrop.Custom(crop.settings.sanitized())
            else -> crop
        }
        val current = _uiState.value.adjustments
        if (current.crop == normalized) return
        _uiState.update { state ->
            val updatedWallpaper = state.wallpaper?.let { existing ->
                when (normalized) {
                    is WallpaperCrop.Custom -> existing.copy(cropSettings = normalized.settings)
                    else -> existing.copy(cropSettings = null)
                }
            }
            state.copy(
                adjustments = current.copy(crop = normalized),
                wallpaper = updatedWallpaper
            )
        }
        cachedAdjustments = null
        generatePreviewForAdjustments(_uiState.value.adjustments)
        schedulePersistAdjustments()
    }

    fun resetAdjustments() {
        _uiState.update {
            it.copy(
                adjustments = WallpaperAdjustments(),
                wallpaper = it.wallpaper?.copy(cropSettings = null)
            )
        }
        cachedAdjustments = null
        generatePreviewForAdjustments(_uiState.value.adjustments)
        schedulePersistAdjustments()
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
            val added = result.getOrNull() == true
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
            val prepared = if (autoInitiated) null else prepareEditedWallpaper()
            val limit = storageLimitBytes
            val result = if (prepared != null) {
                runCatching { libraryRepository.saveEditedWallpaper(wallpaper, prepared, limit) }
            } else {
                runCatching { libraryRepository.downloadWallpapers(listOf(wallpaper), limit) }
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
        previewJob?.cancel()
        processedWallpaper?.bitmap?.takeIf { !it.isRecycled }?.recycle()
        processedWallpaper = null
        originalBitmap?.takeIf { !it.isRecycled }?.recycle()
        originalBitmap = null
    }
}