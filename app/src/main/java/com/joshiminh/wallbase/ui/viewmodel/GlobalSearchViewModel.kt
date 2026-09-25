package com.joshiminh.wallbase.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joshiminh.wallbase.data.entity.Source
import com.joshiminh.wallbase.data.entity.WallpaperItem
import com.joshiminh.wallbase.data.repository.SettingsRepository
import com.joshiminh.wallbase.data.repository.SourceRepository
import com.joshiminh.wallbase.data.repository.WallpaperLayout
import com.joshiminh.wallbase.data.repository.WallpaperRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
import javax.inject.Inject

@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
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
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _isRefreshing = MutableStateFlow(false)

    // Map of tab key (null for "All", or source.key) -> List<WallpaperItem>
    private val _tabWallpapers = MutableStateFlow<Map<String?, List<WallpaperItem>>>(emptyMap())
    private val sourceCursors = mutableMapOf<String, String?>()

    private var searchDebounceJob: Job? = null
    private var loadFeedJob: Job? = null

    val uiState: StateFlow<GlobalSearchUiState> = combine(
        combine(_sources, _selectedSourceKey, _searchQuery, _tabWallpapers) { sources, selectedKey, query, tabWallpapers ->
            val wallpapers = tabWallpapers[selectedKey] ?: emptyList()
            val trimmed = query.trim()
            val isExplore = trimmed.isEmpty()
            Triple(sources, selectedKey, Pair(query, isExplore)) to wallpapers
        },
        combine(_isLoading, _isLoadingMore, _errorMessage, _isRefreshing) { loading, loadingMore, error, refreshing ->
            Quad(loading, loadingMore, error, refreshing)
        },
        settingsRepository.preferences
    ) { (searchInfo, wallpapers), loadingData, prefs ->
        val (sources, selectedKey, queryInfo) = searchInfo
        val (query, isExplore) = queryInfo

        GlobalSearchUiState(
            sources = sources,
            selectedSourceKey = selectedKey,
            searchQuery = query,
            isSearching = !isExplore,
            isExploreMode = isExplore,
            isLoading = loadingData.first,
            isLoadingMore = loadingData.second,
            wallpapers = wallpapers,
            errorMessage = loadingData.third,
            wallpaperGridColumns = prefs.wallpaperGridColumns,
            wallpaperLayout = prefs.wallpaperLayout,
            showDownloadBadge = prefs.showDownloadBadge,
            isRefreshing = loadingData.fourth
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

                if (_selectedSourceKey.value != null && activeSources.none { it.key == _selectedSourceKey.value }) {
                    _selectedSourceKey.value = null
                }

                val currentKey = _selectedSourceKey.value
                val currentTabWallpapers = _tabWallpapers.value[currentKey]
                if (currentTabWallpapers.isNullOrEmpty() && activeSources.isNotEmpty() && !_isLoading.value) {
                    loadFeedForTab(currentKey, activeSources)
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
            val currentKey = _selectedSourceKey.value
            val activeSources = _sources.value
            if (trimmed.isEmpty()) {
                loadFeedForTab(currentKey, activeSources)
            } else {
                performSearchForTab(currentKey, trimmed, activeSources)
            }
        }
    }

    fun selectSourceFilter(sourceKey: String?) {
        if (_selectedSourceKey.value == sourceKey) return
        _selectedSourceKey.value = sourceKey

        val activeSources = _sources.value
        val existingWallpapers = _tabWallpapers.value[sourceKey]

        if (existingWallpapers.isNullOrEmpty() && activeSources.isNotEmpty()) {
            val query = _searchQuery.value.trim()
            if (query.isEmpty()) {
                loadFeedForTab(sourceKey, activeSources)
            } else {
                performSearchForTab(sourceKey, query, activeSources)
            }
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val currentKey = _selectedSourceKey.value
                val activeSources = _sources.value
                val query = _searchQuery.value.trim()
                if (query.isEmpty()) {
                    loadFeedForTab(currentKey, activeSources, isRefresh = true)
                } else {
                    performSearchForTab(currentKey, query, activeSources, isRefresh = true)
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun loadFeedForTab(tabKey: String?, sources: List<Source>, isRefresh: Boolean = false) {
        if (sources.isEmpty()) {
            _tabWallpapers.update { it + (tabKey to emptyList()) }
            return
        }

        loadFeedJob?.cancel()
        loadFeedJob = viewModelScope.launch {
            if (!isRefresh) _isLoading.value = true
            try {
                if (tabKey == null) {
                    // "All" tab: fetch first page of all active sources
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
                        val shuffled = aggregated.shuffled()
                        _tabWallpapers.update { it + (null to shuffled) }
                    }
                } else {
                    // Specific source tab: ONLY fetch this source!
                    val targetSource = sources.find { it.key == tabKey }
                    if (targetSource != null) {
                        val pageResult = runCatching {
                            wallpaperRepository.fetchWallpapersFor(source = targetSource, cursor = null)
                        }.getOrNull()

                        if (pageResult != null) {
                            sourceCursors[tabKey] = pageResult.nextCursor
                            _tabWallpapers.update { it + (tabKey to pageResult.wallpapers) }
                        } else {
                            _tabWallpapers.update { it + (tabKey to emptyList()) }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Unable to load feed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun performSearchForTab(tabKey: String?, query: String, sources: List<Source>, isRefresh: Boolean = false) {
        if (sources.isEmpty()) return

        loadFeedJob?.cancel()
        loadFeedJob = viewModelScope.launch {
            if (!isRefresh) _isLoading.value = true
            try {
                if (tabKey == null) {
                    // Search across all sources
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
                        _tabWallpapers.update { it + (null to aggregated) }
                    }
                } else {
                    // Search ONLY the selected source!
                    val targetSource = sources.find { it.key == tabKey }
                    if (targetSource != null) {
                        val pageResult = runCatching {
                            wallpaperRepository.fetchWallpapersFor(
                                source = targetSource,
                                query = query,
                                cursor = null
                            )
                        }.getOrNull()

                        if (pageResult != null) {
                            sourceCursors[tabKey] = pageResult.nextCursor
                            _tabWallpapers.update { it + (tabKey to pageResult.wallpapers) }
                        } else {
                            _tabWallpapers.update { it + (tabKey to emptyList()) }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Search failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value || _isLoading.value) return
        val currentKey = _selectedSourceKey.value
        val currentWallpapers = _tabWallpapers.value[currentKey] ?: emptyList()
        if (currentWallpapers.isEmpty()) return

        val activeSources = _sources.value
        val currentQuery = _searchQuery.value.trim().takeIf { it.isNotBlank() }

        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                if (currentKey == null) {
                    // Load more for All tab
                    coroutineScope {
                        val deferreds = activeSources.mapNotNull { source ->
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
                            val existingIds = currentWallpapers.map { it.id }.toSet()
                            val distinctNew = newItems.filter { it.id !in existingIds }
                            _tabWallpapers.update { it + (null to (currentWallpapers + distinctNew)) }
                        }
                    }
                } else {
                    // Load more for specific source tab ONLY
                    val targetSource = activeSources.find { it.key == currentKey }
                    if (targetSource != null) {
                        val cursor = sourceCursors[currentKey]
                        if (cursor != null || !sourceCursors.containsKey(currentKey)) {
                            val pageResult = runCatching {
                                wallpaperRepository.fetchWallpapersFor(
                                    source = targetSource,
                                    query = currentQuery,
                                    cursor = cursor
                                )
                            }.getOrNull()

                            if (pageResult != null) {
                                sourceCursors[currentKey] = pageResult.nextCursor
                                val existingIds = currentWallpapers.map { it.id }.toSet()
                                val distinctNew = pageResult.wallpapers.filter { it.id !in existingIds }
                                _tabWallpapers.update { it + (currentKey to (currentWallpapers + distinctNew)) }
                            }
                        }
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
}

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
