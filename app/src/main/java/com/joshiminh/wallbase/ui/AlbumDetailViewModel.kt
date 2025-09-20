package com.joshiminh.wallbase.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.joshiminh.wallbase.data.library.LibraryRepository
import com.joshiminh.wallbase.data.wallpapers.WallpaperItem
import com.joshiminh.wallbase.di.ServiceLocator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class AlbumDetailViewModel(
    private val albumId: Long,
    private val repository: LibraryRepository
) : ViewModel() {

    val uiState: StateFlow<AlbumDetailUiState> = repository.observeAlbum(albumId)
        .map { detail ->
            if (detail == null) {
                AlbumDetailUiState(isLoading = false, notFound = true)
            } else {
                AlbumDetailUiState(
                    isLoading = false,
                    albumTitle = detail.title,
                    wallpapers = detail.wallpapers,
                    notFound = false
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AlbumDetailUiState(isLoading = true)
        )

    data class AlbumDetailUiState(
        val isLoading: Boolean = false,
        val albumTitle: String? = null,
        val wallpapers: List<WallpaperItem> = emptyList(),
        val notFound: Boolean = false
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
