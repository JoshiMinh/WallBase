package com.joshiminh.wallbase.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.joshiminh.wallbase.data.entity.album.AlbumDetail
import com.joshiminh.wallbase.data.entity.source.SourceKeys
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem
import com.joshiminh.wallbase.data.repository.LibraryRepository
import com.joshiminh.wallbase.data.repository.SettingsRepository
import com.joshiminh.wallbase.data.repository.WallpaperLayout
import com.joshiminh.wallbase.data.repository.WallpaperRotationRepository
import com.joshiminh.wallbase.data.repository.WallpaperRotationSchedule
import com.joshiminh.wallbase.ui.sort.WallpaperSortOption
import com.joshiminh.wallbase.ui.sort.sortedWith
import com.joshiminh.wallbase.util.network.ServiceLocator
import com.joshiminh.wallbase.util.wallpapers.WallpaperTarget
import com.joshiminh.wallbase.util.wallpapers.rotation.WallpaperRotationDefaults
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AlbumDetailViewModel(
    private val albumId: Long,
    private val repository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val rotationRepository: WallpaperRotationRepository
) : ViewModel() {

    private val sortOption = MutableStateFlow(WallpaperSortOption.RECENTLY_ADDED)
    private val downloading = MutableStateFlow(false)
    private val removingDownloads = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val showRemoveDownloads = MutableStateFlow(false)
    private val rotationUpdating = MutableStateFlow(false)
    private val renamingAlbum = MutableStateFlow(false)
    private val deletingAlbum = MutableStateFlow(false)
    private val albumDeleted = MutableStateFlow(false)
    private var storageLimitBytes: Long = 0L

    private data class Base(
        val detail: AlbumDetail?, // whatever type repository.observeAlbum(albumId) emits
        val sort: WallpaperSortOption,
        val isDownloading: Boolean,
        val isRemoving: Boolean,
        val message: String?,
        val rotation: WallpaperRotationSchedule?,
        val isRotationUpdating: Boolean
    )

    private val baseState = combine(
        repository.observeAlbum(albumId),
        sortOption,
        downloading,
        removingDownloads,
        message
    ) { detail, sort, isDownloading, isRemoving, message ->
        Base(
            detail = detail,
            sort = sort,
            isDownloading = isDownloading,
            isRemoving = isRemoving,
            message = message,
            rotation = null,
            isRotationUpdating = false
        )
    }
        .combine(rotationRepository.observeSchedule(albumId)) { base, rotation ->
            base.copy(rotation = rotation)
        }
        .combine(rotationUpdating) { base, updating ->
            base.copy(isRotationUpdating = updating)
        }

    val uiState: StateFlow<AlbumDetailUiState> =
        baseState
            .combine(settingsRepository.preferences) { base, preferences ->
                storageLimitBytes = preferences.storageLimitBytes
                val layout = when (preferences.wallpaperLayout) {
                    WallpaperLayout.LIST -> WallpaperLayout.GRID
                    else -> preferences.wallpaperLayout
                }
                if (base.detail == null) {
                    AlbumDetailUiState(
                        isLoading = false,
                        notFound = true,
                        wallpaperSortOption = base.sort,
                        isDownloading = base.isDownloading,
                        isRemovingDownloads = base.isRemoving,
                        message = base.message,
                        wallpaperGridColumns = preferences.wallpaperGridColumns,
                        wallpaperLayout = layout,
                        rotation = base.rotation.toUiState(base.isRotationUpdating)
                    )
                } else {
                    val sorted = base.detail.wallpapers.sortedWith(base.sort)
                    AlbumDetailUiState(
                        isLoading = false,
                        albumTitle = base.detail.title,
                        wallpapers = sorted,
                        notFound = false,
                        wallpaperSortOption = base.sort,
                        isDownloading = base.isDownloading,
                        isAlbumDownloaded = sorted.isAlbumFullyDownloaded(),
                        isRemovingDownloads = base.isRemoving,
                        message = base.message,
                        wallpaperGridColumns = preferences.wallpaperGridColumns,
                        wallpaperLayout = layout,
                        rotation = base.rotation.toUiState(base.isRotationUpdating)
                    )
                }
            }
            .combine(showRemoveDownloads) { state, showRemove ->
                state.copy(showRemoveDownloadsConfirmation = showRemove)
            }
            .combine(renamingAlbum) { state, renaming ->
                state.copy(isRenamingAlbum = renaming)
            }
            .combine(deletingAlbum) { state, deleting ->
                state.copy(isDeletingAlbum = deleting)
            }
            .combine(albumDeleted) { state, deleted ->
                state.copy(isAlbumDeleted = deleted)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = AlbumDetailUiState(isLoading = true)
            )

    fun updateSort(option: WallpaperSortOption) {
        sortOption.update { option }
    }

    fun downloadAlbum() {
        val wallpapers = uiState.value.wallpapers
        if (wallpapers.isEmpty() || downloading.value) return
        viewModelScope.launch {
            downloading.value = true
            val result = runCatching { repository.downloadWallpapers(wallpapers, storageLimitBytes) }
            downloading.value = false
            message.value = result.fold(
                onSuccess = { summary ->
                    when {
                        summary.downloaded > 0 && summary.failed > 0 ->
                            "Downloaded ${summary.downloaded} wallpapers (failed ${summary.failed})"
                        summary.downloaded > 0 && summary.blocked > 0 ->
                            "Downloaded ${summary.downloaded} wallpapers (blocked ${summary.blocked} by storage limit)"
                        summary.downloaded > 0 ->
                            "Downloaded ${summary.downloaded} wallpapers"
                        summary.blocked > 0 ->
                            "Storage limit reached. Download blocked."
                        summary.skipped > 0 ->
                            "All wallpapers are already saved locally"
                        else -> "No wallpapers were downloaded"
                    }
                },
                onFailure = { throwable ->
                    throwable.localizedMessage ?: "Unable to download album"
                }
            )
        }
    }

    fun promptRemoveDownloads() {
        if (uiState.value.isAlbumDownloaded && !removingDownloads.value) {
            showRemoveDownloads.value = true
        }
    }

    fun dismissRemoveDownloadsPrompt() {
        showRemoveDownloads.value = false
    }

    fun removeAlbumDownloads() {
        val wallpapers = uiState.value.wallpapers
        if (wallpapers.isEmpty() || removingDownloads.value) return
        viewModelScope.launch {
            removingDownloads.value = true
            val result = runCatching { repository.removeDownloads(wallpapers) }
            removingDownloads.value = false
            showRemoveDownloads.value = false
            message.value = result.fold(
                onSuccess = { summary ->
                    when {
                        summary.removed > 0 && summary.failed > 0 ->
                            "Removed downloads for ${summary.removed} wallpapers (failed ${summary.failed})"
                        summary.removed > 0 ->
                            "Removed downloads for ${summary.removed} wallpapers"
                        summary.skipped > 0 ->
                            "No downloaded wallpapers to remove"
                        else -> "No downloads were removed"
                    }
                },
                onFailure = { throwable ->
                    throwable.localizedMessage ?: "Unable to remove downloads"
                }
            )
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    fun consumeAlbumDeleted() {
        albumDeleted.value = false
    }

    fun renameAlbum(newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) {
            message.value = "Enter a name for your album"
            return
        }
        if (renamingAlbum.value) return
        viewModelScope.launch {
            renamingAlbum.value = true
            val result = runCatching { repository.renameAlbum(albumId, trimmed) }
            renamingAlbum.value = false
            message.value = result.fold(
                onSuccess = { album -> "Renamed album to \"${album.title}\"" },
                onFailure = { throwable -> throwable.localizedMessage ?: "Unable to rename album" }
            )
        }
    }

    fun deleteAlbum() {
        if (deletingAlbum.value) return
        viewModelScope.launch {
            deletingAlbum.value = true
            val result = runCatching {
                rotationRepository.disableSchedule(albumId)
                repository.deleteAlbums(listOf(albumId))
            }
            deletingAlbum.value = false
            result.onSuccess { deleted ->
                if (deleted > 0) {
                    albumDeleted.value = true
                } else {
                    message.value = "Album not found"
                }
            }.onFailure { throwable ->
                message.value = throwable.localizedMessage ?: "Unable to delete album"
            }
        }
    }

    fun toggleRotation(enabled: Boolean) {
        if (rotationUpdating.value) return
        if (enabled && uiState.value.wallpapers.isEmpty()) {
            message.value = "Add wallpapers to this album before enabling rotation"
            return
        }
        viewModelScope.launch {
            rotationUpdating.value = true
            val rotationState = uiState.value.rotation
            val result = runCatching {
                if (enabled) {
                    rotationRepository.enableSchedule(albumId, rotationState.intervalMinutes, rotationState.target)
                } else {
                    rotationRepository.disableSchedule(albumId)
                }
            }
            rotationUpdating.value = false
            message.value = result.fold(
                onSuccess = {
                    if (enabled) "Scheduled rotation enabled" else "Scheduled rotation paused"
                },
                onFailure = { throwable ->
                    throwable.localizedMessage ?: "Unable to update rotation"
                }
            )
        }
    }

    fun updateRotationInterval(intervalMinutes: Long) {
        if (rotationUpdating.value) return
        val current = uiState.value.rotation.intervalMinutes
        if (current == intervalMinutes) return
        viewModelScope.launch {
            rotationUpdating.value = true
            val result = runCatching { rotationRepository.updateInterval(albumId, intervalMinutes) }
            rotationUpdating.value = false
            message.value = result.fold(
                onSuccess = { "Rotation interval updated" },
                onFailure = { throwable ->
                    throwable.localizedMessage ?: "Unable to update rotation interval"
                }
            )
        }
    }

    fun updateRotationTarget(target: WallpaperTarget) {
        if (rotationUpdating.value) return
        val current = uiState.value.rotation.target
        if (current == target) return
        viewModelScope.launch {
            rotationUpdating.value = true
            val result = runCatching { rotationRepository.updateTarget(albumId, target) }
            rotationUpdating.value = false
            message.value = result.fold(
                onSuccess = { "Rotation target updated" },
                onFailure = { throwable ->
                    throwable.localizedMessage ?: "Unable to update rotation target"
                }
            )
        }
    }

    fun triggerRotationNow() {
        if (rotationUpdating.value) return
        if (!uiState.value.rotation.isEnabled) {
            message.value = "Enable scheduled rotation to start it."
            return
        }
        viewModelScope.launch {
            rotationUpdating.value = true
            val result = runCatching { rotationRepository.triggerRotationNow() }
            rotationUpdating.value = false
            message.value = result.fold(
                onSuccess = { "Rotation started" },
                onFailure = { throwable ->
                    throwable.localizedMessage ?: "Unable to start rotation"
                }
            )
        }
    }

    data class AlbumDetailUiState(
        val isLoading: Boolean = false,
        val albumTitle: String? = null,
        val wallpapers: List<WallpaperItem> = emptyList(),
        val notFound: Boolean = false,
        val wallpaperSortOption: WallpaperSortOption = WallpaperSortOption.RECENTLY_ADDED,
        val isDownloading: Boolean = false,
        val isAlbumDownloaded: Boolean = false,
        val isRemovingDownloads: Boolean = false,
        val message: String? = null,
        val showRemoveDownloadsConfirmation: Boolean = false,
        val wallpaperGridColumns: Int = 2,
        val wallpaperLayout: WallpaperLayout = WallpaperLayout.GRID,
        val rotation: RotationUiState = RotationUiState(),
        val isRenamingAlbum: Boolean = false,
        val isDeletingAlbum: Boolean = false,
        val isAlbumDeleted: Boolean = false
    )

    data class RotationUiState(
        val isEnabled: Boolean = false,
        val isConfigured: Boolean = false,
        val intervalMinutes: Long = WallpaperRotationDefaults.DEFAULT_INTERVAL_MINUTES,
        val target: WallpaperTarget = WallpaperRotationDefaults.DEFAULT_TARGET,
        val lastAppliedAt: Long? = null,
        val isUpdating: Boolean = false
    )

    companion object {
        fun provideFactory(albumId: Long) = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                ServiceLocator.ensureInitialized(application)
                AlbumDetailViewModel(
                    albumId = albumId,
                    repository = ServiceLocator.libraryRepository,
                    settingsRepository = ServiceLocator.settingsRepository,
                    rotationRepository = ServiceLocator.rotationRepository
                )
            }
        }
    }
}

private fun WallpaperRotationSchedule?.toUiState(isUpdating: Boolean): AlbumDetailViewModel.RotationUiState {
    return AlbumDetailViewModel.RotationUiState(
        isEnabled = this?.isEnabled == true,
        isConfigured = this != null,
        intervalMinutes = this?.intervalMinutes ?: WallpaperRotationDefaults.DEFAULT_INTERVAL_MINUTES,
        target = this?.target ?: WallpaperRotationDefaults.DEFAULT_TARGET,
        lastAppliedAt = this?.lastAppliedAt,
        isUpdating = isUpdating
    )
}

private fun List<WallpaperItem>.isAlbumFullyDownloaded(): Boolean {
    if (isEmpty()) return false
    return all { item: WallpaperItem ->
        val sourceKey = item.sourceKey
        when (sourceKey) {
            null -> false
            SourceKeys.LOCAL -> true
            else -> item.isDownloaded && !item.localUri.isNullOrBlank()
        }
    }
}