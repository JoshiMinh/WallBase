package com.joshiminh.wallbase.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.joshiminh.wallbase.data.entity.AlbumDetail
import com.joshiminh.wallbase.data.entity.SourceKeys
import com.joshiminh.wallbase.data.entity.WallpaperItem
import com.joshiminh.wallbase.data.repository.LibraryRepository
import com.joshiminh.wallbase.data.repository.SettingsRepository
import com.joshiminh.wallbase.data.repository.WallpaperLayout
import com.joshiminh.wallbase.util.WallpaperSortOption
import com.joshiminh.wallbase.util.sortedWith
import com.joshiminh.wallbase.util.network.ServiceLocator
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
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val sortOption = MutableStateFlow(WallpaperSortOption.RECENTLY_ADDED)
    private val downloading = MutableStateFlow(false)
    private val removingDownloads = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val showRemoveDownloads = MutableStateFlow(false)
    private val renamingAlbum = MutableStateFlow(false)
    private val deletingAlbum = MutableStateFlow(false)
    private val albumDeleted = MutableStateFlow(false)
    private var storageLimitBytes: Long = 0L

    private data class Base(
        val detail: AlbumDetail?,
        val sort: WallpaperSortOption,
        val isDownloading: Boolean,
        val isRemoving: Boolean,
        val message: String?
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
            message = message
        )
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
                        showDownloadBadge = preferences.showDownloadBadge
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
                        showDownloadBadge = preferences.showDownloadBadge
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

    fun renameWallpaper(wallpaper: WallpaperItem, customTitle: String?) {
        viewModelScope.launch {
            val trimmed = customTitle?.trim()?.takeIf { it.isNotBlank() }
            val success = repository.renameWallpaper(wallpaper, trimmed)
            if (success) {
                message.value = if (trimmed != null) "Wallpaper renamed" else "Wallpaper name reset to original"
            } else {
                message.value = "Unable to rename wallpaper"
            }
        }
    }

    fun updateWallpaperGridColumns(columns: Int) {
        viewModelScope.launch {
            settingsRepository.setWallpaperGridColumns(columns)
        }
    }

    fun updateWallpaperLayout(layout: WallpaperLayout) {
        viewModelScope.launch {
            settingsRepository.setWallpaperLayout(layout)
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
        val showDownloadBadge: Boolean = true,
        val isRenamingAlbum: Boolean = false,
        val isDeletingAlbum: Boolean = false,
        val isAlbumDeleted: Boolean = false
    )

    companion object {
        fun provideFactory(albumId: Long) = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                ServiceLocator.ensureInitialized(application)
                AlbumDetailViewModel(
                    albumId = albumId,
                    repository = ServiceLocator.libraryRepository,
                    settingsRepository = ServiceLocator.settingsRepository
                )
            }
        }
    }
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
