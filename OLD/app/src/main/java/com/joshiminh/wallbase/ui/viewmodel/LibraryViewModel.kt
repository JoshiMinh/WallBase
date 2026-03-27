@file:Suppress("UNCHECKED_CAST")

package com.joshiminh.wallbase.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.joshiminh.wallbase.data.entity.album.AlbumItem
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem
import com.joshiminh.wallbase.data.repository.AlbumLayout
import com.joshiminh.wallbase.data.repository.LibraryRepository
import com.joshiminh.wallbase.data.repository.LibraryRepository.DirectAddResult
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
import kotlinx.coroutines.flow.map
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
    private val selectionAction = MutableStateFlow<SelectionAction?>(null)
    private val directAddInProgress = MutableStateFlow(false)
    private val directAddStatus = MutableStateFlow<Boolean?>(null)
    private val wallpaperSort = MutableStateFlow(WallpaperSortOption.RECENTLY_ADDED)
    private val albumSort = MutableStateFlow(AlbumSortOption.TITLE_ASCENDING)
    private var storageLimitBytes: Long = 0L

    val uiState: StateFlow<LibraryUiState> =
        combine(
            repository.observeSavedWallpapers(),
            repository.observeAlbums(),
            isCreatingAlbum,
            selectionActionInProgress,
            selectionAction,
            messageFlow,
            wallpaperSort,
            albumSort,
            settingsRepository.preferences,
            directAddInProgress,
            directAddStatus
        ) { values ->
            values.toLibraryStateInputs()
        }.map { inputs ->
            storageLimitBytes = inputs.preferences.storageLimitBytes

            LibraryUiState(
                wallpapers = inputs.wallpapers.sortedWith(inputs.wallpaperSortOption),
                albums = inputs.albums.sortedWith(inputs.albumSortOption),
                isCreatingAlbum = inputs.isCreatingAlbum,
                isSelectionActionInProgress = inputs.isSelectionActionInProgress,
                selectionAction = inputs.selectionAction,
                message = inputs.message,
                wallpaperSortOption = inputs.wallpaperSortOption,
                albumSortOption = inputs.albumSortOption,
                wallpaperGridColumns = inputs.preferences.wallpaperGridColumns,
                albumLayout = inputs.preferences.albumLayout,
                wallpaperLayout = inputs.preferences.wallpaperLayout,
                isDirectAddInProgress = inputs.isDirectAddInProgress,
                directAddCompleted = inputs.directAddCompleted
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

    fun addDirectWallpaper(link: String) {
        val trimmed = link.trim()
        if (trimmed.isEmpty()) {
            messageFlow.value = "Enter a direct image link"
            return
        }
        if (directAddInProgress.value) return

        directAddStatus.value = null
        viewModelScope.launch {
            directAddInProgress.value = true
            val outcome = runCatching { repository.addDirectWallpaper(trimmed) }
                .getOrElse { error ->
                    DirectAddResult.Failure(error.localizedMessage ?: "Unable to add wallpaper")
                }
            directAddInProgress.value = false
            when (outcome) {
                is DirectAddResult.Success -> {
                    val title = outcome.wallpaper.title.takeIf { it.isNotBlank() } ?: "Wallpaper"
                    messageFlow.value = "Added \"$title\" to your library"
                    directAddStatus.value = true
                }

                is DirectAddResult.AlreadyExists -> {
                    val title = outcome.wallpaper?.title?.takeIf { it.isNotBlank() } ?: "Wallpaper"
                    messageFlow.value = "\"$title\" is already in your library"
                    directAddStatus.value = true
                }

                is DirectAddResult.Failure -> {
                    messageFlow.value = outcome.reason
                    directAddStatus.value = false
                }
            }
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
            selectionAction.value = SelectionAction.REMOVE_FROM_LIBRARY
            selectionActionInProgress.value = true
            val result = runCatching { repository.removeWallpapers(wallpapers) }
            selectionActionInProgress.value = false
            selectionAction.value = null
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
            selectionAction.value = SelectionAction.ADD_TO_ALBUM
            selectionActionInProgress.value = true
            val result = runCatching { repository.addWallpapersToAlbum(albumId, wallpapers) }
            selectionActionInProgress.value = false
            selectionAction.value = null
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
            selectionAction.value = SelectionAction.DOWNLOAD
            selectionActionInProgress.value = true
            val result = runCatching { repository.downloadWallpapers(wallpapers, storageLimitBytes) }
            selectionActionInProgress.value = false
            selectionAction.value = null
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

    fun deleteAlbums(albumIds: Collection<Long>) {
        if (albumIds.isEmpty() || selectionActionInProgress.value) return
        viewModelScope.launch {
            selectionAction.value = SelectionAction.DELETE_ALBUMS
            selectionActionInProgress.value = true
            val result = runCatching { repository.deleteAlbums(albumIds) }
            selectionActionInProgress.value = false
            selectionAction.value = null
            messageFlow.update {
                result.fold(
                    onSuccess = { deleted ->
                        when {
                            deleted > 1 -> "Deleted $deleted albums"
                            deleted == 1 -> "Deleted 1 album"
                            else -> "No albums were deleted"
                        }
                    },
                    onFailure = { t -> t.localizedMessage ?: "Unable to delete albums" }
                )
            }
        }
    }

    fun removeDownloads(wallpapers: List<WallpaperItem>) {
        if (wallpapers.isEmpty() || selectionActionInProgress.value) return
        viewModelScope.launch {
            selectionAction.value = SelectionAction.REMOVE_DOWNLOADS
            selectionActionInProgress.value = true
            val result = runCatching { repository.removeDownloads(wallpapers) }
            selectionActionInProgress.value = false
            selectionAction.value = null
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
            selectionAction.value = SelectionAction.ADD_TO_ALBUM
            selectionActionInProgress.value = true
            val result = runCatching {
                val album = repository.createAlbum(trimmed)
                val association = repository.addWallpapersToAlbum(album.id, wallpapers)
                album to association
            }
            selectionActionInProgress.value = false
            selectionAction.value = null
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

    fun consumeDirectAddStatus() {
        directAddStatus.value = null
    }

    data class LibraryUiState(
        val wallpapers: List<WallpaperItem> = emptyList(),
        val albums: List<AlbumItem> = emptyList(),
        val isCreatingAlbum: Boolean = false,
        val isSelectionActionInProgress: Boolean = false,
        val selectionAction: SelectionAction? = null,
        val message: String? = null,
        val wallpaperSortOption: WallpaperSortOption = WallpaperSortOption.RECENTLY_ADDED,
        val albumSortOption: AlbumSortOption = AlbumSortOption.TITLE_ASCENDING,
        val wallpaperGridColumns: Int = 2,
        val albumLayout: AlbumLayout = AlbumLayout.CARD_LIST,
        val wallpaperLayout: WallpaperLayout = WallpaperLayout.GRID,
        val isDirectAddInProgress: Boolean = false,
        val directAddCompleted: Boolean? = null
    )

    enum class SelectionAction {
        DOWNLOAD,
        REMOVE_FROM_LIBRARY,
        ADD_TO_ALBUM,
        REMOVE_DOWNLOADS,
        DELETE_ALBUMS
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                ServiceLocator.ensureInitialized(application)
                LibraryViewModel(
                    repository = ServiceLocator.libraryRepository,
                    settingsRepository = ServiceLocator.settingsRepository
                )
            }
        }
    }
}

private data class LibraryStateInputs(
    val wallpapers: List<WallpaperItem>,
    val albums: List<AlbumItem>,
    val isCreatingAlbum: Boolean,
    val isSelectionActionInProgress: Boolean,
    val selectionAction: LibraryViewModel.SelectionAction?,
    val message: String?,
    val wallpaperSortOption: WallpaperSortOption,
    val albumSortOption: AlbumSortOption,
    val preferences: SettingsPreferences,
    val isDirectAddInProgress: Boolean,
    val directAddCompleted: Boolean?
)

private fun Array<Any?>.toLibraryStateInputs(): LibraryStateInputs {
    return LibraryStateInputs(
        wallpapers = this[0] as List<WallpaperItem>,
        albums = this[1] as List<AlbumItem>,
        isCreatingAlbum = this[2] as Boolean,
        isSelectionActionInProgress = this[3] as Boolean,
        selectionAction = this[4] as LibraryViewModel.SelectionAction?,
        message = this[5] as String?,
        wallpaperSortOption = this[6] as WallpaperSortOption,
        albumSortOption = this[7] as AlbumSortOption,
        preferences = this[8] as SettingsPreferences,
        isDirectAddInProgress = this[9] as Boolean,
        directAddCompleted = this[10] as Boolean?
    )
}
