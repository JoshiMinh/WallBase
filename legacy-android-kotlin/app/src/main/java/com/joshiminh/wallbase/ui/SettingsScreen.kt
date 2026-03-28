package com.joshiminh.wallbase.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.joshiminh.wallbase.ui.viewmodel.SettingsViewModel
import kotlin.math.roundToInt
import kotlin.system.exitProcess

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    uiState: SettingsViewModel.SettingsUiState,
    onToggleDarkTheme: (Boolean) -> Unit,
    onToggleAnimations: (Boolean) -> Unit,
    onExportBackup: (Boolean) -> Unit,
    onImportBackup: () -> Unit,
    onMessageShown: () -> Unit,
    onRestartConsumed: () -> Unit,
    onToggleAutoDownload: (Boolean) -> Unit,
    onUpdateStorageLimit: (Long) -> Unit,
    onClearPreviewCache: () -> Unit,
    onClearOriginals: () -> Unit,
    onToggleIncludeSourcesInBackup: (Boolean) -> Unit,
    onRequestAppLockChange: (Boolean) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val oneGbBytes = 1024L * 1024L * 1024L
    var storageSliderValue by remember {
        mutableFloatStateOf(uiState.storageLimitBytes.toFloat() / oneGbBytes.toFloat())
    }

    LaunchedEffect(uiState.storageLimitBytes) {
        storageSliderValue = uiState.storageLimitBytes.toFloat() / oneGbBytes.toFloat()
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
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets(left = 0.dp, top = 0.dp, right = 0.dp, bottom = 0.dp)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SettingsSection(spacing = 8.dp) {
                    Text(
                        text = "Appearance",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SettingsCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SettingsToggleRow(
                                title = "Dark mode",
                                subtitle = "Use a dark theme throughout the app.",
                                checked = uiState.darkTheme,
                                onCheckedChange = onToggleDarkTheme
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            SettingsToggleRow(
                                title = "Enable animations",
                                subtitle = "Show transitions when navigating between screens.",
                                checked = uiState.animationsEnabled,
                                onCheckedChange = onToggleAnimations
                            )
                        }
                    }
                }
            }

            item {
                SettingsSection(spacing = 8.dp) {
                    Text(
                        text = "Data & backup",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SettingsCard {
                        val storageBytes = uiState.storageBytes
                        val storageTotalBytes = uiState.storageTotalBytes
                        val hasUsage = storageBytes != null && storageTotalBytes != null && storageTotalBytes > 0L
                        val limitLabel = if (uiState.storageLimitBytes > 0) {
                            Formatter.formatFileSize(context, uiState.storageLimitBytes)
                        } else {
                            "No limit"
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Downloads & storage",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Control automatic downloads and cached files.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Outlined.Download, contentDescription = null)
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "Auto-download",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Download originals when you save wallpapers.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = uiState.autoDownload,
                                    onCheckedChange = onToggleAutoDownload
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Outlined.Storage, contentDescription = null)
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "Storage usage",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "See how much space downloads and previews use.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                StorageUsageContent(
                                    hasUsage = hasUsage,
                                    storageBytes = storageBytes,
                                    storageTotalBytes = storageTotalBytes,
                                    wallpapersBytes = uiState.wallpapersBytes,
                                    previewBytes = uiState.previewCacheBytes,
                                    isLoading = uiState.isStorageLoading,
                                    context = context
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Outlined.Storage, contentDescription = null)
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "Storage limit",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "Current cap: $limitLabel",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
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

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Outlined.CleaningServices, contentDescription = null)
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "Cache actions",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "Quickly clear cached wallpaper files.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    maxItemsInEachRow = 2
                                ) {
                                    CacheActionButton(
                                        modifier = Modifier.weight(1f),
                                        text = "Delete previews",
                                        icon = Icons.Outlined.Image,
                                        onClick = onClearPreviewCache,
                                        enabled = !uiState.isClearingPreviews,
                                        showProgress = uiState.isClearingPreviews
                                    )
                                    CacheActionButton(
                                        modifier = Modifier.weight(1f),
                                        text = "Delete originals",
                                        icon = Icons.Outlined.Storage,
                                        onClick = onClearOriginals,
                                        enabled = !uiState.isClearingOriginals,
                                        showProgress = uiState.isClearingOriginals
                                    )
                                }
                            }
                        }
                    }

                    SettingsCard {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                                        text = "Save your library and albums as a backup file. " +
                                            "Optionally include downloaded sources.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                SettingsActionButton(
                                    text = if (uiState.isBackingUp) "Exporting…" else "Export",
                                    enabled = !uiState.isBackingUp && !uiState.isRestoring,
                                    showProgress = uiState.isBackingUp,
                                    onClick = { onExportBackup(uiState.includeSourcesInBackup) }
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Include source files",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "Attach downloaded wallpapers to the backup file.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = uiState.includeSourcesInBackup,
                                    onCheckedChange = onToggleIncludeSourcesInBackup,
                                    enabled = !uiState.isBackingUp && !uiState.isRestoring
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                thickness = DividerDefaults.Thickness, color = MaterialTheme.colorScheme.outlineVariant
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
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

            item {
                SettingsSection(spacing = 8.dp) {
                    Text(
                        text = "Privacy & security",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SettingsCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Outlined.Lock, contentDescription = null)
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "App lock",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Require your device screen lock before opening WallBase. Rotations keep running.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.appLockEnabled,
                                onCheckedChange = onRequestAppLockChange
                            )
                        }
                    }
                }
            }

            item {
                SettingsSection(spacing = 8.dp) {
                    Text(
                        text = "About",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SettingsLinkCard(
                        title = "Ko-fi",
                        description = "Support future development.",
                        iconUrl = "https://ko-fi.com/favicon.ico",
                        onClick = { uriHandler.openUri("https://ko-fi.com/joshiminh") },
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFF5E5B),
                            contentColor = Color.White
                        )
                    )
                }
            }

        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
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
private fun SettingsCard(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick != null) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = colors,
            onClick = onClick,
            content = content
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = colors,
            content = content
        )
    }
}

