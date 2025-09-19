package com.joshiminh.wallbase.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.joshiminh.wallbase.data.library.LibraryRepository
import com.joshiminh.wallbase.data.source.RedditCommunity
import com.joshiminh.wallbase.data.source.Source
import com.joshiminh.wallbase.data.source.SourceKeys
import com.joshiminh.wallbase.data.source.SourceRepository
import com.joshiminh.wallbase.data.wallpapers.WallpaperRepository
import com.joshiminh.wallbase.di.ServiceLocator
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
        val detected = sourceRepository.detectRemoteSourceType(input)
        _uiState.update {
            val shouldClearResults =
                it.detectedType == SourceRepository.RemoteSourceType.REDDIT &&
                        detected != SourceRepository.RemoteSourceType.REDDIT
            it.copy(
                urlInput = input,
                detectedType = detected,
                redditSearchResults = if (shouldClearResults) emptyList() else it.redditSearchResults,
                redditSearchError = if (shouldClearResults) null else it.redditSearchError
            )
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

    fun clearSearchResults() {
        _uiState.update {
            it.copy(redditSearchResults = emptyList(), redditSearchError = null)
        }
    }

    fun removeSource(source: Source) {
        viewModelScope.launch {
            runCatching { sourceRepository.removeSource(source) }
                .onSuccess {
                    _uiState.update {
                        it.copy(snackbarMessage = "Removed ${source.title}")
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
            val result = runCatching { libraryRepository.importLocalWallpapers(getApplication(), uris) }
            _uiState.update {
                it.copy(
                    snackbarMessage = result.fold(
                        onSuccess = { "Imported ${uris.size} wallpapers" },
                        onFailure = { throwable ->
                            throwable.localizedMessage ?: "Unable to import images"
                        }
                    )
                )
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
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
