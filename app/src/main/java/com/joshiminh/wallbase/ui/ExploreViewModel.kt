package com.joshiminh.wallbase.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.joshiminh.wallbase.data.source.Source
import com.joshiminh.wallbase.data.wallpapers.WallpaperItem
import com.joshiminh.wallbase.data.wallpapers.WallpaperRepository
import com.joshiminh.wallbase.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExploreViewModel(
    private val repository: WallpaperRepository
) : ViewModel() {

    private val cache = mutableMapOf<String, List<WallpaperItem>>()
    private var currentSourceKey: String? = null

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    fun loadSource(source: Source) {
        val key = source.key
        currentSourceKey = key
        cache[key]?.let { cached ->
            _uiState.value = ExploreUiState(wallpapers = cached)
            return
        }

        _uiState.value = ExploreUiState(isLoading = true)
        viewModelScope.launch {
            val requestKey = key
            val result = runCatching { repository.fetchWallpapersFor(source) }
            result.getOrNull()?.let { cache[requestKey] = it }

            if (currentSourceKey != requestKey) return@launch

            result.fold(
                onSuccess = { wallpapers ->
                    _uiState.value = ExploreUiState(
                        isLoading = false,
                        wallpapers = wallpapers,
                        errorMessage = null
                    )
                },
                onFailure = { throwable ->
                    _uiState.value = ExploreUiState(
                        isLoading = false,
                        wallpapers = emptyList(),
                        errorMessage = throwable.localizedMessage?.takeIf { it.isNotBlank() }
                            ?: "Unable to load wallpapers."
                    )
                }
            )
        }
    }

    fun refresh(source: Source) {
        cache.remove(source.key)
        loadSource(source)
    }

    data class ExploreUiState(
        val isLoading: Boolean = false,
        val wallpapers: List<WallpaperItem> = emptyList(),
        val errorMessage: String? = null
    )

    companion object {
        val Factory = viewModelFactory {
            initializer {
                ExploreViewModel(ServiceLocator.wallpaperRepository)
            }
        }
    }
}
