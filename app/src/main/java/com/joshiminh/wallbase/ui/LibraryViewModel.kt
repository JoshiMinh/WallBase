package com.joshiminh.wallbase.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.joshiminh.wallbase.data.library.AlbumItem
import com.joshiminh.wallbase.data.library.LibraryRepository
import com.joshiminh.wallbase.data.wallpapers.WallpaperItem
import com.joshiminh.wallbase.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val repository: LibraryRepository
) : ViewModel() {

    private val messageFlow = MutableStateFlow<String?>(null)
    private val isCreatingAlbum = MutableStateFlow(false)
    private val selectionActionInProgress = MutableStateFlow(false)

    val uiState: StateFlow<LibraryUiState> = combine(
        repository.observeSavedWallpapers(),
        repository.observeAlbums(),
        isCreatingAlbum,
        selectionActionInProgress,
        messageFlow
    ) { wallpapers, albums, creating, selectionBusy, message ->
        LibraryUiState(
            wallpapers = wallpapers,
            albums = albums,
            isCreatingAlbum = creating,
            isSelectionActionInProgress = selectionBusy,
            message = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LibraryUiState()
    )

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
                    onFailure = { throwable ->
                        throwable.localizedMessage ?: "Unable to create album"
                    }
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
                    onFailure = { throwable ->
                        throwable.localizedMessage ?: "Unable to remove wallpapers"
                    }
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
                    onFailure = { throwable ->
                        throwable.localizedMessage ?: "Unable to update album"
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
                    onFailure = { throwable ->
                        throwable.localizedMessage ?: "Unable to create album"
                    }
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
        val message: String? = null
    )

    companion object {
        val Factory = viewModelFactory {
            initializer {
                LibraryViewModel(
                    repository = ServiceLocator.libraryRepository
                )
            }
        }
    }
}
