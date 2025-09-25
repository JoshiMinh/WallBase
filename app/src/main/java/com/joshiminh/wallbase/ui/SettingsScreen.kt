package com.joshiminh.wallbase.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joshiminh.wallbase.ui.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    uiState: SettingsViewModel.SettingsUiState,
    onToggleDarkTheme: (Boolean) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onMessageShown: () -> Unit,
    onToggleAutoDownload: (Boolean) -> Unit,
    onUpdateStorageLimit: (Long) -> Unit,
    onClearPreviewCache: () -> Unit,
    onClearOriginals: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val oneGbBytes = 1024L * 1024L * 1024L
    var storageSliderValue by remember {
        mutableStateOf(uiState.storageLimitBytes.toFloat() / oneGbBytes.toFloat())
    }

    LaunchedEffect(uiState.storageLimitBytes) {
        storageSliderValue = uiState.storageLimitBytes.toFloat() / oneGbBytes.toFloat()
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onMessageShown()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets(left = 0.dp, top = 0.dp, right = 0.dp, bottom = 0.dp)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                SettingsSection(spacing = 12.dp) {
                    Text(
                        text = "Appearance",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SettingsCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Dark mode",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Use a dark theme throughout the app.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Switch(
                                checked = uiState.darkTheme,
                                onCheckedChange = onToggleDarkTheme
                            )
                        }
                    }
                }
            }

            item {
                SettingsSection(spacing = 12.dp) {
                    Text(
                        text = "Data & backup",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SettingsCard {
                        val storageBytes = uiState.storageBytes
                        val storageTotalBytes = uiState.storageTotalBytes
                        val hasUsage = storageBytes != null && storageTotalBytes != null && storageTotalBytes > 0L
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Storage usage",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "See how much space WallBase takes on this device.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            when {
                                hasUsage -> {
                                    val used = Formatter.formatFileSize(context, storageBytes)
                                    val total = Formatter.formatFileSize(context, storageTotalBytes)
                                    val progress = (storageBytes.toDouble() / storageTotalBytes.toDouble())
                                        .toFloat()
                                        .coerceIn(0f, 1f)

                                    LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = ProgressIndicatorDefaults.linearColor,
                                    trackColor = ProgressIndicatorDefaults.linearTrackColor,
                                    strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                                    )
                                    Text(
                                        text = "$used of $total used",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                uiState.isStorageLoading -> {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    Text(
                                        text = "Calculating storage usage…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                else -> {
                                    LinearProgressIndicator(
                                    progress = { 0f },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = ProgressIndicatorDefaults.linearColor,
                                    trackColor = ProgressIndicatorDefaults.linearTrackColor,
                                    strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                                    )
                                    Text(
                                        text = "Storage usage unavailable",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            uiState.wallpapersBytes?.let { wallpapersBytes ->
                                val wallpapersSize = Formatter.formatFileSize(context, wallpapersBytes)
                                Text(
                                    text = "WallBase wallpapers: $wallpapersSize",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Wallpapers are stored privately inside WallBase's app data.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            uiState.previewCacheBytes?.let { previewBytes ->
                                val previewSize = Formatter.formatFileSize(context, previewBytes)
                                Text(
                                    text = "Preview cache: $previewSize",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    SettingsCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Auto-download",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Automatically download wallpapers when adding them to your library.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = uiState.autoDownload,
                                    onCheckedChange = onToggleAutoDownload
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Storage limit",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                val limitLabel = if (uiState.storageLimitBytes > 0) {
                                    Formatter.formatFileSize(context, uiState.storageLimitBytes)
                                } else {
                                    "No limit"
                                }
                                Text(
                                    text = "Original downloads cap: $limitLabel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Slider(
                                    value = storageSliderValue.coerceIn(0f, 10f),
                                    onValueChange = { storageSliderValue = it },
                                    valueRange = 0f..10f,
                                    steps = 20,
                                    onValueChangeFinished = {
                                        val clamped = (storageSliderValue * 2f).roundToInt() / 2f
                                        val bytes = (clamped.toDouble() * oneGbBytes.toDouble()).toLong()
                                        onUpdateStorageLimit(bytes)
                                    }
                                )
                                Text(
                                    text = if (storageSliderValue <= 0f) {
                                        "Downloads will keep using storage until you clear them."
                                    } else {
                                        "Limit downloads to approximately ${(storageSliderValue * 2f).roundToInt() / 2f} GB."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Quick actions",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = onClearPreviewCache,
                                        enabled = !uiState.isClearingPreviews
                                    ) {
                                        if (uiState.isClearingPreviews) {
                                            CircularProgressIndicator(
                                                modifier = Modifier
                                                    .size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text(text = "Delete previews")
                                    }
                                    Button(
                                        onClick = onClearOriginals,
                                        enabled = !uiState.isClearingOriginals
                                    ) {
                                        if (uiState.isClearingOriginals) {
                                            CircularProgressIndicator(
                                                modifier = Modifier
                                                    .size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text(text = "Delete originals")
                                    }
                                }
                            }
                        }
                    }

                    SettingsCard {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Export library",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Save your sources, library, and albums as a backup file.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                SettingsActionButton(
                                    text = if (uiState.isBackingUp) "Exporting…" else "Export",
                                    enabled = !uiState.isBackingUp && !uiState.isRestoring,
                                    showProgress = uiState.isBackingUp,
                                    onClick = onExportBackup
                                )
                            }

                            Divider(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Import backup",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Restore your sources, library, and albums from a backup file.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                SettingsActionButton(
                                    text = if (uiState.isRestoring) "Importing…" else "Import",
                                    enabled = !uiState.isRestoring && !uiState.isBackingUp,
                                    showProgress = uiState.isRestoring,
                                    onClick = onImportBackup
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    spacing: Dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(spacing), content = content)
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        content = content
    )
}

@Composable
private fun SettingsActionButton(
    text: String,
    enabled: Boolean,
    showProgress: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled
    ) {
        if (showProgress) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 12.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        Text(text = text)
    }
}