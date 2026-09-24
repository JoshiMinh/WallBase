package com.joshiminh.wallbase.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import coil3.compose.AsyncImage
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joshiminh.wallbase.R
import com.joshiminh.wallbase.data.entity.CategoryItem
import com.joshiminh.wallbase.data.repository.AppAccentColor
import com.joshiminh.wallbase.data.repository.AppTheme
import com.joshiminh.wallbase.ui.components.ManageCategoriesDialog
import com.joshiminh.wallbase.ui.components.bottomBarInsetPadding
import com.joshiminh.wallbase.ui.components.topBarInsetPadding
import com.joshiminh.wallbase.ui.theme.AccentBlue
import com.joshiminh.wallbase.ui.theme.AccentGreen
import com.joshiminh.wallbase.ui.theme.AccentPink
import com.joshiminh.wallbase.ui.theme.AccentRed
import com.joshiminh.wallbase.ui.theme.WallBaseShapes
import com.joshiminh.wallbase.ui.viewmodel.SettingsViewModel
import kotlin.math.roundToInt
import kotlin.system.exitProcess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsViewModel.SettingsUiState,
    onSetAppTheme: (AppTheme) -> Unit,
    onSetAppAccentColor: (AppAccentColor) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onToggleAmoledDark: (Boolean) -> Unit,
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
    onToggleShowHorizontalWallpapers: (Boolean) -> Unit,
    onToggleShowDownloadBadge: (Boolean) -> Unit,
    onToggleCategories: (Boolean) -> Unit = {},
    onSaveSourceCredentials: (String) -> Unit,
    onOpenExtensions: () -> Unit = {},
    onCreateCategory: (String) -> Unit = {},
    onRenameCategory: (CategoryItem, String) -> Unit = { _, _ -> },
    onDeleteCategory: (CategoryItem) -> Unit = {},
    onReorderCategories: (List<Long>) -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onDismissAvailableUpdate: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    var showManageCategoriesDialog by rememberSaveable { mutableStateOf(false) }
    var showAppearanceSheet by rememberSaveable { mutableStateOf(false) }
    var showDataStorageSheet by rememberSaveable { mutableStateOf(false) }
    var showSourceConnectionDialog by rememberSaveable { mutableStateOf(false) }
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }
    var showExportConfirmation by rememberSaveable { mutableStateOf(false) }
    var showClearOriginalsConfirmation by rememberSaveable { mutableStateOf(false) }

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
                top = topBarInsetPadding(8.dp, hasTabBar = false),
                end = 16.dp,
                bottom = bottomBarInsetPadding(24.dp, hasBottomNav = true)
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top App Branding Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = R.mipmap.ic_launcher_round,
                        contentDescription = "WallBase Icon",
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(20.dp)
                            )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "WallBase",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "v6.5",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
            }

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

            // Navigation Rows (Tachiyomi / Mihon style list)
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
                            onClick = { showManageCategoriesDialog = true }
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
                            onClick = { showAppearanceSheet = true }
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
                            onClick = { showDataStorageSheet = true }
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

                        // Source connection
                        SettingsNavRow(
                            icon = Icons.Outlined.VpnKey,
                            title = "Source connection",
                            subtitle = if (uiState.wallhavenTokenConfigured) "Wallhaven API token configured" else "Set up API tokens for Wallhaven",
                            onClick = { showSourceConnectionDialog = true }
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
                                uiState.availableUpdateVersion != null -> "Update v${uiState.availableUpdateVersion} available!"
                                uiState.hasCheckedForUpdates -> "You are on the latest version (v6.5)"
                                else -> "Check for new releases on GitHub"
                            },
                            isLoading = uiState.isCheckingForUpdates,
                            onClick = onCheckForUpdates
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        // About
                        SettingsNavRow(
                            icon = Icons.Outlined.Info,
                            title = "About",
                            subtitle = "WallBase v6.5 • Open-source wallpaper manager",
                            onClick = { showAboutDialog = true }
                        )
                    }
                }
            }
        }
    }

    // Manage Categories Dialog
    if (showManageCategoriesDialog) {
        ManageCategoriesDialog(
            categories = uiState.categories,
            onCreateCategory = onCreateCategory,
            onRenameCategory = onRenameCategory,
            onDeleteCategory = onDeleteCategory,
            onReorderCategories = onReorderCategories,
            onDismiss = { showManageCategoriesDialog = false }
        )
    }

    // Appearance Bottom Sheet
    if (showAppearanceSheet) {
        AppearanceBottomSheet(
            uiState = uiState,
            onSetAppTheme = onSetAppTheme,
            onSetAppAccentColor = onSetAppAccentColor,
            onToggleDynamicColor = onToggleDynamicColor,
            onToggleAmoledDark = onToggleAmoledDark,
            onToggleAnimations = onToggleAnimations,
            onToggleShowHorizontalWallpapers = onToggleShowHorizontalWallpapers,
            onToggleShowDownloadBadge = onToggleShowDownloadBadge,
            onDismiss = { showAppearanceSheet = false }
        )
    }

    // Data and Storage Bottom Sheet
    if (showDataStorageSheet) {
        DataAndStorageBottomSheet(
            uiState = uiState,
            context = context,
            onUpdateStorageLimit = onUpdateStorageLimit,
            onClearPreviewCache = onClearPreviewCache,
            onRequestClearOriginals = { showClearOriginalsConfirmation = true },
            onToggleIncludeSourcesInBackup = onToggleIncludeSourcesInBackup,
            onRequestExportBackup = { showExportConfirmation = true },
            onImportBackup = onImportBackup,
            onDismiss = { showDataStorageSheet = false }
        )
    }

    // Source Connection Dialog
    if (showSourceConnectionDialog) {
        SourceCredentialsDialog(
            isConfigured = uiState.wallhavenTokenConfigured,
            onSave = onSaveSourceCredentials,
            onDismiss = { showSourceConnectionDialog = false }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AboutDialog(
            onOpenGithub = { uriHandler.openUri("https://github.com/JoshiMinh/WallBase") },
            onDismiss = { showAboutDialog = false }
        )
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

    // Export Confirmation Dialog
    if (showExportConfirmation) {
        AlertDialog(
            onDismissRequest = { showExportConfirmation = false },
            title = { Text(text = "Export Backup") },
            text = {
                Text(
                    text = "A backup file containing your favorites, custom albums, source list, and preferences will be generated."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showExportConfirmation = false
                    onExportBackup(uiState.includeSourcesInBackup)
                }) {
                    Text("Export")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Remove Downloads Confirmation Dialog
    if (showClearOriginalsConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearOriginalsConfirmation = false },
            title = { Text(text = "Remove All Downloads?") },
            text = {
                Text(
                    text = "This will delete all saved high-resolution wallpaper files from local storage. Your library entries and bookmarks will remain intact."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearOriginalsConfirmation = false
                        onClearOriginals()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Remove All", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearOriginalsConfirmation = false }) {
                    Text("Cancel")
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
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
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
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceBottomSheet(
    uiState: SettingsViewModel.SettingsUiState,
    onSetAppTheme: (AppTheme) -> Unit,
    onSetAppAccentColor: (AppAccentColor) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onToggleAmoledDark: (Boolean) -> Unit,
    onToggleAnimations: (Boolean) -> Unit,
    onToggleShowHorizontalWallpapers: (Boolean) -> Unit,
    onToggleShowDownloadBadge: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Outlined.Close, contentDescription = "Close")
                }
            }

            // Theme Selection (System, Light, Dark)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ThemeOptionCard(
                        icon = Icons.Outlined.BrightnessAuto,
                        label = "System",
                        selected = uiState.appTheme == AppTheme.SYSTEM,
                        onClick = { onSetAppTheme(AppTheme.SYSTEM) },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeOptionCard(
                        icon = Icons.Outlined.LightMode,
                        label = "Light",
                        selected = uiState.appTheme == AppTheme.LIGHT,
                        onClick = { onSetAppTheme(AppTheme.LIGHT) },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeOptionCard(
                        icon = Icons.Outlined.DarkMode,
                        label = "Dark",
                        selected = uiState.appTheme == AppTheme.DARK,
                        onClick = { onSetAppTheme(AppTheme.DARK) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Dynamic Color (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleDynamicColor(!uiState.dynamicColor) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dynamic color",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Extract theme colors from your device wallpaper",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.dynamicColor,
                        onCheckedChange = onToggleDynamicColor
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }

            // Accent Color Palette
            if (!uiState.dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Accent Color",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        AccentColorCircle(
                            color = AccentPink,
                            label = "Pink",
                            selected = uiState.appAccentColor == AppAccentColor.PINK,
                            onClick = { onSetAppAccentColor(AppAccentColor.PINK) }
                        )
                        AccentColorCircle(
                            color = AccentRed,
                            label = "Red",
                            selected = uiState.appAccentColor == AppAccentColor.RED,
                            onClick = { onSetAppAccentColor(AppAccentColor.RED) }
                        )
                        AccentColorCircle(
                            color = AccentBlue,
                            label = "Blue",
                            selected = uiState.appAccentColor == AppAccentColor.BLUE,
                            onClick = { onSetAppAccentColor(AppAccentColor.BLUE) }
                        )
                        AccentColorCircle(
                            color = AccentGreen,
                            label = "Green",
                            selected = uiState.appAccentColor == AppAccentColor.GREEN,
                            onClick = { onSetAppAccentColor(AppAccentColor.GREEN) }
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }

            // Other Appearance Toggles
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // AMOLED Dark
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleAmoledDark(!uiState.amoledDark) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pure Black (AMOLED)",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Use deep black backgrounds to conserve battery",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.amoledDark,
                        onCheckedChange = onToggleAmoledDark
                    )
                }

                // UI Animations
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleAnimations(!uiState.animationsEnabled) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "UI Animations",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Smooth transitions and shared element zooms",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.animationsEnabled,
                        onCheckedChange = onToggleAnimations
                    )
                }

                // Phone-only Wallpapers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleShowHorizontalWallpapers(uiState.showHorizontalWallpapers) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show only phone wallpapers",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Hide landscape wallpapers not intended for phones",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = !uiState.showHorizontalWallpapers,
                        onCheckedChange = { onToggleShowHorizontalWallpapers(!it) }
                    )
                }

                // Download badge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleShowDownloadBadge(!uiState.showDownloadBadge) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show download badge",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Display downloaded indicator icon on saved items",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.showDownloadBadge,
                        onCheckedChange = onToggleShowDownloadBadge
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeOptionCard(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun AccentColorCircle(
    color: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataAndStorageBottomSheet(
    uiState: SettingsViewModel.SettingsUiState,
    context: Context,
    onUpdateStorageLimit: (Long) -> Unit,
    onClearPreviewCache: () -> Unit,
    onRequestClearOriginals: () -> Unit,
    onToggleIncludeSourcesInBackup: (Boolean) -> Unit,
    onRequestExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val oneGbBytes = 1024L * 1024L * 1024L
    var storageSliderValue by remember(uiState.storageLimitBytes) {
        mutableFloatStateOf(uiState.storageLimitBytes.toFloat() / oneGbBytes.toFloat())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Data and storage",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Outlined.Close, contentDescription = "Close")
                }
            }

            // Storage Breakdown Card
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val usedBytes = uiState.storageBytes ?: 0L
                    val totalBytes = uiState.storageTotalBytes ?: 1L
                    val progress = (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Storage Usage",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = Formatter.formatFileSize(context, usedBytes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StorageStatItem(
                            label = "Downloads",
                            size = Formatter.formatFileSize(context, uiState.wallpapersBytes ?: 0L)
                        )
                        StorageStatItem(
                            label = "Cache",
                            size = Formatter.formatFileSize(context, uiState.previewCacheBytes ?: 0L)
                        )
                        StorageStatItem(
                            label = "Total Capacity",
                            size = Formatter.formatFileSize(context, totalBytes)
                        )
                    }
                }
            }

            // Storage Limit Slider
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val limitLabel = if (storageSliderValue <= 0f) "No limit" else "${(storageSliderValue * 2f).roundToInt() / 2f} GB"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Storage limit",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = limitLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = storageSliderValue.coerceIn(0f, 10f),
                    onValueChange = { storageSliderValue = it },
                    valueRange = 0f..10f,
                    steps = 19,
                    onValueChangeFinished = {
                        val clamped = (storageSliderValue * 2f).roundToInt() / 2f
                        val bytes = (clamped.toDouble() * oneGbBytes.toDouble()).toLong()
                        onUpdateStorageLimit(bytes)
                    }
                )
                Text(
                    text = if (storageSliderValue <= 0f) {
                        "Downloads will use storage until manual cleanup."
                    } else {
                        "Cap downloaded wallpaper files to approximately $limitLabel."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Cache & Storage Cleanup Actions
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Cleanup Actions",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onClearPreviewCache,
                        enabled = !uiState.isClearingPreviews,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (uiState.isClearingPreviews) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.CleaningServices,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear cache")
                        }
                    }

                    Button(
                        onClick = onRequestClearOriginals,
                        enabled = !uiState.isClearingOriginals,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        if (uiState.isClearingOriginals) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete files")
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Backup & Restore
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Backup & Restore",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleIncludeSourcesInBackup(!uiState.includeSourcesInBackup) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Include sources in backup",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Save custom scrapers & source credentials",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.includeSourcesInBackup,
                        onCheckedChange = onToggleIncludeSourcesInBackup
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onRequestExportBackup,
                        enabled = !uiState.isBackingUp && !uiState.isRestoring,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (uiState.isBackingUp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.FileUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export")
                        }
                    }

                    OutlinedButton(
                        onClick = onImportBackup,
                        enabled = !uiState.isRestoring && !uiState.isBackingUp,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (uiState.isRestoring) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageStatItem(
    label: String,
    size: String
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = size,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SourceCredentialsDialog(
    isConfigured: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var wallhavenKey by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.VpnKey,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(text = "Wallhaven API Token")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter your personal Wallhaven API key to unlock NSFW/sketchy feeds, custom collections, and higher rate limits.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = wallhavenKey,
                    onValueChange = { wallhavenKey = it },
                    label = { Text("API Key") },
                    placeholder = { Text(if (isConfigured) "••••••••••••••••" else "Enter API key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = WallBaseShapes.control
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(wallhavenKey)
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AboutDialog(
    onOpenGithub: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = R.mipmap.ic_launcher_round,
                    contentDescription = "WallBase Icon",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "WallBase",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Version 6.5",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "A modern, high-performance Android wallpaper discovery & management application built with 100% Jetpack Compose and Material You.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onOpenGithub,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("GitHub Repository")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
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