@Composable
private fun SettingsLinkCard(
    title: String,
    description: String,
    iconUrl: String,
    onClick: () -> Unit,
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
) {
    SettingsCard(colors = colors, onClick = onClick) {
        val contentColor = LocalContentColor.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = iconUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
            Icon(imageVector = Icons.Outlined.OpenInNew, contentDescription = null)
        }
    }
}

@Composable
private fun SettingsListItem(
    headline: String,
    supporting: String? = null,
    leadingIcon: ImageVector? = null,
    supportingIcon: ImageVector? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    ListItem(
        colors = colors,
        leadingContent = {
            when {
                leadingIcon != null -> Icon(imageVector = leadingIcon, contentDescription = null)
                supportingIcon != null -> Icon(imageVector = supportingIcon, contentDescription = null)
            }
        },
        headlineContent = { Text(headline, style = MaterialTheme.typography.titleSmall) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                supporting?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                content()
            }
        },
        trailingContent = trailingContent
    )
}

@Composable
private fun StorageUsageContent(
    hasUsage: Boolean,
    storageBytes: Long?,
    storageTotalBytes: Long?,
    wallpapersBytes: Long?,
    previewBytes: Long?,
    isLoading: Boolean,
    context: Context
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            hasUsage && storageBytes != null && storageTotalBytes != null -> {
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            isLoading -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = "Calculating storage usage…",
                    style = MaterialTheme.typography.bodySmall,
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        wallpapersBytes?.let { wallpapers ->
            val wallpapersSize = Formatter.formatFileSize(context, wallpapers)
            Text(
                text = "Wallpapers: $wallpapersSize",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        previewBytes?.let { previews ->
            val previewSize = Formatter.formatFileSize(context, previews)
            Text(
                text = "Previews: $previewSize",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CacheActionButton(
    modifier: Modifier = Modifier,
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
    showProgress: Boolean
) {
    FilledTonalButton(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    ) {
        if (showProgress) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(16.dp)
                    .padding(end = 8.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 8.dp)
            )
        }
        Text(text = text)
    }
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
                    .size(16.dp)
                    .padding(end = 6.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        Text(text = text)
    }
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