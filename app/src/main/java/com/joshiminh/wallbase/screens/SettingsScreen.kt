package com.joshiminh.wallbase.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.joshiminh.wallbase.BuildConfig
import com.joshiminh.wallbase.R
import com.joshiminh.wallbase.ui.components.MinResolutionDialog
import com.joshiminh.wallbase.ui.components.bottomBarInsetPadding
import com.joshiminh.wallbase.ui.components.topBarInsetPadding
import com.joshiminh.wallbase.ui.theme.WallBaseShapes
import com.joshiminh.wallbase.ui.viewmodel.SettingsViewModel
import com.joshiminh.wallbase.util.MinResolution
import java.util.Locale
import kotlin.system.exitProcess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsViewModel.SettingsUiState,
    onRequestAppLockChange: (Boolean) -> Unit,
    onToggleAutoDownload: (Boolean) -> Unit,
    onSetMinResolution: (MinResolution) -> Unit = {},
    onOpenAppearance: () -> Unit,
    onOpenDataStorage: () -> Unit,
    onOpenExtensions: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onShowUpdateDialog: () -> Unit = {},
    onDismissUpdateDialog: (() -> Unit)? = null,
    onDismissAvailableUpdate: () -> Unit,
    onStartUpdateDownloadAndInstall: () -> Unit = {},
    onCancelUpdateDownload: () -> Unit = {},
    onInstallDownloadedApk: () -> Unit = {},
    onClearUpdateDownloadError: () -> Unit = {},
    onMessageShown: () -> Unit,
    onRestartConsumed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var showMinResolutionDialog by remember { mutableStateOf(false) }

    if (showMinResolutionDialog) {
        MinResolutionDialog(
            currentResolution = uiState.minResolution,
            onSelectResolution = onSetMinResolution,
            onDismiss = { showMinResolutionDialog = false }
        )
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onMessageShown()
    }

    LaunchedEffect(uiState.shouldRestartAfterImport) {
        if (!uiState.shouldRestartAfterImport) return@LaunchedEffect
        val activity = context.findActivity()
        if (activity == null) {
            onRestartConsumed()
            return@LaunchedEffect
        }
        restartApplication(activity, onRestartConsumed)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 80.dp)
            )
        },
        contentWindowInsets = WindowInsets(left = 0.dp, top = 0.dp, right = 0.dp, bottom = 0.dp)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = topBarInsetPadding(8.dp),
                end = 16.dp,
                bottom = bottomBarInsetPadding(32.dp, hasBottomNav = true)
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Quick Switches Card
            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = WallBaseShapes.card,
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    )
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        QuickToggleRow(
                            icon = Icons.Outlined.Lock,
                            title = "App lock",
                            subtitle = "Require biometric or PIN to unlock",
                            checked = uiState.appLockEnabled,
                            onCheckedChange = onRequestAppLockChange
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        QuickToggleRow(
                            icon = Icons.Outlined.Download,
                            title = "Auto download",
                            subtitle = "Save original resolution when viewing",
                            checked = uiState.autoDownload,
                            onCheckedChange = onToggleAutoDownload
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        SettingsNavRow(
                            icon = Icons.Outlined.HighQuality,
                            title = "Minimum resolution",
                            subtitle = uiState.minResolution.label,
                            onClick = { showMinResolutionDialog = true }
                        )
                    }
                }
            }

            // Navigation Rows Card
            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = WallBaseShapes.card,
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    )
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Appearance
                        SettingsNavRow(
                            icon = Icons.Outlined.Palette,
                            title = "Appearance",
                            subtitle = "Theme, accent color, AMOLED dark, animations",
                            onClick = onOpenAppearance
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        // Data and storage
                        SettingsNavRow(
                            icon = Icons.Outlined.Storage,
                            title = "Data and storage",
                            subtitle = "Storage limit, preview cache, downloads, backup",
                            onClick = onOpenDataStorage
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        // Extensions
                        SettingsNavRow(
                            icon = Icons.Outlined.Extension,
                            title = "Extensions",
                            subtitle = "Scraper extensions and repositories",
                            onClick = onOpenExtensions
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        // Check for updates
                        SettingsNavRow(
                            icon = Icons.Outlined.SystemUpdate,
                            title = "Check for updates",
                            subtitle = when {
                                uiState.isCheckingForUpdates -> "Checking GitHub releases…"
                                uiState.isDownloadingUpdate -> {
                                    val pct = uiState.updateDownloadProgress?.let { " (${(it * 100).toInt()}%)" } ?: ""
                                    "Downloading update$pct…"
                                }
                                uiState.downloadedApkFile != null && uiState.downloadedApkFile.exists() ->
                                    "Update v${uiState.availableUpdateVersion} downloaded. Tap to install"
                                uiState.updateDownloadError != null ->
                                    "Update failed. Tap to retry or download"
                                uiState.availableUpdateVersion != null ->
                                    "Update v${uiState.availableUpdateVersion} available! Tap to install"
                                uiState.updateError != null -> "Check failed. Tap to retry"
                                uiState.hasCheckedForUpdates -> "Up to date (v${BuildConfig.VERSION_NAME})"
                                else -> "Check for new releases on GitHub"
                            },
                            isLoading = uiState.isCheckingForUpdates || uiState.isDownloadingUpdate,
                            onClick = {
                                if (uiState.availableUpdateVersion != null || uiState.updateDownloadError != null) {
                                    onShowUpdateDialog()
                                } else {
                                    onCheckForUpdates()
                                }
                            }
                        )
                    }
                }
            }

            // Neutral About Section Footer
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "WallBase v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                    Text(
                        text = "Open-source wallpaper manager",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        textAlign = TextAlign.Center
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "GitHub",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            modifier = Modifier.clickable {
                                uriHandler.openUri("https://github.com/JoshiMinh/WallBase")
                            }
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            text = "Support on Ko-fi",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            modifier = Modifier.clickable {
                                uriHandler.openUri("https://ko-fi.com/joshiminh")
                            }
                        )
                    }
                }
            }
        }
    }

    // Update Available Dialog
    val handleDismissDialog = onDismissUpdateDialog ?: onDismissAvailableUpdate
    val browserUrl = uiState.releasePageUrl ?: uiState.updateUrl ?: "https://github.com/JoshiMinh/WallBase/releases"
    if (uiState.showUpdateDialog && uiState.availableUpdateVersion != null) {
        when {
            // State 1: Download in progress
            uiState.isDownloadingUpdate -> {
                AlertDialog(
                    onDismissRequest = onCancelUpdateDownload,
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.SystemUpdate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    },
                    title = {
                        Text(text = "Downloading Update (v${uiState.availableUpdateVersion})")
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Downloading the latest release APK...",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            val progress = uiState.updateDownloadProgress
                            if (progress != null) {
                                LinearProgressIndicator(
                                    progress = { progress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                val downloadedMb = uiState.updateDownloadBytes / (1024.0 * 1024.0)
                                val totalMb = uiState.updateDownloadTotalBytes / (1024.0 * 1024.0)
                                val percent = (progress * 100).toInt()
                                val progressLabel = if (uiState.updateDownloadTotalBytes > 0L) {
                                    String.format(Locale.US, "%d%% (%.1f MB / %.1f MB)", percent, downloadedMb, totalMb)
                                } else {
                                    String.format(Locale.US, "%.1f MB downloaded", downloadedMb)
                                }
                                Text(
                                    text = progressLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(
                                    text = "Connecting to server...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                uriHandler.openUri(browserUrl)
                                onCancelUpdateDownload()
                                handleDismissDialog()
                            }
                        ) {
                            Text("Open in Browser")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = onCancelUpdateDownload) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // State 2: Download or Installation Error
            uiState.updateDownloadError != null -> {
                AlertDialog(
                    onDismissRequest = {
                        onClearUpdateDownloadError()
                        handleDismissDialog()
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(32.dp)
                        )
                    },
                    title = {
                        Text(text = "Update Failed")
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = uiState.updateDownloadError,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "You can retry downloading automatically, or open GitHub in your web browser to download the APK directly.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    confirmButton = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onClearUpdateDownloadError()
                                    onStartUpdateDownloadAndInstall()
                                }
                            ) {
                                Text("Retry")
                            }
                            Button(
                                onClick = {
                                    uriHandler.openUri(browserUrl)
                                    onClearUpdateDownloadError()
                                    handleDismissDialog()
                                }
                            ) {
                                Text("Browser")
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                onClearUpdateDownloadError()
                                handleDismissDialog()
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // State 3: Download Complete & Ready to Install
            uiState.downloadedApkFile != null && uiState.downloadedApkFile.exists() -> {
                AlertDialog(
                    onDismissRequest = handleDismissDialog,
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    },
                    title = {
                        Text(text = "Ready to Install (v${uiState.availableUpdateVersion})")
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "The update has been downloaded successfully.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Tap Install Now to start the system package installer. If prompted, make sure to allow installing unknown apps from WallBase.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    confirmButton = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    uriHandler.openUri(browserUrl)
                                    handleDismissDialog()
                                }
                            ) {
                                Text("Browser")
                            }
                            Button(
                                onClick = {
                                    onInstallDownloadedApk()
                                }
                            ) {
                                Text("Install Now")
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = handleDismissDialog) {
                            Text("Later")
                        }
                    }
                )
            }

            // State 4: Default - Update Available prompt
            else -> {
                Dialog(onDismissRequest = handleDismissDialog) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 440.dp),
                        shape = WallBaseShapes.dialog,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 6.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.SystemUpdate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(12.dp).size(26.dp)
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Update available",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "WallBase v${uiState.availableUpdateVersion}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Text(
                                text = "A new version is ready to install.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (!uiState.updateNotes.isNullOrBlank()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = WallBaseShapes.control,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(160.dp)
                                            .verticalScroll(rememberScrollState())
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "WHAT'S NEW",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = uiState.updateNotes,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = onStartUpdateDownloadAndInstall,
                                modifier = Modifier.fillMaxWidth(),
                                shape = WallBaseShapes.pill
                            ) {
                                Icon(Icons.Outlined.Download, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("Download & install")
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = {
                                    uriHandler.openUri(browserUrl)
                                    handleDismissDialog()
                                }) {
                                    Text("View release")
                                }
                                TextButton(onClick = handleDismissDialog) {
                                    Text("Later")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun restartApplication(activity: Activity, onRestartConsumed: () -> Unit) {
    val restartIntent = Intent.makeRestartActivityTask(activity.componentName)
    activity.startActivity(restartIntent)
    onRestartConsumed()
    exitProcess(0)
}
