package com.joshiminh.wallbase.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.joshiminh.wallbase.data.repository.LibraryRepository
import com.joshiminh.wallbase.sources.reddit.RedditCommunity
import com.joshiminh.wallbase.data.entity.source.Source
import com.joshiminh.wallbase.data.entity.source.SourceKeys
import com.joshiminh.wallbase.data.repository.SourceRepository
import com.joshiminh.wallbase.data.repository.WallpaperRepository
import com.joshiminh.wallbase.util.network.ServiceLocator
import com.joshiminh.wallbase.sources.google_photos.GooglePhotosAlbum
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

class SourcesViewModel(
    application: Application,
    private val sourceRepository: SourceRepository,
    private val libraryRepository: LibraryRepository,
    private val wallpaperRepository: WallpaperRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SourcesUiState())
    val uiState: StateFlow<SourcesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sourceRepository.observeSources().collect { sources ->
                val redditConfigs = sources
                    .filter { it.providerKey == SourceKeys.REDDIT }
                    .mapNotNull { it.config?.lowercase(Locale.ROOT) }
                    .toSet()
                _uiState.update {
                    it.copy(
                        sources = sources,
                        existingRedditConfigs = redditConfigs,
                        detectedType = sourceRepository.detectRemoteSourceType(it.urlInput)
                    )
                }
            }
        }
    }

    fun updateSourceInput(input: String) {
        _uiState.update { state ->
            if (state.urlInput == input) {
                state
            } else {
                val detected = sourceRepository.detectRemoteSourceType(input)
                val shouldClearResults =
                    state.detectedType == SourceRepository.RemoteSourceType.REDDIT &&
                            detected != SourceRepository.RemoteSourceType.REDDIT
                state.copy(
                    urlInput = input,
                    detectedType = detected,
                    redditSearchResults = if (shouldClearResults) emptyList() else state.redditSearchResults,
                    redditSearchError = if (shouldClearResults) null else state.redditSearchError
                )
            }
        }
    }

    fun searchRedditCommunities() {
        val state = uiState.value
        if (state.detectedType != SourceRepository.RemoteSourceType.REDDIT) {
            _uiState.update {
                it.copy(snackbarMessage = "Enter a subreddit name or URL to search.")
            }
            return
        }

        val query = state.urlInput.trim()
        if (query.length < 2) {
            _uiState.update { it.copy(snackbarMessage = "Enter at least two characters to search.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingReddit = true, redditSearchError = null) }
            val result = runCatching { wallpaperRepository.searchRedditCommunities(query) }
            _uiState.update {
                it.copy(
                    isSearchingReddit = false,
                    redditSearchError = result.exceptionOrNull()?.localizedMessage
                        ?: if (result.isFailure) "Unable to search communities." else null,
                    redditSearchResults = result.getOrDefault(emptyList())
                )
            }
        }
    }

    fun addRedditCommunity(community: RedditCommunity) {
        if (uiState.value.isSearchingReddit) return
        viewModelScope.launch {
            val result = runCatching { sourceRepository.addRedditCommunity(community) }
            result.fold(
                onSuccess = { source ->
                    _uiState.update {
                        it.copy(
                            snackbarMessage = "Added ${source.title}",
                            urlInput = "",
                            detectedType = null,
                            redditSearchResults = emptyList(),
                            redditSearchError = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(snackbarMessage = error.localizedMessage ?: "Unable to add subreddit.")
                    }
                }
            )
        }
    }

    fun addSourceFromInput() {
        val input = uiState.value.urlInput
        if (input.isBlank()) {
            _uiState.update { it.copy(snackbarMessage = "Enter a subreddit or wallpaper URL.") }
            return
        }

        viewModelScope.launch {
            val result = runCatching { sourceRepository.addSourceFromInput(input) }
            result.fold(
                onSuccess = { source ->
                    _uiState.update {
                        it.copy(
                            snackbarMessage = "Added ${source.title}",
                            urlInput = "",
                            detectedType = null,
                            redditSearchResults = emptyList(),
                            redditSearchError = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            snackbarMessage = error.localizedMessage ?: "Unable to add source.",
                            detectedType = sourceRepository.detectRemoteSourceType(it.urlInput)
                        )
                    }
                }
            )
        }
    }

    fun quickAddSource(input: String) {
        if (input.isBlank()) return
        viewModelScope.launch {
            val result = runCatching { sourceRepository.addSourceFromInput(input) }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { source ->
                        state.copy(snackbarMessage = "Added ${source.title}")
                    },
                    onFailure = { error ->
                        state.copy(snackbarMessage = error.localizedMessage ?: "Unable to add source.")
                    }
                )
            }
        }
    }

    fun addGooglePhotosAlbum(album: GooglePhotosAlbum) {
        viewModelScope.launch {
            val result = runCatching { sourceRepository.addGooglePhotosAlbum(album) }
            _uiState.update { state ->
                val message = result.fold(
                    onSuccess = { source -> "Added ${source.title}" },
                    onFailure = { error ->
                        error.localizedMessage ?: "Unable to add Google Photos album."
                    }
                )
                state.copy(snackbarMessage = message)
            }
        }
    }

    fun clearSearchResults() {
        _uiState.update {
            it.copy(redditSearchResults = emptyList(), redditSearchError = null)
        }
    }

    fun removeSource(source: Source, deleteWallpapers: Boolean) {
        viewModelScope.launch {
            runCatching { sourceRepository.removeSource(source, deleteWallpapers) }
                .onSuccess { removedWallpapers ->
                    val message = when {
                        deleteWallpapers && removedWallpapers == 1 ->
                            "Removed ${source.title} and 1 wallpaper"
                        deleteWallpapers && removedWallpapers > 1 ->
                            "Removed ${source.title} and $removedWallpapers wallpapers"
                        else -> "Removed ${source.title}"
                    }
                    _uiState.update {
                        it.copy(snackbarMessage = message)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(snackbarMessage = error.localizedMessage ?: "Unable to remove source.")
                    }
                }
        }
    }

    fun importLocalWallpapers(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val result = runCatching { libraryRepository.importLocalWallpapers(uris) }
            _uiState.update {
                it.copy(
                    snackbarMessage = result.fold(
                        onSuccess = { summary ->
                            when {
                                summary.imported > 0 -> "Imported ${summary.imported} wallpapers"
                                summary.skipped > 0 -> "No new wallpapers imported"
                                else -> "No wallpapers were imported"
                            }
                        },
                        onFailure = { throwable ->
                            throwable.localizedMessage ?: "Unable to import images"
                        }
                    )
                )
            }
        }
    }

    fun importLocalFolder(folderUri: Uri) {
        viewModelScope.launch {
            val result = runCatching { libraryRepository.importLocalFolder(folderUri) }
            _uiState.update {
                it.copy(
                    snackbarMessage = result.fold(
                        onSuccess = { summary ->
                            when {
                                summary.imported > 0 ->
                                    "Imported ${summary.imported} wallpapers to \"${summary.albumTitle}\""
                                else -> "No images found in \"${summary.albumTitle}\""
                            }
                        },
                        onFailure = { throwable ->
                            throwable.localizedMessage ?: "Unable to import folder"
                        }
                    )
                )
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { state ->
            if (state.snackbarMessage == null) state else state.copy(snackbarMessage = null)
        }
    }

    data class SourcesUiState(
        val sources: List<Source> = emptyList(),
        val urlInput: String = "",
        val detectedType: SourceRepository.RemoteSourceType? = null,
        val isSearchingReddit: Boolean = false,
        val redditSearchResults: List<RedditCommunity> = emptyList(),
        val redditSearchError: String? = null,
        val snackbarMessage: String? = null,
        val existingRedditConfigs: Set<String> = emptySet()
    )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                ServiceLocator.initialize(application)
                SourcesViewModel(
                    application = application,
                    sourceRepository = ServiceLocator.sourceRepository,
                    libraryRepository = ServiceLocator.libraryRepository,
                    wallpaperRepository = ServiceLocator.wallpaperRepository
                )
            }
        }
    }
}
