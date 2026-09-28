@file:Suppress("unused")

package com.joshiminh.wallbase.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import com.joshiminh.wallbase.data.DatabaseBackupManager
import com.joshiminh.wallbase.data.repository.AppTheme
import com.joshiminh.wallbase.data.repository.AppAccentColor
import com.joshiminh.wallbase.data.repository.LocalStorageCoordinator
import com.joshiminh.wallbase.data.repository.AlbumLayout
import com.joshiminh.wallbase.data.repository.LibraryRepository
import com.joshiminh.wallbase.data.repository.SettingsRepository
import com.joshiminh.wallbase.data.repository.SourceCredentialStore
import com.joshiminh.wallbase.data.repository.UpdateRepository
import com.joshiminh.wallbase.util.MinResolution
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val backupManager: DatabaseBackupManager,
    private val settingsRepository: SettingsRepository,
    private val updateRepository: UpdateRepository,
    private val localStorage: LocalStorageCoordinator,
    private val libraryRepository: LibraryRepository,
    private val credentialStore: SourceCredentialStore,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.preferences.collectLatest { preferences ->
                _uiState.update {
                    it.copy(
                        appTheme = preferences.appTheme,
                        appAccentColor = preferences.appAccentColor,
                        dynamicColor = preferences.dynamicColor,
                        amoledDark = preferences.amoledDark,
                        animationsEnabled = preferences.animationsEnabled,
                        wallpaperGridColumns = preferences.wallpaperGridColumns,
                        albumLayout = preferences.albumLayout,
                        autoDownload = preferences.autoDownload,
                        includeSourcesInBackup = preferences.includeSourcesInBackup,
                        storageLimitBytes = preferences.storageLimitBytes,
                        dismissedUpdateVersion = preferences.dismissedUpdateVersion,
                        appLockEnabled = preferences.appLockEnabled,
                        hasCompletedOnboarding = preferences.onboardingCompleted,
                        showHorizontalWallpapers = preferences.showHorizontalWallpapers,
                        showDownloadBadge = preferences.showDownloadBadge,
                        minResolution = preferences.minResolution,
                    )
                }
            }
        }

        refreshStorageSnapshot()
        refreshSourceConnectionState()
        autoCheckForUpdates()
    }

    fun setMinResolution(minResolution: MinResolution) {
        if (_uiState.value.minResolution == minResolution) return
        _uiState.update { it.copy(minResolution = minResolution) }
        viewModelScope.launch {
            settingsRepository.setMinResolution(minResolution)
        }
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

    fun setShowHorizontalWallpapers(show: Boolean) {
        if (_uiState.value.showHorizontalWallpapers == show) return
        _uiState.update { it.copy(showHorizontalWallpapers = show) }
        viewModelScope.launch {
            settingsRepository.setShowHorizontalWallpapers(show)
        }
    }

    fun markOnboardingComplete() {
        if (_uiState.value.hasCompletedOnboarding) return
        _uiState.update { it.copy(hasCompletedOnboarding = true) }
        viewModelScope.launch {
            settingsRepository.setOnboardingCompleted(true)
        }
    }

    private var downloadJob: Job? = null

    fun autoCheckForUpdates() {
        viewModelScope.launch(Dispatchers.IO) {
            // Delay running the background check so we don't compete during cold start / app opening
            delay(5000)
            when (val result = updateRepository.checkForUpdates()) {
                is UpdateRepository.UpdateResult.UpdateAvailable -> {
                    val releaseUrl = result.downloadUrl ?: DEFAULT_RELEASES_URL
                    _uiState.update { state ->
                        if (state.dismissedUpdateVersion == result.version) {
                            state.copy(hasCheckedForUpdates = true)
                        } else {
                            state.copy(
                                availableUpdateVersion = result.version,
                                updateNotes = result.notes,
                                updateUrl = releaseUrl,
                                apkDownloadUrl = result.apkDownloadUrl,
                                releasePageUrl = result.releasePageUrl ?: releaseUrl,
                                hasCheckedForUpdates = true,
                                showUpdateDialog = false,
                                updateError = null
                            )
                        }
                    }
                }
                is UpdateRepository.UpdateResult.UpToDate -> {
                    _uiState.update { it.copy(hasCheckedForUpdates = true) }
                }
                is UpdateRepository.UpdateResult.Error -> {
                    // Silently ignore errors during background check
                }
            }
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
                            apkDownloadUrl = null,
                            releasePageUrl = null,
                            hasCheckedForUpdates = true,
                            showUpdateDialog = false,
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
                                apkDownloadUrl = result.apkDownloadUrl,
                                releasePageUrl = result.releasePageUrl ?: releaseUrl,
                                hasCheckedForUpdates = true,
                                showUpdateDialog = true,
                                updateError = null,
                                updateDownloadError = null
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

    fun startUpdateDownloadAndInstall() {
        val state = _uiState.value
        val downloadUrl = state.apkDownloadUrl ?: state.updateUrl
        val version = state.availableUpdateVersion ?: "latest"

        if (downloadUrl == null) {
            _uiState.update {
                it.copy(
                    updateDownloadError = "No download URL available for this update.",
                    showUpdateDialog = true
                )
            }
            return
        }

        // If the URL is just an HTML page (no direct APK), advise downloading via browser
        if (!downloadUrl.endsWith(".apk") && state.apkDownloadUrl == null) {
            _uiState.update {
                it.copy(
                    updateDownloadError = "No APK asset found on release. Please download via browser.",
                    showUpdateDialog = true
                )
            }
            return
        }

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            val context = getApplication<Application>()
            val updatesDir = File(context.cacheDir, "updates")
            val targetFile = File(updatesDir, "WallBase-$version.apk")

            _uiState.update {
                it.copy(
                    isDownloadingUpdate = true,
                    updateDownloadProgress = 0f,
                    updateDownloadBytes = 0L,
                    updateDownloadTotalBytes = 0L,
                    updateDownloadError = null,
                    showUpdateDialog = true
                )
            }

            val result = updateRepository.downloadApk(downloadUrl, targetFile) { bytesRead, totalBytes ->
                val progress = if (totalBytes > 0) bytesRead.toFloat() / totalBytes else null
                _uiState.update {
                    it.copy(
                        updateDownloadProgress = progress,
                        updateDownloadBytes = bytesRead,
                        updateDownloadTotalBytes = totalBytes
                    )
                }
            }

            result.fold(
                onSuccess = { file ->
                    _uiState.update {
                        it.copy(
                            isDownloadingUpdate = false,
                            updateDownloadProgress = 1f,
                            downloadedApkFile = file
                        )
                    }
                    val installResult = installDownloadedApk(context, file)
                    if (installResult.isFailure) {
                        _uiState.update {
                            it.copy(
                                updateDownloadError = installResult.exceptionOrNull()?.localizedMessage
                                    ?: "Failed to open package installer"
                            )
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isDownloadingUpdate = false,
                            updateDownloadError = error.localizedMessage ?: "Download failed"
                        )
                    }
                }
            )
        }
    }

    fun cancelUpdateDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _uiState.update {
            it.copy(
                isDownloadingUpdate = false,
                updateDownloadProgress = null,
                updateDownloadError = null
            )
        }
    }

    fun installDownloadedApk(
        context: Context = getApplication(),
        file: File? = _uiState.value.downloadedApkFile
    ): Result<Unit> {
        val apkFile = file ?: return Result.failure(IllegalStateException("No APK file downloaded"))
        return runCatching {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                throw IllegalStateException("Downloaded APK file not found or corrupted.")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val manageIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(manageIntent)
                    throw SecurityException("Please enable 'Allow from this source', then tap Install Now.")
                }
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            _uiState.update { it.copy(updateDownloadError = null) }
        }
    }

    fun promptInstallDownloadedApk() {
        val result = installDownloadedApk()
        if (result.isFailure) {
            _uiState.update {
                it.copy(
                    updateDownloadError = result.exceptionOrNull()?.localizedMessage
                        ?: "Failed to launch package installer."
                )
            }
        }
    }

    fun clearUpdateDownloadError() {
        _uiState.update { it.copy(updateDownloadError = null) }
    }

    fun showUpdateDialog() {
        _uiState.update { it.copy(showUpdateDialog = true) }
    }

    fun dismissUpdateDialogOnly() {
        _uiState.update { it.copy(showUpdateDialog = false) }
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
                showUpdateDialog = false,
                dismissedUpdateVersion = version,
                hasCheckedForUpdates = true
            )
        }
    }

    fun onUpdateUrlOpened(@Suppress("UNUSED_PARAMETER") url: String) {
        if (_uiState.value.availableUpdateVersion == null) return
        dismissAvailableUpdate()
    }

    fun setAppTheme(theme: AppTheme) {
        if (_uiState.value.appTheme == theme) return
        _uiState.update { it.copy(appTheme = theme) }
        viewModelScope.launch {
            settingsRepository.setAppTheme(theme)
        }
    }

    fun setAppAccentColor(color: AppAccentColor) {
        if (_uiState.value.appAccentColor == color) return
        _uiState.update { it.copy(appAccentColor = color) }
        viewModelScope.launch {
            settingsRepository.setAppAccentColor(color)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        if (_uiState.value.dynamicColor == enabled) return
        _uiState.update { it.copy(dynamicColor = enabled) }
        viewModelScope.launch {
            settingsRepository.setDynamicColor(enabled)
        }
    }

    fun setAmoledDark(enabled: Boolean) {
        if (_uiState.value.amoledDark == enabled) return
        _uiState.update { it.copy(amoledDark = enabled) }
        viewModelScope.launch {
            settingsRepository.setAmoledDark(enabled)
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

    fun setShowDownloadBadge(show: Boolean) {
        if (_uiState.value.showDownloadBadge == show) return
        _uiState.update { it.copy(showDownloadBadge = show) }
        viewModelScope.launch {
            settingsRepository.setShowDownloadBadge(show)
        }
    }

    fun showMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    fun saveSourceCredentials(wallhavenApiKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = credentialStore.snapshot()
            credentialStore.save(
                SourceCredentialStore.SourceCredentials(
                    wallhavenApiKey = wallhavenApiKey.trim().ifBlank { existing.wallhavenApiKey },
                )
            )
            refreshSourceConnectionState()
            _uiState.update { it.copy(message = "Source connection settings saved") }
        }
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

    @Immutable
    data class SettingsUiState(
        val isBackingUp: Boolean = false,
        val isRestoring: Boolean = false,
        val message: String? = null,
        val appTheme: AppTheme = AppTheme.SYSTEM,
        val appAccentColor: AppAccentColor = AppAccentColor.PINK,
        val dynamicColor: Boolean = false,
        val amoledDark: Boolean = false,
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
        val apkDownloadUrl: String? = null,
        val releasePageUrl: String? = null,
        val updateError: String? = null,
        val hasCheckedForUpdates: Boolean = false,
        val showUpdateDialog: Boolean = false,
        val isDownloadingUpdate: Boolean = false,
        val updateDownloadProgress: Float? = null,
        val updateDownloadBytes: Long = 0L,
        val updateDownloadTotalBytes: Long = 0L,
        val downloadedApkFile: File? = null,
        val updateDownloadError: String? = null,
        val dismissedUpdateVersion: String? = null,
        val shouldRestartAfterImport: Boolean = false,
        val showHorizontalWallpapers: Boolean = true,
        val showDownloadBadge: Boolean = true,
        val minResolution: MinResolution = MinResolution.ANY,
        val wallhavenTokenConfigured: Boolean = false,
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

    private fun refreshSourceConnectionState() {
        val credentials = credentialStore.snapshot()
        _uiState.update {
            it.copy(
                wallhavenTokenConfigured = credentials.hasWallhavenToken,
            )
        }
    }
}
