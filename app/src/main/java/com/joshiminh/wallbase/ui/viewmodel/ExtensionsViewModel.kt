package com.joshiminh.wallbase.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.joshiminh.wallbase.data.entity.WallpaperItem
import com.joshiminh.wallbase.scraper.engine.DeclarativeScraperEngine
import com.joshiminh.wallbase.scraper.model.ExtensionRepoItem
import com.joshiminh.wallbase.scraper.model.SourceManifest
import com.joshiminh.wallbase.scraper.repository.ExtensionRepositoryManager
import com.joshiminh.wallbase.util.network.ServiceLocator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExtensionsViewModel @Inject constructor(
    private val repositoryManager: ExtensionRepositoryManager,
    private val scraperEngine: DeclarativeScraperEngine
) : ViewModel() {

    data class ExtensionsUiState(
        val installedExtensions: List<SourceManifest> = emptyList(),
        val communityCatalog: List<ExtensionRepoItem> = emptyList(),
        val subscribedRepos: Set<String> = emptySet(),
        val selectedTab: Int = 0,
        val isLoading: Boolean = false,
        val searchQuery: String = "",
        val snackbarMessage: String? = null,
        val testWallpapers: List<WallpaperItem> = emptyList(),
        val testError: String? = null,
        val isTestingScraper: Boolean = false
    )

    private val _uiState = MutableStateFlow(ExtensionsUiState())
    val uiState: StateFlow<ExtensionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repositoryManager.ensureDefaultExtensionsInstalled()
            repositoryManager.observeInstalledManifests().collect { manifests ->
                _uiState.update { it.copy(installedExtensions = manifests) }
            }
        }
        viewModelScope.launch {
            repositoryManager.observeSubscribedRepos().collect { repos ->
                _uiState.update { it.copy(subscribedRepos = repos) }
                syncAllRepos(repos)
            }
        }
    }

    fun selectTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val installed = repositoryManager.getInstalledManifestsList()
            _uiState.update { it.copy(installedExtensions = installed) }
            syncAllRepos(uiState.value.subscribedRepos)
            _uiState.update { it.copy(isLoading = false, snackbarMessage = "Extensions refreshed") }
        }
    }

    private suspend fun syncAllRepos(repos: Set<String>) {
        _uiState.update { it.copy(isLoading = true) }
        val catalogItems = mutableListOf<ExtensionRepoItem>()
        for (repoUrl in repos) {
            val result = repositoryManager.fetchRemoteRepo(repoUrl)
            result.getOrNull()?.sources?.let { catalogItems.addAll(it) }
        }
        _uiState.update {
            it.copy(
                communityCatalog = catalogItems.distinctBy { item -> item.id },
                isLoading = false
            )
        }
    }

    fun installFromCatalog(item: ExtensionRepoItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repositoryManager.downloadAndInstallFromUrl(item.manifestUrl)
            result.fold(
                onSuccess = { manifest ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            snackbarMessage = "Installed ${manifest.name}"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            snackbarMessage = "Install failed: ${error.localizedMessage}"
                        )
                    }
                }
            )
        }
    }

    fun importFromRawJson(json: String) {
        viewModelScope.launch {
            val result = repositoryManager.parseManifestFromJson(json)
            result.fold(
                onSuccess = { manifest ->
                    repositoryManager.installOrUpdateManifest(manifest)
                    _uiState.update { it.copy(snackbarMessage = "Imported ${manifest.name}") }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(snackbarMessage = "Invalid JSON: ${error.localizedMessage}") }
                }
            )
        }
    }

    fun importFromFileUri(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.fold(
                onSuccess = { json ->
                    if (!json.isNullOrBlank()) {
                        importFromRawJson(json)
                    } else {
                        _uiState.update { it.copy(snackbarMessage = "File was empty") }
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(snackbarMessage = "Failed reading file: ${error.localizedMessage}") }
                }
            )
        }
    }

    fun uninstallExtension(manifestId: String) {
        viewModelScope.launch {
            repositoryManager.uninstallManifest(manifestId)
            val updated = repositoryManager.getInstalledManifestsList()
            _uiState.update {
                it.copy(
                    installedExtensions = updated,
                    snackbarMessage = "Uninstalled extension"
                )
            }
        }
    }

    fun addRepository(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            repositoryManager.addSubscribedRepo(url)
            _uiState.update { it.copy(snackbarMessage = "Added repository") }
        }
    }

    fun removeRepository(url: String) {
        viewModelScope.launch {
            repositoryManager.removeSubscribedRepo(url)
            _uiState.update { it.copy(snackbarMessage = "Removed repository") }
        }
    }

    fun testScraper(manifestJson: String, testQuery: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingScraper = true, testError = null, testWallpapers = emptyList()) }
            val parseResult = repositoryManager.parseManifestFromJson(manifestJson)
            val manifest = parseResult.getOrNull()
            if (manifest == null) {
                _uiState.update {
                    it.copy(
                        isTestingScraper = false,
                        testError = "JSON Parse Error: ${parseResult.exceptionOrNull()?.localizedMessage}"
                    )
                }
                return@launch
            }

            val scrapeResult = runCatching {
                scraperEngine.scrape(
                    manifest = manifest,
                    query = testQuery,
                    limit = 10
                )
            }

            scrapeResult.fold(
                onSuccess = { page ->
                    _uiState.update {
                        it.copy(
                            isTestingScraper = false,
                            testWallpapers = page.wallpapers,
                            testError = if (page.wallpapers.isEmpty()) "Scraped 0 wallpapers. Check your selectors." else null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isTestingScraper = false,
                            testError = "Scrape Failed: ${error.localizedMessage}"
                        )
                    }
                }
            )
        }
    }

    fun clearTestResults() {
        _uiState.update { it.copy(testWallpapers = emptyList(), testError = null, isTestingScraper = false) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                ExtensionsViewModel(
                    repositoryManager = ServiceLocator.extensionRepositoryManager,
                    scraperEngine = ServiceLocator.declarativeScraperEngine
                )
            }
        }
    }
}

