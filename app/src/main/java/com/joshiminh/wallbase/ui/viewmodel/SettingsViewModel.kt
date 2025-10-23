@file:Suppress("unused")

package com.joshiminh.wallbase.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.SingletonImageLoader
import com.joshiminh.wallbase.data.DatabaseBackupManager
import com.joshiminh.wallbase.data.repository.LocalStorageCoordinator
import com.joshiminh.wallbase.data.repository.AlbumLayout
import com.joshiminh.wallbase.data.repository.LibraryRepository
import com.joshiminh.wallbase.data.repository.SettingsRepository
import com.joshiminh.wallbase.data.repository.UpdateRepository
import com.joshiminh.wallbase.util.network.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsViewModel(
    application: Application,
    private val backupManager: DatabaseBackupManager,
    private val settingsRepository: SettingsRepository,
    private val updateRepository: UpdateRepository,
    private val localStorage: LocalStorageCoordinator,
    private val libraryRepository: LibraryRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.preferences.collectLatest { preferences ->
                _uiState.update {
                    it.copy(
                        darkTheme = preferences.darkTheme,
                        animationsEnabled = preferences.animationsEnabled,
                        wallpaperGridColumns = preferences.wallpaperGridColumns,
                        albumLayout = preferences.albumLayout,
                        autoDownload = preferences.autoDownload,
                        includeSourcesInBackup = preferences.includeSourcesInBackup,
                        storageLimitBytes = preferences.storageLimitBytes,
                        dismissedUpdateVersion = preferences.dismissedUpdateVersion,
                        appLockEnabled = preferences.appLockEnabled,
                        hasCompletedOnboarding = preferences.onboardingCompleted,
                    )
                }
            }
        }

        refreshStorageSnapshot()
    }

    fun exportBackup(destination: Uri, includeSources: Boolean) {
        if (_uiState.value.isBackingUp) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBackingUp = true, message = null) }
            val result = backupManager.exportBackup(destination, includeSources)
            val message = result.fold(
                onSuccess = {
                    "Backup saved."
                },
                onFailure = { error ->
                    val detail = error.localizedMessage
                    if (detail.isNullOrBlank()) {
                        "Unable to export backup."
                    } else {
                        "Unable to export backup ($detail)."
                    }
                }
            )
            _uiState.update { it.copy(isBackingUp = false, message = message) }
        }
    }

    fun importBackup(source: Uri) {
        if (_uiState.value.isRestoring) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRestoring = true, message = null) }
            val result = backupManager.importBackup(source)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isRestoring = false,
                            message = "Backup imported. Restarting…",
                            shouldRestartAfterImport = true
                        )
                    }
                },
                onFailure = { error ->
                    val detail = error.localizedMessage
                    val message = if (detail.isNullOrBlank()) {
                        "Unable to import backup."
                    } else {
                        "Unable to import backup ($detail)."
                    }
                    _uiState.update {
                        it.copy(
                            isRestoring = false,
                            message = message
                        )
                    }
                }
            )
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun setIncludeSourcesInBackup(include: Boolean) {
        if (_uiState.value.includeSourcesInBackup == include) return
        _uiState.update { it.copy(includeSourcesInBackup = include) }
        viewModelScope.launch {
            settingsRepository.setIncludeSourcesInBackup(include)
        }
    }

    fun markOnboardingComplete() {
        if (_uiState.value.hasCompletedOnboarding) return
        _uiState.update { it.copy(hasCompletedOnboarding = true) }
        viewModelScope.launch {
            settingsRepository.setOnboardingCompleted(true)
        }
    }

    fun checkForUpdates() {
        if (_uiState.value.isCheckingForUpdates) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCheckingForUpdates = true,
                    updateError = null
                )
            }
            when (val result = updateRepository.checkForUpdates()) {
                is UpdateRepository.UpdateResult.UpToDate -> {
                    _uiState.update {
                        it.copy(
                            isCheckingForUpdates = false,
                            availableUpdateVersion = null,
                            updateNotes = null,
                            updateUrl = null,
                            hasCheckedForUpdates = true,
                            updateError = null
                        )
                    }
                }

                is UpdateRepository.UpdateResult.UpdateAvailable -> {
                    val releaseUrl = result.downloadUrl ?: DEFAULT_RELEASES_URL
                    _uiState.update { state ->
                        if (state.dismissedUpdateVersion == result.version) {
                            state.copy(
                                isCheckingForUpdates = false,
                                hasCheckedForUpdates = true,
                                updateError = null
                            )
                        } else {
                            state.copy(
                                isCheckingForUpdates = false,
                                availableUpdateVersion = result.version,
                                updateNotes = result.notes,
                                updateUrl = releaseUrl,
                                hasCheckedForUpdates = true,
                                updateError = null
                            )
                        }
                    }
                }

                is UpdateRepository.UpdateResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isCheckingForUpdates = false,
                            updateError = result.throwable.localizedMessage
                                ?: "Unable to check for updates.",
                            hasCheckedForUpdates = true
                        )
                    }
                }
            }
        }
    }

    fun clearUpdateStatus() {
        _uiState.update {
            it.copy(updateError = null)
        }
    }

    fun dismissAvailableUpdate() {
        val version = _uiState.value.availableUpdateVersion ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                settingsRepository.setDismissedUpdateVersion(version)
            }
        }
        _uiState.update {
            it.copy(
                availableUpdateVersion = null,
                updateNotes = null,
                updateUrl = null,
                dismissedUpdateVersion = version,
                hasCheckedForUpdates = true
            )
        }
    }

    fun onUpdateUrlOpened(@Suppress("UNUSED_PARAMETER") url: String) {
        if (_uiState.value.availableUpdateVersion == null) return
        dismissAvailableUpdate()
    }

    fun setDarkTheme(enabled: Boolean) {
        if (_uiState.value.darkTheme == enabled) return
        _uiState.update { it.copy(darkTheme = enabled) }
        viewModelScope.launch {
            settingsRepository.setDarkTheme(enabled)
        }
    }

    fun setAutoDownload(enabled: Boolean) {
        if (_uiState.value.autoDownload == enabled) return
        _uiState.update { it.copy(autoDownload = enabled) }
        viewModelScope.launch {
            settingsRepository.setAutoDownload(enabled)
        }
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        if (_uiState.value.animationsEnabled == enabled) return
        _uiState.update { it.copy(animationsEnabled = enabled) }
        viewModelScope.launch {
            settingsRepository.setAnimationsEnabled(enabled)
        }
    }

    fun setStorageLimit(limitBytes: Long) {
        if (_uiState.value.storageLimitBytes == limitBytes) return
        _uiState.update { it.copy(storageLimitBytes = limitBytes, isStorageLoading = true) }
        viewModelScope.launch {
            settingsRepository.setStorageLimitBytes(limitBytes)
            refreshStorageSnapshot()
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        if (_uiState.value.appLockEnabled == enabled) return
        _uiState.update { it.copy(appLockEnabled = enabled) }
        viewModelScope.launch {
            settingsRepository.setAppLockEnabled(enabled)
        }
    }

    fun showMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    fun consumeRestartRequest() {
        if (!_uiState.value.shouldRestartAfterImport) return
        _uiState.update { it.copy(shouldRestartAfterImport = false) }
    }

    fun clearPreviewCache() {
        if (_uiState.value.isClearingPreviews) return
        viewModelScope.launch {
            _uiState.update { it.copy(isClearingPreviews = true) }
            val context = getApplication<Application>()
            withContext(Dispatchers.IO) {
                runCatching {
                    SingletonImageLoader.get(context).diskCache?.clear()
                }
            }
            refreshStorageSnapshot()
            _uiState.update {
                it.copy(
                    isClearingPreviews = false,
                    message = "Deleted preview cache"
                )
            }
        }
    }

    fun clearOriginalDownloads() {
        if (_uiState.value.isClearingOriginals) return
        viewModelScope.launch {
            _uiState.update { it.copy(isClearingOriginals = true) }
            val result = withContext(Dispatchers.IO) {
                runCatching { libraryRepository.removeAllDownloads() }
            }
            refreshStorageSnapshot()
            _uiState.update { state ->
                state.copy(
                    isClearingOriginals = false,
                    message = result.fold(
                        onSuccess = { summary ->
                            when {
                                summary.removed > 0 && summary.failed > 0 ->
                                    "Removed ${summary.removed} downloads (failed ${summary.failed})"
                                summary.removed > 0 ->
                                    "Removed ${summary.removed} downloads"
                                summary.skipped > 0 ->
                                    "No downloads to remove"
                                else -> "No downloads removed"
                            }
                        },
                        onFailure = { error ->
                            error.localizedMessage ?: "Unable to remove downloads"
                        }
                    )
                )
            }
        }
    }

    data class SettingsUiState(
        val isBackingUp: Boolean = false,
        val isRestoring: Boolean = false,
        val message: String? = null,
        val darkTheme: Boolean = false,
        val animationsEnabled: Boolean = true,
        val wallpaperGridColumns: Int = 2,
        val albumLayout: AlbumLayout = AlbumLayout.CARD_LIST,
        val storageBytes: Long? = null,
        val storageTotalBytes: Long? = null,
        val wallpapersBytes: Long? = null,
        val previewCacheBytes: Long? = null,
        val storageLimitBytes: Long = 0,
        val autoDownload: Boolean = false,
        val isStorageLoading: Boolean = true,
        val isClearingPreviews: Boolean = false,
        val isClearingOriginals: Boolean = false,
        val includeSourcesInBackup: Boolean = true,
        val appLockEnabled: Boolean = false,
        val hasCompletedOnboarding: Boolean = false,
        val isCheckingForUpdates: Boolean = false,
        val availableUpdateVersion: String? = null,
        val updateNotes: String? = null,
        val updateUrl: String? = null,
        val updateError: String? = null,
        val hasCheckedForUpdates: Boolean = false,
        val dismissedUpdateVersion: String? = null,
        val shouldRestartAfterImport: Boolean = false,
    )

    private data class StorageUsage(
        val usedBytes: Long,
        val totalBytes: Long
    )

    private fun calculateStorageUsage(): StorageUsage? {
        return try {
            val context = getApplication<Application>()
            val dataDir = context.dataDir ?: context.filesDir?.parentFile
            val totalBytes = dataDir?.let { StatFs(it.absolutePath).totalBytes }
                ?: StatFs(context.filesDir.absolutePath).totalBytes

            val appInfo = context.applicationInfo
            val sourceDirs = buildList {
                add(appInfo.sourceDir)
                appInfo.publicSourceDir?.let { add(it) }
                appInfo.splitSourceDirs?.let { addAll(it) }
            }

            val apkBytes = sourceDirs.distinct().sumOf { path ->
                if (path.isNullOrBlank()) 0L else File(path).length()
            }

            val dataBytes = dataDir?.let { directorySize(it) } ?: 0L

            StorageUsage(
                usedBytes = dataBytes + apkBytes,
                totalBytes = totalBytes
            )
        } catch (error: Throwable) {
            null
        }
    }

    private fun directorySize(root: File): Long {
        if (!root.exists()) return 0L
        return try {
            root.walkBottomUp()
                .filter { it.isFile }
                .fold(0L) { acc, file -> acc + file.length() }
        } catch (_: Throwable) {
            0L
        }
    }

    companion object {
        private const val DEFAULT_RELEASES_URL = "https://github.com/JoshiMinh/WallBase/releases"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                ServiceLocator.ensureInitialized(application)
                SettingsViewModel(
                    application = application,
                    backupManager = DatabaseBackupManager(
                        application.applicationContext,
                        ServiceLocator.localStorageCoordinator
                    ),
                    settingsRepository = ServiceLocator.settingsRepository,
                    updateRepository = ServiceLocator.updateRepository,
                    localStorage = ServiceLocator.localStorageCoordinator,
                    libraryRepository = ServiceLocator.libraryRepository
                )
            }
        }
    }

    private fun refreshStorageSnapshot() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            _uiState.update { it.copy(isStorageLoading = true) }
            runCatching { localStorage.cleanupLegacyEditorCache() }
            val usage = calculateStorageUsage()
            val wallpapersDir = runCatching { localStorage.currentBaseDirectory() }.getOrNull()
            val wallpapersBytes = wallpapersDir?.let { directorySize(it) }
            val previewCacheBytes = runCatching {
                SingletonImageLoader.get(context).diskCache?.size ?: 0L
            }.getOrDefault(0L)
            _uiState.update {
                it.copy(
                    storageBytes = usage?.usedBytes,
                    storageTotalBytes = usage?.totalBytes,
                    wallpapersBytes = wallpapersBytes,
                    previewCacheBytes = previewCacheBytes,
                    isStorageLoading = false
                )
            }
        }
    }
}