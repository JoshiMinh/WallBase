package com.joshiminh.wallbase.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.joshiminh.wallbase.data.entity.source.SourceKeys
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem
import com.joshiminh.wallbase.data.repository.LibraryRepository
import com.joshiminh.wallbase.ui.sort.WallpaperSortOption
import com.joshiminh.wallbase.ui.sort.sortedWith
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
    private val repository: LibraryRepository
) : ViewModel() {

    private val sortOption = MutableStateFlow(WallpaperSortOption.RECENTLY_ADDED)
    private val downloading = MutableStateFlow(false)
    private val removingDownloads = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val showRemoveDownloads = MutableStateFlow(false)

    val uiState: StateFlow<AlbumDetailUiState> = combine(
        repository.observeAlbum(albumId),
        sortOption,
        downloading,
        removingDownloads,
        message,
        showRemoveDownloads
    ) { detail, sort, isDownloading, isRemoving, message, showRemove ->
        if (detail == null) {
            AlbumDetailUiState(
                isLoading = false,
                notFound = true,
                wallpaperSortOption = sort,
                isDownloading = isDownloading,
                isRemovingDownloads = isRemoving,
                message = message,
                showRemoveDownloadsConfirmation = showRemove
            )
        } else {
            val sortedWallpapers = detail.wallpapers.sortedWith(sort)
            AlbumDetailUiState(
                isLoading = false,
                albumTitle = detail.title,
                wallpapers = sortedWallpapers,
                notFound = false,
                wallpaperSortOption = sort,
                isDownloading = isDownloading,
                isAlbumDownloaded = sortedWallpapers.isAlbumFullyDownloaded(),
                isRemovingDownloads = isRemoving,
                message = message,
                showRemoveDownloadsConfirmation = showRemove
            )
        }
    }.stateIn(
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
            val result = runCatching { repository.downloadWallpapers(wallpapers) }
            downloading.value = false
            message.value = result.fold(
                onSuccess = { summary ->
                    when {
                        summary.downloaded > 0 && summary.failed > 0 ->
                            "Downloaded ${summary.downloaded} wallpapers (failed ${summary.failed})"
                        summary.downloaded > 0 ->
                            "Downloaded ${summary.downloaded} wallpapers"
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
        val showRemoveDownloadsConfirmation: Boolean = false
    )

    companion object {
        fun provideFactory(albumId: Long) = viewModelFactory {
            initializer {
                AlbumDetailViewModel(
                    albumId = albumId,
                    repository = ServiceLocator.libraryRepository
                )
            }
        }
    }
}

private fun List<WallpaperItem>.isAlbumFullyDownloaded(): Boolean {
    if (isEmpty()) return false
    return all { item ->
        val sourceKey = item.sourceKey
        when {
            sourceKey == null -> false
            sourceKey == SourceKeys.LOCAL -> true
            else -> item.isDownloaded && !item.localUri.isNullOrBlank()
        }
    }
}
