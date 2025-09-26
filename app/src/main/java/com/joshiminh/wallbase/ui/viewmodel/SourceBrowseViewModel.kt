package com.joshiminh.wallbase.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.joshiminh.wallbase.data.entity.album.AlbumItem
import com.joshiminh.wallbase.data.entity.source.Source
import com.joshiminh.wallbase.data.repository.LibraryRepository
import com.joshiminh.wallbase.data.repository.SettingsRepository
import com.joshiminh.wallbase.data.repository.WallpaperLayout
import com.joshiminh.wallbase.data.repository.SourceRepository
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem
import com.joshiminh.wallbase.data.repository.WallpaperRepository
import com.joshiminh.wallbase.ui.sort.AlbumSortOption
import com.joshiminh.wallbase.ui.sort.WallpaperSortOption
import com.joshiminh.wallbase.ui.sort.sortedWith
import com.joshiminh.wallbase.util.network.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.LinkedHashMap

class SourceBrowseViewModel(
    private val sourceKey: String,
    private val sourceRepository: SourceRepository,
    private val wallpaperRepository: WallpaperRepository,
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SourceBrowseUiState())
    val uiState: StateFlow<SourceBrowseUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var currentQuery: String? = null
    private var activeSourceKey: String? = null
    private var lastSourceConfig: String? = null
    private var nextPageCursor: String? = null
    private var autoDownloadEnabled: Boolean = false
    private var storageLimitBytes: Long = 0L

    init {
        viewModelScope.launch {
            sourceRepository.observeSource(sourceKey).collectLatest { source ->
                _uiState.update { it.copy(source = source) }
                if (source == null) {
                    loadJob?.cancel()
                    activeSourceKey = null
                    lastSourceConfig = null
                    nextPageCursor = null
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

        viewModelScope.launch {
            libraryRepository.observeSavedWallpapers().collectLatest { saved ->
                val keys = saved.mapNotNull { it.libraryKey() }.toSet()
                val remoteIdsByProvider = mutableMapOf<String, MutableSet<String>>()
                val imageUrls = mutableSetOf<String>()
                saved.forEach { wallpaper ->
                    val remoteId = wallpaper.remoteIdentifierWithinSource()
                    if (remoteId != null) {
                        val provider = wallpaper.providerKey() ?: UNKNOWN_PROVIDER_KEY
                        remoteIdsByProvider.getOrPut(provider) { mutableSetOf() }.add(remoteId)
                    }
                    imageUrls += wallpaper.imageUrl
                }
                val remoteSnapshot = remoteIdsByProvider.mapValues { entry -> entry.value.toSet() }
                val imagesSnapshot = imageUrls.toSet()
                _uiState.update { state ->
                    if (state.savedWallpaperKeys == keys &&
                        state.savedRemoteIdsByProvider == remoteSnapshot &&
                        state.savedImageUrls == imagesSnapshot
                    ) {
                        state
                    } else {
                        state.copy(
                            savedWallpaperKeys = keys,
                            savedRemoteIdsByProvider = remoteSnapshot,
                            savedImageUrls = imagesSnapshot
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            libraryRepository.observeAlbums().collectLatest { albums ->
                _uiState.update { state ->
                    val sorted = albums.sortedWith(AlbumSortOption.TITLE_ASCENDING)
                    if (state.albums == sorted) state else state.copy(albums = sorted)
                }
            }
        }

        viewModelScope.launch {
            settingsRepository.preferences.collectLatest { preferences ->
                _uiState.update { state ->
                    val columns = preferences.wallpaperGridColumns
                    val layout = preferences.wallpaperLayout
                    autoDownloadEnabled = preferences.autoDownload
                    storageLimitBytes = preferences.storageLimitBytes
                    val needsUpdate = state.wallpaperGridColumns != columns ||
                        state.wallpaperLayout != layout ||
                        state.autoDownloadEnabled != preferences.autoDownload ||
                        state.storageLimitBytes != preferences.storageLimitBytes
                    if (!needsUpdate) {
                        state
                    } else {
                        state.copy(
                            wallpaperGridColumns = columns,
                            wallpaperLayout = layout,
                            autoDownloadEnabled = preferences.autoDownload,
                            storageLimitBytes = preferences.storageLimitBytes
                        )
                    }
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
        nextPageCursor = null
        loadWallpapers(source, query = currentQuery, showLoading = false)
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.canLoadMore || state.isLoading || state.isRefreshing || state.isAppending) return
        val source = state.source ?: return
        loadWallpapers(
            source = source,
            query = currentQuery,
            showLoading = false,
            append = true,
            cursor = nextPageCursor
        )
    }

    private fun loadWallpapers(
        source: Source,
        query: String?,
        showLoading: Boolean,
        append: Boolean = false,
        cursor: String? = null
    ) {
        loadJob?.cancel()
        activeSourceKey = source.key
        lastSourceConfig = source.config
        if (!append) {
            nextPageCursor = null
        }
        loadJob = viewModelScope.launch {
            val hasExisting = _uiState.value.wallpapers.isNotEmpty()
            _uiState.update {
                it.copy(
                    isLoading = if (append) it.isLoading else showLoading || !hasExisting,
                    isRefreshing = if (append) it.isRefreshing else !showLoading && hasExisting,
                    isAppending = append,
                    errorMessage = if (append) it.errorMessage else null,
                    canLoadMore = if (append) it.canLoadMore else false
                )
            }
            val result = runCatching {
                wallpaperRepository.fetchWallpapersFor(source, query, cursor)
            }
            result.fold(
                onSuccess = { page ->
                    _uiState.update { state ->
                        nextPageCursor = page.nextCursor
                        val combined = if (append) {
                            val merged = LinkedHashMap<String, WallpaperItem>()
                            state.wallpapers.forEach { existing -> merged[existing.id] = existing }
                            page.wallpapers.forEach { item -> merged[item.id] = item }
                            merged.values.toList()
                        } else {
                            page.wallpapers
                        }
                        val availableIds = combined.mapTo(hashSetOf()) { it.id }
                        val retainedSelection = state.selectedIds.filterTo(hashSetOf()) { it in availableIds }
                        val sorted = combined.sortedWith(state.wallpaperSortOption)
                        val allowMore = page.nextCursor != null || page.wallpapers.isNotEmpty()
                        state.copy(
                            wallpapers = sorted,
                            isLoading = false,
                            isRefreshing = false,
                            isAppending = false,
                            errorMessage = null,
                            selectedIds = retainedSelection,
                            isSelectionMode = retainedSelection.isNotEmpty(),
                            canLoadMore = allowMore
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { state ->
                        val message = error.localizedMessage?.takeIf { it.isNotBlank() }
                            ?: "Unable to load wallpapers."
                        state.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isAppending = false,
                            errorMessage = message,
                            wallpapers = state.wallpapers,
                            canLoadMore = state.canLoadMore
                        )
                    }
                }
            )
        }
    }

    fun beginSelection(wallpaper: WallpaperItem) {
        modifySelection { it.add(wallpaper.id) }
    }

    fun toggleSelection(wallpaper: WallpaperItem) {
        modifySelection {
            if (!it.add(wallpaper.id)) {
                it.remove(wallpaper.id)
            }
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet(), isSelectionMode = false) }
    }

    fun addSelectedToLibrary() {
        val current = selectedWallpapers()
        if (current.isEmpty() || _uiState.value.isActionInProgress) return

        viewModelScope.launch {
            _uiState.update { it.copy(isActionInProgress = true) }
            val result = runCatching { libraryRepository.addWallpapersToLibrary(current) }
            val addedWallpapers = result.getOrNull()?.addedWallpapers ?: emptyList()
            _uiState.update { state ->
                val (message, clearSelection) = result.fold(
                    onSuccess = { outcome ->
                        val message = when {
                            outcome.added > 0 && outcome.skipped > 0 ->
                                "Saved ${outcome.added} wallpapers (skipped ${outcome.skipped} already saved)"

                            outcome.added > 0 -> "Saved ${outcome.added} wallpapers to your library"

                            else -> "All selected wallpapers are already in your library"
                        }
                        message to true
                    },
                    onFailure = { error ->
                        val message = error.localizedMessage?.takeIf { it.isNotBlank() }
                            ?: "Unable to save wallpapers"
                        message to false
                    }
                )
                val updatedSelection = if (clearSelection) emptySet() else state.selectedIds
                state.copy(
                    isActionInProgress = false,
                    message = message,
                    selectedIds = updatedSelection,
                    isSelectionMode = updatedSelection.isNotEmpty()
                )
            }
            if (autoDownloadEnabled && addedWallpapers.isNotEmpty()) {
                viewModelScope.launch {
                    val downloadResult = runCatching {
                        libraryRepository.downloadWallpapers(addedWallpapers, storageLimitBytes)
                    }
                    downloadResult.fold(
                        onSuccess = { summary ->
                            val extra = when {
                                summary.downloaded > 0 && summary.blocked > 0 ->
                                    "Auto-downloaded ${summary.downloaded} wallpapers (blocked ${summary.blocked} by storage limit)"
                                summary.downloaded > 0 ->
                                    "Auto-downloaded ${summary.downloaded} wallpapers"
                                summary.blocked > 0 ->
                                    "Auto-download blocked by storage limit"
                                summary.failed > 0 ->
                                    "Auto-download failed for ${summary.failed} wallpapers"
                                else -> null
                            }
                            extra?.let(::setMessage)
                        },
                        onFailure = { error ->
                            val detail = error.localizedMessage?.takeIf { it.isNotBlank() }
                            setMessage(detail?.let { "Auto-download failed: $it" } ?: "Auto-download failed")
                        }
                    )
                }
            }
        }
    }

    fun addSelectedToAlbum(albumId: Long) {
        val albums = _uiState.value.albums
        if (albums.none { it.id == albumId }) {
            setMessage("Album not available")
            return
        }
        val current = selectedWallpapers()
        if (current.isEmpty() || _uiState.value.isActionInProgress) return

        viewModelScope.launch {
            _uiState.update { it.copy(isActionInProgress = true) }
            val result = runCatching { libraryRepository.addWallpapersToAlbum(albumId, current) }
            _uiState.update { state ->
                val (message, clearSelection) = result.fold(
                    onSuccess = { outcome ->
                        val message = when {
                            outcome.addedToAlbum > 0 && (outcome.alreadyPresent > 0 || outcome.skipped > 0) ->
                                "Added ${outcome.addedToAlbum} wallpapers (skipped ${outcome.alreadyPresent + outcome.skipped} others)"

                            outcome.addedToAlbum > 0 -> "Added ${outcome.addedToAlbum} wallpapers to the album"

                            outcome.alreadyPresent > 0 && outcome.skipped == 0 ->
                                "All selected wallpapers are already in this album"

                            outcome.skipped > 0 -> "Unable to add ${outcome.skipped} wallpapers to the album"

                            else -> "All selected wallpapers are already in this album"
                        }
                        message to true
                    },
                    onFailure = { error ->
                        val message = error.localizedMessage?.takeIf { it.isNotBlank() }
                            ?: "Unable to update album"
                        message to false
                    }
                )
                val updatedSelection = if (clearSelection) emptySet() else state.selectedIds
                state.copy(
                    isActionInProgress = false,
                    message = message,
                    selectedIds = updatedSelection,
                    isSelectionMode = updatedSelection.isNotEmpty()
                )
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { state ->
            if (state.message == null) state else state.copy(message = null)
        }
    }

    fun updateSort(option: WallpaperSortOption) {
        _uiState.update { state ->
            if (state.wallpaperSortOption == option) {
                state
            } else {
                state.copy(
                    wallpaperSortOption = option,
                    wallpapers = state.wallpapers.sortedWith(option)
                )
            }
        }
    }

    fun updateGridColumns(columns: Int) {
        val clamped = columns.coerceIn(1, 4)
        if (_uiState.value.wallpaperGridColumns == clamped) return
        _uiState.update { it.copy(wallpaperGridColumns = clamped) }
        viewModelScope.launch {
            settingsRepository.setWallpaperGridColumns(clamped)
        }
    }

    fun updateWallpaperLayout(layout: WallpaperLayout) {
        if (_uiState.value.wallpaperLayout == layout) return
        _uiState.update { it.copy(wallpaperLayout = layout) }
        viewModelScope.launch {
            settingsRepository.setWallpaperLayout(layout)
        }
    }

    private fun selectedWallpapers(): List<WallpaperItem> {
        val current = _uiState.value
        if (current.selectedIds.isEmpty()) return emptyList()
        val byId = current.wallpapers.associateBy { it.id }
        return current.selectedIds.mapNotNull(byId::get)
    }

    private fun modifySelection(block: (MutableSet<String>) -> Unit) {
        _uiState.update { state ->
            val working = state.selectedIds.toMutableSet()
            block(working)
            val updated = working.toSet()
            val selectionChanged = updated != state.selectedIds
            val modeChanged = state.isSelectionMode != updated.isNotEmpty()
            if (!selectionChanged && !modeChanged) {
                state
            } else {
                state.copy(
                    selectedIds = updated,
                    isSelectionMode = updated.isNotEmpty()
                )
            }
        }
    }

    private fun setMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    data class SourceBrowseUiState(
        val source: Source? = null,
        val query: String = "",
        val wallpapers: List<WallpaperItem> = emptyList(),
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val errorMessage: String? = null,
        val savedWallpaperKeys: Set<String> = emptySet(),
        val savedRemoteIdsByProvider: Map<String, Set<String>> = emptyMap(),
        val savedImageUrls: Set<String> = emptySet(),
        val isSelectionMode: Boolean = false,
        val selectedIds: Set<String> = emptySet(),
        val isActionInProgress: Boolean = false,
        val message: String? = null,
        val albums: List<AlbumItem> = emptyList(),
        val wallpaperSortOption: WallpaperSortOption = WallpaperSortOption.RECENTLY_ADDED,
        val isAppending: Boolean = false,
        val canLoadMore: Boolean = false,
        val wallpaperGridColumns: Int = 2,
        val wallpaperLayout: WallpaperLayout = WallpaperLayout.GRID,
        val autoDownloadEnabled: Boolean = false,
        val storageLimitBytes: Long = 0L
    )

    companion object {
        private const val UNKNOWN_PROVIDER_KEY = ""

        fun provideFactory(sourceKey: String) = viewModelFactory {
            initializer {
                SourceBrowseViewModel(
                    sourceKey = sourceKey,
                    sourceRepository = ServiceLocator.sourceRepository,
                    wallpaperRepository = ServiceLocator.wallpaperRepository,
                    libraryRepository = ServiceLocator.libraryRepository,
                    settingsRepository = ServiceLocator.settingsRepository
                )
            }
        }
    }
}
