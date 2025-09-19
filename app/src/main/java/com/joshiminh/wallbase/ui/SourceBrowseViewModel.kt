package com.joshiminh.wallbase.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.joshiminh.wallbase.data.library.AlbumItem
import com.joshiminh.wallbase.data.library.LibraryRepository
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
    private val wallpaperRepository: WallpaperRepository,
    private val libraryRepository: LibraryRepository
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

        viewModelScope.launch {
            libraryRepository.observeSavedWallpapers().collectLatest { saved ->
                val keys = saved.mapNotNull { it.libraryKey() }.toSet()
                _uiState.update { state ->
                    if (state.savedWallpaperKeys == keys) state else state.copy(savedWallpaperKeys = keys)
                }
            }
        }

        viewModelScope.launch {
            libraryRepository.observeAlbums().collectLatest { albums ->
                _uiState.update { state ->
                    if (state.albums == albums) state else state.copy(albums = albums)
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
                    _uiState.update { state ->
                        val availableIds = wallpapers.mapTo(hashSetOf()) { it.id }
                        val retainedSelection = state.selectedIds.filterTo(hashSetOf()) { it in availableIds }
                        state.copy(
                            wallpapers = wallpapers,
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = null,
                            selectedIds = retainedSelection,
                            isSelectionMode = retainedSelection.isNotEmpty()
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
        _uiState.update { it.copy(message = null) }
    }

    private fun selectedWallpapers(): List<WallpaperItem> {
        val current = _uiState.value
        if (current.selectedIds.isEmpty()) return emptyList()
        val byId = current.wallpapers.associateBy { it.id }
        return current.selectedIds.mapNotNull(byId::get)
    }

    private fun modifySelection(block: (MutableSet<String>) -> Unit) {
        _uiState.update { state ->
            val updated = state.selectedIds.toMutableSet()
            block(updated)
            state.copy(
                selectedIds = updated,
                isSelectionMode = updated.isNotEmpty()
            )
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
        val isSelectionMode: Boolean = false,
        val selectedIds: Set<String> = emptySet(),
        val isActionInProgress: Boolean = false,
        val message: String? = null,
        val albums: List<AlbumItem> = emptyList()
    )

    companion object {
        fun provideFactory(sourceKey: String) = viewModelFactory {
            initializer {
                SourceBrowseViewModel(
                    sourceKey = sourceKey,
                    sourceRepository = ServiceLocator.sourceRepository,
                    wallpaperRepository = ServiceLocator.wallpaperRepository,
                    libraryRepository = ServiceLocator.libraryRepository
                )
            }
        }
    }
}
