@file:Suppress("UNCHECKED_CAST")

package com.joshiminh.wallbase.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.joshiminh.wallbase.data.entity.album.AlbumItem
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem
import com.joshiminh.wallbase.data.repository.AlbumLayout
import com.joshiminh.wallbase.data.repository.LibraryRepository
import com.joshiminh.wallbase.data.repository.SettingsRepository
import com.joshiminh.wallbase.data.repository.SettingsPreferences
import com.joshiminh.wallbase.data.repository.WallpaperLayout
import com.joshiminh.wallbase.ui.sort.AlbumSortOption
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

class LibraryViewModel(
    private val repository: LibraryRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val messageFlow = MutableStateFlow<String?>(null)
    private val isCreatingAlbum = MutableStateFlow(false)
    private val selectionActionInProgress = MutableStateFlow(false)
    private val wallpaperSort = MutableStateFlow(WallpaperSortOption.RECENTLY_ADDED)
    private val albumSort = MutableStateFlow(AlbumSortOption.TITLE_ASCENDING)
    private var storageLimitBytes: Long = 0L

    val uiState: StateFlow<LibraryUiState> =
        combine(
            repository.observeSavedWallpapers(),
            repository.observeAlbums(),
            isCreatingAlbum,
            selectionActionInProgress,
            messageFlow,
            wallpaperSort,
            albumSort,
            settingsRepository.preferences
        ) { arr: Array<Any?> ->
            val wallpapers = arr[0] as List<WallpaperItem>
            val albums = arr[1] as List<AlbumItem>
            val creating = arr[2] as Boolean
            val selectionBusy = arr[3] as Boolean
            val message = arr[4] as String?
            val wallpaperSortOption = arr[5] as WallpaperSortOption
            val albumSortOption = arr[6] as AlbumSortOption
            val preferences = arr[7] as SettingsPreferences
            storageLimitBytes = preferences.storageLimitBytes

            LibraryUiState(
                wallpapers = wallpapers.sortedWith(wallpaperSortOption),
                albums = albums.sortedWith(albumSortOption),
                isCreatingAlbum = creating,
                isSelectionActionInProgress = selectionBusy,
                message = message,
                wallpaperSortOption = wallpaperSortOption,
                albumSortOption = albumSortOption,
                wallpaperGridColumns = preferences.wallpaperGridColumns,
                albumLayout = preferences.albumLayout,
                wallpaperLayout = preferences.wallpaperLayout
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = LibraryUiState()
        )

    fun updateWallpaperSort(option: WallpaperSortOption) {
        wallpaperSort.value = option
    }

    fun updateAlbumSort(option: AlbumSortOption) {
        albumSort.value = option
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

    fun updateAlbumLayout(layout: AlbumLayout) {
        viewModelScope.launch {
            settingsRepository.setAlbumLayout(layout)
        }
    }

    fun createAlbum(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) {
            messageFlow.value = "Enter a name for your album"
            return
        }
        if (isCreatingAlbum.value) return

        viewModelScope.launch {
            isCreatingAlbum.value = true
            val result = runCatching { repository.createAlbum(trimmed) }
            isCreatingAlbum.value = false
            messageFlow.update {
                result.fold(
                    onSuccess = { album -> "Created \"${album.title}\"" },
                    onFailure = { t -> t.localizedMessage ?: "Unable to create album" }
                )
            }
        }
    }

    fun removeWallpapers(wallpapers: List<WallpaperItem>) {
        if (wallpapers.isEmpty() || selectionActionInProgress.value) return
        viewModelScope.launch {
            selectionActionInProgress.value = true
            val result = runCatching { repository.removeWallpapers(wallpapers) }
            selectionActionInProgress.value = false
            messageFlow.update {
                result.fold(
                    onSuccess = { removed ->
                        when {
                            removed > 1 -> "Removed $removed wallpapers from your library"
                            removed == 1 -> "Removed 1 wallpaper from your library"
                            else -> "No wallpapers were removed"
                        }
                    },
                    onFailure = { t -> t.localizedMessage ?: "Unable to remove wallpapers" }
                )
            }
        }
    }

    fun addWallpapersToAlbum(albumId: Long, wallpapers: List<WallpaperItem>) {
        if (wallpapers.isEmpty() || selectionActionInProgress.value) return
        viewModelScope.launch {
            selectionActionInProgress.value = true
            val result = runCatching { repository.addWallpapersToAlbum(albumId, wallpapers) }
            selectionActionInProgress.value = false
            messageFlow.update {
                result.fold(
                    onSuccess = { outcome ->
                        when {
                            outcome.addedToAlbum > 0 && (outcome.alreadyPresent > 0 || outcome.skipped > 0) ->
                                "Added ${outcome.addedToAlbum} wallpapers (skipped ${outcome.alreadyPresent + outcome.skipped})"
                            outcome.addedToAlbum > 0 ->
                                "Added ${outcome.addedToAlbum} wallpapers to the album"
                            outcome.alreadyPresent > 0 && outcome.skipped == 0 ->
                                "All selected wallpapers are already in this album"
                            outcome.skipped > 0 ->
                                "Unable to add ${outcome.skipped} wallpapers to the album"
                            else -> "All selected wallpapers are already in this album"
                        }
                    },
                    onFailure = { t -> t.localizedMessage ?: "Unable to update album" }
                )
            }
        }
    }

    fun downloadWallpapers(wallpapers: List<WallpaperItem>) {
        if (wallpapers.isEmpty() || selectionActionInProgress.value) return
        viewModelScope.launch {
            selectionActionInProgress.value = true
            val result = runCatching { repository.downloadWallpapers(wallpapers, storageLimitBytes) }
            selectionActionInProgress.value = false
            messageFlow.update {
                result.fold(
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
                                "Selected wallpapers are already saved locally"
                            else -> "No wallpapers were downloaded"
                        }
                    },
                    onFailure = { throwable ->
                        throwable.localizedMessage ?: "Unable to download wallpapers"
                    }
                )
            }
        }
    }

    fun removeDownloads(wallpapers: List<WallpaperItem>) {
        if (wallpapers.isEmpty() || selectionActionInProgress.value) return
        viewModelScope.launch {
            selectionActionInProgress.value = true
            val result = runCatching { repository.removeDownloads(wallpapers) }
            selectionActionInProgress.value = false
            messageFlow.update {
                result.fold(
                    onSuccess = { summary ->
                        when {
                            summary.removed > 0 && summary.failed > 0 ->
                                "Removed downloads for ${summary.removed} wallpapers (failed ${summary.failed})"
                            summary.removed > 0 ->
                                "Removed downloads for ${summary.removed} wallpapers"
                            summary.skipped > 0 ->
                                "Selected wallpapers don't have local downloads"
                            else -> "No downloads were removed"
                        }
                    },
                    onFailure = { throwable ->
                        throwable.localizedMessage ?: "Unable to remove downloads"
                    }
                )
            }
        }
    }

    fun createAlbumAndAdd(title: String, wallpapers: List<WallpaperItem>) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) {
            messageFlow.value = "Enter a name for your album"
            return
        }
        if (wallpapers.isEmpty() || selectionActionInProgress.value) return
        viewModelScope.launch {
            selectionActionInProgress.value = true
            val result = runCatching {
                val album = repository.createAlbum(trimmed)
                val association = repository.addWallpapersToAlbum(album.id, wallpapers)
                album to association
            }
            selectionActionInProgress.value = false
            messageFlow.update {
                result.fold(
                    onSuccess = { (album, outcome) ->
                        when {
                            outcome.addedToAlbum > 0 -> "Added ${outcome.addedToAlbum} wallpapers to \"${album.title}\""
                            outcome.alreadyPresent > 0 -> "Wallpapers are already in \"${album.title}\""
                            outcome.skipped > 0 -> "Unable to add ${outcome.skipped} wallpapers to \"${album.title}\""
                            else -> "Updated \"${album.title}\""
                        }
                    },
                    onFailure = { t -> t.localizedMessage ?: "Unable to create album" }
                )
            }
        }
    }

    fun consumeMessage() {
        messageFlow.value = null
    }

    data class LibraryUiState(
        val wallpapers: List<WallpaperItem> = emptyList(),
        val albums: List<AlbumItem> = emptyList(),
        val isCreatingAlbum: Boolean = false,
        val isSelectionActionInProgress: Boolean = false,
        val message: String? = null,
        val wallpaperSortOption: WallpaperSortOption = WallpaperSortOption.RECENTLY_ADDED,
        val albumSortOption: AlbumSortOption = AlbumSortOption.TITLE_ASCENDING,
        val wallpaperGridColumns: Int = 2,
        val albumLayout: AlbumLayout = AlbumLayout.CARD_LIST,
        val wallpaperLayout: WallpaperLayout = WallpaperLayout.GRID
    )

    companion object {
        val Factory = viewModelFactory {
            initializer {
                LibraryViewModel(
                    repository = ServiceLocator.libraryRepository,
                    settingsRepository = ServiceLocator.settingsRepository
                )
            }
        }
    }
}