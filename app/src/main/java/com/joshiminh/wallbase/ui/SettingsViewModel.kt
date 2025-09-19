package com.joshiminh.wallbase.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.joshiminh.wallbase.R
import com.joshiminh.wallbase.data.local.DatabaseBackupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    application: Application,
    private val backupManager: DatabaseBackupManager = DatabaseBackupManager(application.applicationContext)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun exportBackup(destination: Uri) {
        if (_uiState.value.isBackingUp) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBackingUp = true, message = null) }
            val result = backupManager.exportBackup(destination)
            val message = result.fold(
                onSuccess = {
                    getApplication<Application>().getString(R.string.settings_backup_export_success)
                },
                onFailure = { error ->
                    val detail = error.localizedMessage
                    val app = getApplication<Application>()
                    if (detail.isNullOrBlank()) {
                        app.getString(R.string.settings_backup_export_failure)
                    } else {
                        app.getString(R.string.settings_backup_export_failure_with_reason, detail)
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
                    getApplication<Application>().getString(R.string.settings_backup_import_success)
                },
                onFailure = { error ->
                    val detail = error.localizedMessage
                    val app = getApplication<Application>()
                    if (detail.isNullOrBlank()) {
                        app.getString(R.string.settings_backup_import_failure)
                    } else {
                        app.getString(R.string.settings_backup_import_failure_with_reason, detail)
                    }
                }
            )
            _uiState.update { it.copy(isRestoring = false, message = message) }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    data class SettingsUiState(
        val isBackingUp: Boolean = false,
        val isRestoring: Boolean = false,
        val message: String? = null
    )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                SettingsViewModel(application)
            }
        }
    }
}
