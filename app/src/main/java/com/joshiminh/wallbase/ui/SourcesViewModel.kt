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
import com.joshiminh.wallbase.data.source.SourceRepository
import com.joshiminh.wallbase.data.source.SourceKeys
import com.joshiminh.wallbase.di.ServiceLocator
import com.joshiminh.wallbase.data.wallpapers.WallpaperRepository
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
                        existingRedditConfigs = redditConfigs
                    )
                }
            }
        }
    }

    fun toggleSource(source: Source, enabled: Boolean) {
        viewModelScope.launch {
            sourceRepository.setSourceEnabled(source, enabled)
        }
    }

    fun updateRedditQuery(query: String) {
        _uiState.update { it.copy(redditQuery = query) }
    }

    fun searchRedditCommunities() {
        val query = uiState.value.redditQuery.trim()
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
            val result = runCatching {
                sourceRepository.addRedditSource(
                    slug = community.name,
                    displayName = community.displayName,
                    description = community.description
                )
            }
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            snackbarMessage = "Added ${community.displayName}",
                            redditQuery = "",
                            redditSearchResults = emptyList()
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

    fun addRedditFromQuery() {
        val parsed = parseSubredditInput(uiState.value.redditQuery)
        if (parsed == null) {
            _uiState.update { it.copy(snackbarMessage = "Enter a valid subreddit URL or name.") }
            return
        }

        viewModelScope.launch {
            val result = runCatching {
                sourceRepository.addRedditSource(
                    slug = parsed.slug,
                    displayName = parsed.displayName,
                    description = null
                )
            }
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            snackbarMessage = "Added ${parsed.displayName}",
                            redditQuery = "",
                            redditSearchResults = emptyList()
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

    fun clearSearchResults() {
        _uiState.update { it.copy(redditSearchResults = emptyList(), redditSearchError = null) }
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
        val redditQuery: String = "",
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

    private fun parseSubredditInput(input: String): ParsedSubreddit? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        val normalized = trimmed
            .removePrefixIgnoreCase("https://")
            .removePrefixIgnoreCase("http://")
            .removePrefixIgnoreCase("www.")
            .substringAfterDomain("reddit.com/")
            .removePrefixIgnoreCase("r/")
            .substringBefore('/')
            .trim()
        if (normalized.isBlank()) return null

        val slug = normalized.lowercase(Locale.ROOT)
        return ParsedSubreddit(slug = slug, displayName = "r/$normalized")
    }

    private data class ParsedSubreddit(val slug: String, val displayName: String)

    private fun String.removePrefixIgnoreCase(prefix: String): String {
        return if (startsWith(prefix, ignoreCase = true)) {
            substring(prefix.length)
        } else {
            this
        }
    }

    private fun String.substringAfterDomain(domain: String): String {
        val index = indexOf(domain, ignoreCase = true)
        return if (index >= 0) substring(index + domain.length) else this
    }
}
