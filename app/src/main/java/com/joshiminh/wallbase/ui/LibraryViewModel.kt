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

class LibraryViewModel(
    private val repository: LibraryRepository
) : ViewModel() {

    val uiState: StateFlow<LibraryUiState> = repository.observeSavedWallpapers()
        .map { LibraryUiState(wallpapers = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState()
        )

    data class LibraryUiState(
        val wallpapers: List<WallpaperItem> = emptyList()
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
