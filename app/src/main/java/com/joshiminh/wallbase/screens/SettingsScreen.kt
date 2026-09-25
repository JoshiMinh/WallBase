package com.joshiminh.wallbase.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.joshiminh.wallbase.ui.components.bottomBarInsetPadding
import com.joshiminh.wallbase.ui.components.topBarInsetPadding
import com.joshiminh.wallbase.ui.theme.WallBaseShapes
import com.joshiminh.wallbase.ui.viewmodel.SettingsViewModel
import kotlin.system.exitProcess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsViewModel.SettingsUiState,
    onRequestAppLockChange: (Boolean) -> Unit,
    onToggleCategories: (Boolean) -> Unit,
    onToggleAutoDownload: (Boolean) -> Unit,
    onOpenCategories: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenDataStorage: () -> Unit,
    onOpenExtensions: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onDismissAvailableUpdate: () -> Unit,
    onMessageShown: () -> Unit,
    onRestartConsumed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

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
                            icon = Icons.Outlined.Category,
                            title = "Enable categories",
                            subtitle = "Show category tabs in Library",
                            checked = uiState.categoriesEnabled,
                            onCheckedChange = onToggleCategories
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
                        // Categories
                        SettingsNavRow(
                            icon = Icons.Outlined.Category,
                            title = "Categories",
                            subtitle = if (uiState.categories.isEmpty()) "Manage wallpaper categories" else "${uiState.categories.size} categories configured",
                            onClick = onOpenCategories
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

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
                                uiState.availableUpdateVersion != null -> "Update v${uiState.availableUpdateVersion} available! Tap to install"
                                uiState.updateError != null -> "Check failed. Tap to retry"
                                uiState.hasCheckedForUpdates -> "Up to date (v${BuildConfig.VERSION_NAME})"
                                else -> "Check for new releases on GitHub"
                            },
                            isLoading = uiState.isCheckingForUpdates,
                            onClick = onCheckForUpdates
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
    if (uiState.availableUpdateVersion != null) {
        AlertDialog(
            onDismissRequest = onDismissAvailableUpdate,
            icon = {
                Icon(
                    imageVector = Icons.Outlined.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(text = "Update Available (v${uiState.availableUpdateVersion})")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "A new version of WallBase is ready to install.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!uiState.updateNotes.isNullOrBlank()) {
                        Text(
                            text = uiState.updateNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val url = uiState.updateUrl ?: "https://github.com/JoshiMinh/WallBase/releases"
                    uriHandler.openUri(url)
                    onDismissAvailableUpdate()
                }) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissAvailableUpdate) {
                    Text("Later")
                }
            }
        )
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
