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

    val uiState: StateFlow<LibraryUiState> = combine(
        repository.observeSavedWallpapers(),
        repository.observeAlbums(),
        isCreatingAlbum,
        messageFlow
    ) { wallpapers, albums, creating, message ->
        LibraryUiState(
            wallpapers = wallpapers,
            albums = albums,
            isCreatingAlbum = creating,
            message = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
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

    fun consumeMessage() {
        messageFlow.value = null
    }

    data class LibraryUiState(
        val wallpapers: List<WallpaperItem> = emptyList(),
        val albums: List<AlbumItem> = emptyList(),
        val isCreatingAlbum: Boolean = false,
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
