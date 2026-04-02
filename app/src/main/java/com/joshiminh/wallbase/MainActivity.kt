@file:Suppress("DEPRECATION", "unused")

package com.joshiminh.wallbase

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joshiminh.wallbase.ui.theme.WallBaseTheme
import com.joshiminh.wallbase.ui.viewmodel.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.joshiminh.wallbase.ui.WallBaseApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val sourcesViewModel: SourcesViewModel = viewModel(factory = SourcesViewModel.Factory)
            val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)

            val sourcesUiState by sourcesViewModel.uiState.collectAsStateWithLifecycle()
            val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()

            var pendingIncludeSources by remember { mutableStateOf(true) }

            val backupExporter = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
            ) { uri ->
                if (uri != null) settingsViewModel.exportBackup(uri, pendingIncludeSources)
            }

            val backupImporter = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri != null) settingsViewModel.importBackup(uri)
            }

            val backupFileFormatter = remember { SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US) }

            WallBaseTheme(
                appTheme = settingsUiState.appTheme,
                appAccentColor = settingsUiState.appAccentColor
            ) {
                WallBaseApp(
                    sourcesUiState = sourcesUiState,
                    settingsUiState = settingsUiState,
                    onSetAppTheme = settingsViewModel::setAppTheme,
                    onSetAppAccentColor = settingsViewModel::setAppAccentColor,
                    onToggleAnimations = settingsViewModel::setAnimationsEnabled,
                    onUpdateSourceInput = sourcesViewModel::updateSourceInput,
                    onSearchReddit = sourcesViewModel::searchRedditCommunities,
                    onAddSourceFromInput = sourcesViewModel::addSourceFromInput,
                    onQuickAddSource = sourcesViewModel::quickAddSource,
                    onAddRedditCommunity = sourcesViewModel::addRedditCommunity,
                    onClearRedditSearch = sourcesViewModel::clearSearchResults,
                    onRemoveSource = sourcesViewModel::removeSource,
                    onSourcesMessageShown = sourcesViewModel::consumeMessage,
                    onSourceUrlCopied = sourcesViewModel::onSourceUrlCopied,
                    onExportBackup = { includeSources ->
                        pendingIncludeSources = includeSources
                        val timestamp = backupFileFormatter.format(Date())
                        backupExporter.launch("wallbase-backup-$timestamp.wbbackup")
                    },
                    onImportBackup = {
                        backupImporter.launch(
                            arrayOf(
                                "application/zip",
                                "application/octet-stream",
                                "application/x-sqlite3",
                                "application/vnd.sqlite3",
                            ),
                        )
                    },
                    onSettingsMessageShown = settingsViewModel::consumeMessage,
                    onSettingsRestartConsumed = settingsViewModel::consumeRestartRequest,
                    onToggleAutoDownload = settingsViewModel::setAutoDownload,
                    onUpdateStorageLimit = settingsViewModel::setStorageLimit,
                    onClearPreviewCache = settingsViewModel::clearPreviewCache,
                    onClearOriginals = settingsViewModel::clearOriginalDownloads,
                    onToggleIncludeSourcesInBackup = settingsViewModel::setIncludeSourcesInBackup,
                    onSetAppLockEnabled = settingsViewModel::setAppLockEnabled,
                    onShowSettingsMessage = settingsViewModel::showMessage,
                    onCompleteOnboarding = settingsViewModel::markOnboardingComplete,
                )
            }
        }
    }
}

