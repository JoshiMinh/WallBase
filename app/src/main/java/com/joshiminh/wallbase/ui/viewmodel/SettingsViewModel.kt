package com.joshiminh.wallbase.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.joshiminh.wallbase.data.DatabaseBackupManager
import com.joshiminh.wallbase.data.repository.SettingsRepository
import com.joshiminh.wallbase.util.network.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    application: Application,
    private val backupManager: DatabaseBackupManager,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.preferences.collectLatest { preferences ->
                _uiState.update {
                    it.copy(
                        darkTheme = preferences.darkTheme,
                        sourceRepoUrl = preferences.sourceRepoUrl
                    )
                }
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
        val sourceRepoUrl: String = ""
    )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                SettingsViewModel(
                    application = application,
                    backupManager = DatabaseBackupManager(application.applicationContext),
                    settingsRepository = ServiceLocator.settingsRepository
                )
            }
        }
    }
}
