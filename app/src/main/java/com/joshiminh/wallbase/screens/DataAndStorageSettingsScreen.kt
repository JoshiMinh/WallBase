package com.joshiminh.wallbase.screens

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Restore
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joshiminh.wallbase.navigation.TopBarHandle
import com.joshiminh.wallbase.navigation.TopBarState
import com.joshiminh.wallbase.ui.components.bottomBarInsetPadding
import com.joshiminh.wallbase.ui.components.topBarInsetPadding
import com.joshiminh.wallbase.ui.theme.WallBaseShapes
import com.joshiminh.wallbase.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataAndStorageSettingsScreen(
    uiState: SettingsViewModel.SettingsUiState,
    onUpdateStorageLimit: (Long) -> Unit,
    onClearPreviewCache: () -> Unit,
    onClearOriginals: () -> Unit,
    onExportBackup: (Boolean) -> Unit,
    onImportBackup: () -> Unit,
    onToggleIncludeSourcesInBackup: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    onConfigureTopBar: (TopBarState) -> TopBarHandle,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showClearOriginalsDialog by remember { mutableStateOf(false) }

    val topBarState = remember {
        TopBarState(
            title = "Data & storage",
            navigationIcon = TopBarState.NavigationIcon(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onNavigateBack
            )
        )
    }

    val topBarHandleState = remember { mutableStateOf<TopBarHandle?>(null) }
    SideEffect {
        val handle = topBarHandleState.value
        if (handle == null) {
            topBarHandleState.value = onConfigureTopBar(topBarState)
        } else {
            handle.update(topBarState)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            topBarHandleState.value?.clear()
            topBarHandleState.value = null
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(
                top = topBarInsetPadding(8.dp),
                bottom = bottomBarInsetPadding(16.dp, hasBottomNav = false)
            ),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Storage Cache Section
        item {
            Text(
                text = "Cache & Storage",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
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
                    val previewBytes = uiState.previewCacheBytes ?: 0L
                    val formattedCache = Formatter.formatFileSize(context, previewBytes)
                    StorageActionRow(
                        icon = Icons.Outlined.CleaningServices,
                        title = "Clear thumbnail cache",
                        subtitle = "Free up cached previews ($formattedCache)",
                        isLoading = uiState.isClearingPreviews,
                        onClick = onClearPreviewCache
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    val downloadedBytes = uiState.wallpapersBytes ?: 0L
                    val formattedDownloads = Formatter.formatFileSize(context, downloadedBytes)
                    StorageActionRow(
                        icon = Icons.Outlined.DeleteOutline,
                        title = "Delete downloaded originals",
                        subtitle = "Remove full-res offline files ($formattedDownloads)",
                        isLoading = uiState.isClearingOriginals,
                        onClick = { showClearOriginalsDialog = true }
                    )
                }
            }
        }

        // Backup & Restore Section
        item {
            Text(
                text = "Backup & Restore",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
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
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleIncludeSourcesInBackup(!uiState.includeSourcesInBackup) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Include sources in backup",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Save custom feeds and repo configs along with your library",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.includeSourcesInBackup,
                            onCheckedChange = onToggleIncludeSourcesInBackup
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { onExportBackup(uiState.includeSourcesInBackup) },
                            enabled = !uiState.isBackingUp,
                            shape = WallBaseShapes.pill,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (uiState.isBackingUp) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Outlined.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Export")
                            }
                        }

                        OutlinedButton(
                            onClick = onImportBackup,
                            enabled = !uiState.isRestoring,
                            shape = WallBaseShapes.pill,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (uiState.isRestoring) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Import")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearOriginalsDialog) {
        AlertDialog(
            onDismissRequest = { showClearOriginalsDialog = false },
            title = { Text("Delete downloaded originals?") },
            text = { Text("This will delete all saved high-resolution files from device storage. Your favorites and bookmarks will remain intact.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearOriginals()
                        showClearOriginalsDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearOriginalsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StorageActionRow(
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
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, enabled = !isLoading)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    )
}
