package com.joshiminh.wallbase.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import com.joshiminh.wallbase.data.entity.AlbumItem
import com.joshiminh.wallbase.data.entity.Source
import com.joshiminh.wallbase.data.entity.WallpaperItem
import com.joshiminh.wallbase.data.repository.LibraryRepository
import com.joshiminh.wallbase.data.repository.SettingsRepository
import com.joshiminh.wallbase.data.repository.SourceRepository
import com.joshiminh.wallbase.data.repository.WallpaperLayout
import com.joshiminh.wallbase.data.repository.WallpaperRepository
import com.joshiminh.wallbase.util.AlbumSortOption
import com.joshiminh.wallbase.util.WallpaperSortOption
import com.joshiminh.wallbase.util.matchesHorizontalPreference
import com.joshiminh.wallbase.util.network.ServiceLocator
import com.joshiminh.wallbase.util.sortedWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SourceBrowseViewModel(
    private val sourceKey: String,
    private val sourceRepository: SourceRepository,
    private val wallpaperRepository: WallpaperRepository,
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SourceBrowseUiState())
    val uiState: StateFlow<SourceBrowseUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow<String?>(null)
    private var autoDownloadEnabled: Boolean = false
    private var storageLimitBytes: Long = 0L

    @OptIn(ExperimentalCoroutinesApi::class)
    val wallpaperPagingFlow: Flow<PagingData<WallpaperItem>> = combine(
        sourceRepository.observeSource(sourceKey),
        _searchQuery,
        settingsRepository.preferences.map { it.showHorizontalWallpapers }
    ) { source, query, showHorizontal ->
        Triple(source, query, showHorizontal)
    }.flatMapLatest { (source, query, showHorizontal) ->
        if (source == null) {
            flowOf(PagingData.empty())
        } else {
            wallpaperRepository.getWallpaperPagingData(source, query)
                .map { pagingData ->
                    pagingData.filter { item ->
                        item.matchesHorizontalPreference(showHorizontal)
                    }
                }
        }
    }.cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            sourceRepository.observeSource(sourceKey).collectLatest { source ->
                _uiState.update { it.copy(source = source) }
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
                        state.storageLimitBytes != preferences.storageLimitBytes ||
                        state.showHorizontalWallpapers != preferences.showHorizontalWallpapers
                    if (!needsUpdate) {
                        state
                    } else {
                        state.copy(
                            wallpaperGridColumns = columns,
                            wallpaperLayout = layout,
                            autoDownloadEnabled = preferences.autoDownload,
                            storageLimitBytes = preferences.storageLimitBytes,
                            showHorizontalWallpapers = preferences.showHorizontalWallpapers
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
        val trimmed = _uiState.value.query.trim()
        _searchQuery.value = trimmed.takeIf { it.isNotEmpty() }
    }

    fun clearQuery() {
        if (_uiState.value.query.isBlank() && _searchQuery.value == null) return
        _uiState.update { it.copy(query = "") }
        _searchQuery.value = null
    }

    fun beginSelection(wallpaper: WallpaperItem) {
        _uiState.update { state ->
            val updated = state.selectedWallpapers + (wallpaper.id to wallpaper)
            state.copy(
                selectedWallpapers = updated,
                selectedIds = updated.keys,
                isSelectionMode = updated.isNotEmpty()
            )
        }
    }

    fun toggleSelection(wallpaper: WallpaperItem) {
        _uiState.update { state ->
            val updated = if (wallpaper.id in state.selectedWallpapers) {
                state.selectedWallpapers - wallpaper.id
            } else {
                state.selectedWallpapers + (wallpaper.id to wallpaper)
            }
            state.copy(
                selectedWallpapers = updated,
                selectedIds = updated.keys,
                isSelectionMode = updated.isNotEmpty()
            )
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                selectedWallpapers = emptyMap(),
                selectedIds = emptySet(),
                isSelectionMode = false
            )
        }
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
                val updatedMap = if (clearSelection) emptyMap() else state.selectedWallpapers
                state.copy(
                    isActionInProgress = false,
                    message = message,
                    selectedWallpapers = updatedMap,
                    selectedIds = updatedMap.keys,
                    isSelectionMode = updatedMap.isNotEmpty()
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
                val updatedMap = if (clearSelection) emptyMap() else state.selectedWallpapers
                state.copy(
                    isActionInProgress = false,
                    message = message,
                    selectedWallpapers = updatedMap,
                    selectedIds = updatedMap.keys,
                    isSelectionMode = updatedMap.isNotEmpty()
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
                state.copy(wallpaperSortOption = option)
            }
        }
    }

    fun updateGridColumns(columns: Int) {
        val clamped = columns.coerceIn(1, 3)
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
        return _uiState.value.selectedWallpapers.values.toList()
    }

    private fun setMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    data class SourceBrowseUiState(
        val source: Source? = null,
        val query: String = "",
        val savedWallpaperKeys: Set<String> = emptySet(),
        val savedRemoteIdsByProvider: Map<String, Set<String>> = emptyMap(),
        val savedImageUrls: Set<String> = emptySet(),
        val isSelectionMode: Boolean = false,
        val selectedIds: Set<String> = emptySet(),
        val selectedWallpapers: Map<String, WallpaperItem> = emptyMap(),
        val isActionInProgress: Boolean = false,
        val message: String? = null,
        val albums: List<AlbumItem> = emptyList(),
        val wallpaperSortOption: WallpaperSortOption = WallpaperSortOption.RECENTLY_ADDED,
        val wallpaperGridColumns: Int = 2,
        val wallpaperLayout: WallpaperLayout = WallpaperLayout.GRID,
        val autoDownloadEnabled: Boolean = false,
        val storageLimitBytes: Long = 0L,
        val showHorizontalWallpapers: Boolean = true
    )

    companion object {
        private const val UNKNOWN_PROVIDER_KEY = ""

        fun provideFactory(sourceKey: String) = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                ServiceLocator.ensureInitialized(application)
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
