package com.joshiminh.wallbase.ui

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExploreViewModel(
    private val repository: WallpaperRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val cache = mutableMapOf<String, List<WallpaperItem>>()
    private var currentSourceKey: String? = null

    private val _uiState = MutableStateFlow(
        ExploreUiState(activeSourceKey = savedStateHandle[ACTIVE_SOURCE_KEY])
    )
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    fun selectSource(source: Source) {
        loadSource(source = source, forceRefresh = false)
    }

    fun refresh(source: Source) {
        loadSource(source = source, forceRefresh = true)
    }

    data class ExploreUiState(
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val wallpapers: List<WallpaperItem> = emptyList(),
        val errorMessage: String? = null,
        val activeSourceKey: String? = null
    )

    companion object {
        private const val ACTIVE_SOURCE_KEY = "activeSourceKey"

        val Factory = viewModelFactory {
            initializer {
                ExploreViewModel(
                    repository = ServiceLocator.wallpaperRepository,
                    savedStateHandle = this.createSavedStateHandle()
                )
            }
        }
    }

    private fun loadSource(source: Source, forceRefresh: Boolean) {
        val key = source.key
        currentSourceKey = key
        savedStateHandle[ACTIVE_SOURCE_KEY] = key

        val cached = cache[key]

        _uiState.update { current ->
            if (!forceRefresh && cached != null) {
                current.copy(
                    activeSourceKey = key,
                    wallpapers = cached,
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = null
                )
            } else {
                val shouldShowLoading = cached == null
                current.copy(
                    activeSourceKey = key,
                    wallpapers = cached ?: current.takeIf { it.activeSourceKey == key }?.wallpapers
                        ?: emptyList(),
                    isLoading = shouldShowLoading,
                    isRefreshing = !shouldShowLoading,
                    errorMessage = null
                )
            }
        }

        if (!forceRefresh && cached != null) return

        viewModelScope.launch {
            val result = runCatching { repository.fetchWallpapersFor(source) }
            result.getOrNull()?.let { fetched -> cache[key] = fetched }

            if (currentSourceKey != key) return@launch

            result.fold(
                onSuccess = { wallpapers ->
                    _uiState.update {
                        it.copy(
                            activeSourceKey = key,
                            wallpapers = wallpapers,
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        val fallback = cache[key]
                            ?: it.takeIf { state -> state.activeSourceKey == key }?.wallpapers
                            ?: emptyList()
                        it.copy(
                            activeSourceKey = key,
                            wallpapers = fallback,
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = throwable.localizedMessage?.takeIf(String::isNotBlank)
                                ?: "Unable to load wallpapers."
                        )
                    }
                }
            )
        }
    }
}
