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
import com.joshiminh.wallbase.data.DatabaseBackupManager
import com.joshiminh.wallbase.data.repository.LocalStorageCoordinator
import com.joshiminh.wallbase.data.repository.AlbumLayout
import com.joshiminh.wallbase.data.repository.SettingsRepository
import com.joshiminh.wallbase.util.network.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(
    application: Application,
    private val backupManager: DatabaseBackupManager,
    private val settingsRepository: SettingsRepository,
    private val localStorage: LocalStorageCoordinator
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.preferences.collectLatest { preferences ->
                _uiState.update {
                    it.copy(
                        darkTheme = preferences.darkTheme,
                        sourceRepoUrl = preferences.sourceRepoUrl,
                        wallpaperGridColumns = preferences.wallpaperGridColumns,
                        albumLayout = preferences.albumLayout
                    )
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            val usage = calculateStorageUsage()
            val wallpapersDir = runCatching { localStorage.currentBaseDirectory() }.getOrNull()
            val wallpapersBytes = wallpapersDir?.let { directorySize(it) }
            _uiState.update {
                it.copy(
                    storageBytes = usage?.usedBytes,
                    storageTotalBytes = usage?.totalBytes,
                    wallpapersBytes = wallpapersBytes,
                    isStorageLoading = false
                )
            }
        }
    }

    fun exportBackup(destination: Uri) {
        if (_uiState.value.isBackingUp) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBackingUp = true, message = null) }
            val result = backupManager.exportBackup(destination)
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
            val message = result.fold(
                onSuccess = {
                    "Backup imported."
                },
                onFailure = { error ->
                    val detail = error.localizedMessage
                    if (detail.isNullOrBlank()) {
                        "Unable to import backup."
                    } else {
                        "Unable to import backup ($detail)."
                    }
                }
            )
            _uiState.update { it.copy(isRestoring = false, message = message) }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun setDarkTheme(enabled: Boolean) {
        if (_uiState.value.darkTheme == enabled) return
        _uiState.update { it.copy(darkTheme = enabled) }
        viewModelScope.launch {
            settingsRepository.setDarkTheme(enabled)
        }
    }

    fun updateSourceRepoUrl(url: String) {
        if (_uiState.value.sourceRepoUrl == url) return
        _uiState.update { it.copy(sourceRepoUrl = url) }
        viewModelScope.launch {
            settingsRepository.setSourceRepoUrl(url)
        }
    }

    data class SettingsUiState(
        val isBackingUp: Boolean = false,
        val isRestoring: Boolean = false,
        val message: String? = null,
        val darkTheme: Boolean = false,
        val sourceRepoUrl: String = "",
        val wallpaperGridColumns: Int = 2,
        val albumLayout: AlbumLayout = AlbumLayout.CARD_LIST,
        val storageBytes: Long? = null,
        val storageTotalBytes: Long? = null,
        val wallpapersBytes: Long? = null,
        val isStorageLoading: Boolean = true
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
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                SettingsViewModel(
                    application = application,
                    backupManager = DatabaseBackupManager(
                        application.applicationContext,
                        ServiceLocator.localStorageCoordinator
                    ),
                    settingsRepository = ServiceLocator.settingsRepository,
                    localStorage = ServiceLocator.localStorageCoordinator
                )
            }
        }
    }
}