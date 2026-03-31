@file:Suppress("DEPRECATION", "unused")

package com.joshiminh.wallbase

import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.*
import com.joshiminh.wallbase.data.entity.Source
import com.joshiminh.wallbase.data.entity.WallpaperItem
import com.joshiminh.wallbase.sources.RedditCommunity
import com.joshiminh.wallbase.ui.*
import com.joshiminh.wallbase.LibraryScreen
import com.joshiminh.wallbase.AlbumRoute
import com.joshiminh.wallbase.ui.theme.WallBaseTheme
import com.joshiminh.wallbase.ui.viewmodel.*
import com.joshiminh.wallbase.data.repository.AppTheme
import com.joshiminh.wallbase.data.repository.AppAccentColor
import com.joshiminh.wallbase.util.network.ServiceLocator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import okio.Path.Companion.toOkioPath


import com.joshiminh.wallbase.ui.WallBaseApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceLocator.ensureInitialized(applicationContext)
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
                    onEditSource = sourcesViewModel::editSource,
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

