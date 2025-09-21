package com.joshiminh.wallbase.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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

class AlbumDetailViewModel(
    private val albumId: Long,
    private val repository: LibraryRepository
) : ViewModel() {

    private val sortOption = MutableStateFlow(WallpaperSortOption.RECENTLY_ADDED)

    val uiState: StateFlow<AlbumDetailUiState> = combine(
        repository.observeAlbum(albumId),
        sortOption
    ) { detail, sort ->
        if (detail == null) {
            AlbumDetailUiState(isLoading = false, notFound = true, wallpaperSortOption = sort)
        } else {
            AlbumDetailUiState(
                isLoading = false,
                albumTitle = detail.title,
                wallpapers = detail.wallpapers.sortedWith(sort),
                notFound = false,
                wallpaperSortOption = sort
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

    data class AlbumDetailUiState(
        val isLoading: Boolean = false,
        val albumTitle: String? = null,
        val wallpapers: List<WallpaperItem> = emptyList(),
        val notFound: Boolean = false,
        val wallpaperSortOption: WallpaperSortOption = WallpaperSortOption.RECENTLY_ADDED
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
