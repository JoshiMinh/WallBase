package com.joshiminh.wallbase.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.joshiminh.wallbase.data.entity.Source
import com.joshiminh.wallbase.data.entity.WallpaperItem
import com.joshiminh.wallbase.data.repository.SettingsRepository
import com.joshiminh.wallbase.data.repository.SourceRepository
import com.joshiminh.wallbase.data.repository.WallpaperLayout
import com.joshiminh.wallbase.data.repository.WallpaperRepository
import com.joshiminh.wallbase.util.network.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GlobalSearchViewModel(
    private val sourceRepository: SourceRepository,
    private val wallpaperRepository: WallpaperRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    data class GlobalSearchUiState(
        val sources: List<Source> = emptyList(),
        val selectedSourceKey: String? = null,
        val searchQuery: String = "",
        val isSearching: Boolean = false,
        val isExploreMode: Boolean = true,
        val isLoading: Boolean = false,
        val isLoadingMore: Boolean = false,
        val wallpapers: List<WallpaperItem> = emptyList(),
        val errorMessage: String? = null,
        val wallpaperGridColumns: Int = 2,
        val wallpaperLayout: WallpaperLayout = WallpaperLayout.GRID,
        val showDownloadBadge: Boolean = true,
        val isRefreshing: Boolean = false
    )

    private val _sources = MutableStateFlow<List<Source>>(emptyList())
    private val _selectedSourceKey = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(false)
    private val _isLoadingMore = MutableStateFlow(false)
    private val _wallpapers = MutableStateFlow<List<WallpaperItem>>(emptyList())
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _isRefreshing = MutableStateFlow(false)

    private var searchDebounceJob: Job? = null
    private val sourceCursors = mutableMapOf<String, String?>()

    private data class SearchFlowData(
        val sources: List<Source>,
        val selectedKey: String?,
        val query: String,
        val wallpapers: List<WallpaperItem>
    )

    private data class LoadingFlowData(
        val loading: Boolean,
        val loadingMore: Boolean,
        val error: String?,
        val refreshing: Boolean
    )

    private val searchFlowData = combine(
        _sources,
        _selectedSourceKey,
        _searchQuery,
        _wallpapers
    ) { sources, selectedKey, query, wallpapers ->
        SearchFlowData(sources, selectedKey, query, wallpapers)
    }

    private val loadingFlowData = combine(
        _isLoading,
        _isLoadingMore,
        _errorMessage,
        _isRefreshing
    ) { loading, loadingMore, error, refreshing ->
        LoadingFlowData(loading, loadingMore, error, refreshing)
    }

    val uiState: StateFlow<GlobalSearchUiState> = combine(
        searchFlowData,
        loadingFlowData,
        settingsRepository.preferences
    ) { searchData, loadingData, prefs ->
        val trimmed = searchData.query.trim()
        val isExplore = trimmed.isEmpty()
        val filteredWallpapers = if (searchData.selectedKey == null) {
            searchData.wallpapers
        } else {
            searchData.wallpapers.filter { it.sourceKey == searchData.selectedKey }
        }

        GlobalSearchUiState(
            sources = searchData.sources,
            selectedSourceKey = searchData.selectedKey,
            searchQuery = searchData.query,
            isSearching = !isExplore,
            isExploreMode = isExplore,
            isLoading = loadingData.loading,
            isLoadingMore = loadingData.loadingMore,
            wallpapers = filteredWallpapers,
            errorMessage = loadingData.error,
            wallpaperGridColumns = prefs.wallpaperGridColumns,
            wallpaperLayout = prefs.wallpaperLayout,
            showDownloadBadge = prefs.showDownloadBadge,
            isRefreshing = loadingData.refreshing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GlobalSearchUiState()
    )

    init {
        viewModelScope.launch {
            sourceRepository.observeSources().collect { allSources ->
                val activeSources = allSources.filter { it.enabled && !it.isLocal }
                _sources.value = activeSources
                if (_wallpapers.value.isEmpty()) {
                    loadExploreFeed(activeSources)
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(400)
            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                loadExploreFeed(_sources.value)
            } else {
                performSearch(trimmed, _sources.value)
            }
        }
    }

    fun selectSourceFilter(sourceKey: String?) {
        _selectedSourceKey.value = sourceKey
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val query = _searchQuery.value.trim()
                if (query.isEmpty()) {
                    loadExploreFeed(_sources.value, isRefresh = true)
                } else {
                    performSearch(query, _sources.value, isRefresh = true)
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun loadExploreFeed(sources: List<Source>, isRefresh: Boolean = false) {
        if (sources.isEmpty()) {
            _wallpapers.value = emptyList()
            return
        }
        if (!isRefresh) _isLoading.value = true
        sourceCursors.clear()

        try {
            coroutineScope {
                val deferreds = sources.map { source ->
                    async {
                        runCatching {
                            val page = wallpaperRepository.fetchWallpapersFor(source = source, cursor = null)
                            source.key to page
                        }.getOrNull()
                    }
                }
                val results = deferreds.awaitAll().filterNotNull()
                val aggregated = mutableListOf<WallpaperItem>()
                results.forEach { (sourceKey, page) ->
                    sourceCursors[sourceKey] = page.nextCursor
                    aggregated.addAll(page.wallpapers)
                }
                // Shuffle/interleave nicely for explore feed
                _wallpapers.value = aggregated.shuffled()
            }
        } catch (e: Exception) {
            _errorMessage.value = e.localizedMessage ?: "Unable to load explore feed"
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun performSearch(query: String, sources: List<Source>, isRefresh: Boolean = false) {
        if (sources.isEmpty()) return
        if (!isRefresh) _isLoading.value = true
        sourceCursors.clear()

        try {
            coroutineScope {
                val deferreds = sources.map { source ->
                    async {
                        runCatching {
                            val page = wallpaperRepository.fetchWallpapersFor(
                                source = source,
                                query = query,
                                cursor = null
                            )
                            source.key to page
                        }.getOrNull()
                    }
                }
                val results = deferreds.awaitAll().filterNotNull()
                val aggregated = mutableListOf<WallpaperItem>()
                results.forEach { (sourceKey, page) ->
                    sourceCursors[sourceKey] = page.nextCursor
                    aggregated.addAll(page.wallpapers)
                }
                _wallpapers.value = aggregated
            }
        } catch (e: Exception) {
            _errorMessage.value = e.localizedMessage ?: "Search failed"
        } finally {
            _isLoading.value = false
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value || _isLoading.value || _wallpapers.value.isEmpty()) return
        val currentSources = _sources.value
        val currentQuery = _searchQuery.value.trim().takeIf { it.isNotBlank() }

        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                coroutineScope {
                    val deferreds = currentSources.mapNotNull { source ->
                        val cursor = sourceCursors[source.key]
                        if (cursor == null && sourceCursors.containsKey(source.key)) null
                        else {
                            async {
                                runCatching {
                                    val page = wallpaperRepository.fetchWallpapersFor(
                                        source = source,
                                        query = currentQuery,
                                        cursor = cursor
                                    )
                                    source.key to page
                                }.getOrNull()
                            }
                        }
                    }
                    val results = deferreds.awaitAll().filterNotNull()
                    val newItems = mutableListOf<WallpaperItem>()
                    results.forEach { (sourceKey, page) ->
                        sourceCursors[sourceKey] = page.nextCursor
                        newItems.addAll(page.wallpapers)
                    }
                    if (newItems.isNotEmpty()) {
                        val existingIds = _wallpapers.value.map { it.id }.toSet()
                        val distinctNew = newItems.filter { it.id !in existingIds }
                        _wallpapers.value = _wallpapers.value + distinctNew
                    }
                }
            } finally {
                _isLoadingMore.value = false
            }
        }
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

    fun consumeError() {
        _errorMessage.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GlobalSearchViewModel(
                    sourceRepository = ServiceLocator.sourceRepository,
                    wallpaperRepository = ServiceLocator.wallpaperRepository,
                    settingsRepository = ServiceLocator.settingsRepository
                )
            }
        }
    }
}
