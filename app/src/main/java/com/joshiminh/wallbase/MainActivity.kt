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
import androidx.lifecycle.lifecycleScope
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import com.joshiminh.wallbase.data.WallBaseDatabase
import com.joshiminh.wallbase.ui.WallBaseApp
import com.joshiminh.wallbase.ui.theme.WallBaseTheme
import com.joshiminh.wallbase.ui.viewmodel.*
import com.joshiminh.wallbase.util.network.ServiceLocator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ServiceLocator.ensureInitialized(this)
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { WallBaseDatabase.getInstance(applicationContext) }
        }
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .diskCache {
                    DiskCache.Builder()
                        .directory(context.cacheDir.resolve("coil_previews").toOkioPath())
                        .maxSizeBytes(50L * 1024 * 1024)
                        .build()
                }
                .build()
        }

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
                appAccentColor = settingsUiState.appAccentColor,
                customAccentColorRgb = settingsUiState.customAccentColorRgb
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
                    onToggleShowHorizontalWallpapers = settingsViewModel::setShowHorizontalWallpapers,
                    onShowSettingsMessage = settingsViewModel::showMessage,
                    onCompleteOnboarding = settingsViewModel::markOnboardingComplete,
                )
            }
        }
    }
}