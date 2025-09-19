package com.joshiminh.wallbase.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.joshiminh.wallbase.data.source.Source
import com.joshiminh.wallbase.data.source.SourceRepository
import com.joshiminh.wallbase.data.wallpapers.WallpaperItem
import com.joshiminh.wallbase.data.wallpapers.WallpaperRepository
import com.joshiminh.wallbase.di.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SourceBrowseViewModel(
    private val sourceKey: String,
    private val sourceRepository: SourceRepository,
    private val wallpaperRepository: WallpaperRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SourceBrowseUiState())
    val uiState: StateFlow<SourceBrowseUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var currentQuery: String? = null
    private var activeSourceKey: String? = null
    private var lastSourceConfig: String? = null

    init {
        viewModelScope.launch {
            sourceRepository.observeSource(sourceKey).collectLatest { source ->
                _uiState.update { it.copy(source = source) }
                if (source == null) {
                    loadJob?.cancel()
                    activeSourceKey = null
                    lastSourceConfig = null
                    _uiState.update {
                        it.copy(
                            wallpapers = emptyList(),
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = "Source unavailable"
                        )
                    }
                } else if (
                    source.key != activeSourceKey ||
                    source.config != lastSourceConfig ||
                    _uiState.value.wallpapers.isEmpty()
                ) {
                    loadWallpapers(source, query = currentQuery, showLoading = true)
                }
            }
        }
    }

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun search() {
        val source = _uiState.value.source ?: return
        val trimmed = _uiState.value.query.trim()
        currentQuery = trimmed.takeIf { it.isNotEmpty() }
        loadWallpapers(source, query = currentQuery, showLoading = true)
    }

    fun clearQuery() {
        if (_uiState.value.query.isBlank() && currentQuery == null) return
        _uiState.update { it.copy(query = "") }
        val source = _uiState.value.source ?: return
        currentQuery = null
        loadWallpapers(source, query = null, showLoading = true)
    }

    fun refresh() {
        val source = _uiState.value.source ?: return
        loadWallpapers(source, query = currentQuery, showLoading = false)
    }

    private fun loadWallpapers(source: Source, query: String?, showLoading: Boolean) {
        loadJob?.cancel()
        activeSourceKey = source.key
        lastSourceConfig = source.config
        loadJob = viewModelScope.launch {
            val hasExisting = _uiState.value.wallpapers.isNotEmpty()
            _uiState.update {
                it.copy(
                    isLoading = showLoading || !hasExisting,
                    isRefreshing = !showLoading && hasExisting,
                    errorMessage = null
                )
            }
            val result = runCatching { wallpaperRepository.fetchWallpapersFor(source, query) }
            result.fold(
                onSuccess = { wallpapers ->
                    _uiState.update {
                        it.copy(
                            wallpapers = wallpapers,
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = error.localizedMessage?.takeIf { it.isNotBlank() }
                                ?: "Unable to load wallpapers.",
                            wallpapers = state.wallpapers
                        )
                    }
                }
            )
        }
    }

    data class SourceBrowseUiState(
        val source: Source? = null,
        val query: String = "",
        val wallpapers: List<WallpaperItem> = emptyList(),
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val errorMessage: String? = null
    )

    companion object {
        fun provideFactory(sourceKey: String) = viewModelFactory {
            initializer {
                SourceBrowseViewModel(
                    sourceKey = sourceKey,
                    sourceRepository = ServiceLocator.sourceRepository,
                    wallpaperRepository = ServiceLocator.wallpaperRepository
                )
            }
        }
    }
}
