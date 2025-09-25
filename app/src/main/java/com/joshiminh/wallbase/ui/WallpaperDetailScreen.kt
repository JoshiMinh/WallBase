package com.joshiminh.wallbase.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.joshiminh.wallbase.data.entity.source.SourceKeys
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem
import com.joshiminh.wallbase.ui.viewmodel.WallpaperDetailViewModel
import com.joshiminh.wallbase.util.wallpapers.WallpaperAdjustments
import com.joshiminh.wallbase.util.wallpapers.WallpaperCrop
import com.joshiminh.wallbase.util.wallpapers.WallpaperFilter
import com.joshiminh.wallbase.util.wallpapers.WallpaperTarget
import kotlin.math.roundToInt

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun WallpaperDetailRoute(
    wallpaper: WallpaperItem,
    onNavigateBack: () -> Unit,
    viewModel: WallpaperDetailViewModel = viewModel(factory = WallpaperDetailViewModel.Factory),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = viewModel::onWallpaperPermissionResult
    )
    var launchedPreview by remember { mutableStateOf<WallpaperDetailViewModel.WallpaperPreviewLaunch?>(null) }
    val previewLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        launchedPreview?.let { preview ->
            viewModel.onPreviewResult(preview, result.resultCode)
            launchedPreview = null
        }
    }

    LaunchedEffect(wallpaper.id) {
        viewModel.setWallpaper(wallpaper)
    }

    LaunchedEffect(uiState.pendingPreview) {
        val preview = uiState.pendingPreview ?: return@LaunchedEffect
        launchedPreview = preview
        try {
            previewLauncher.launch(preview.preview.intent)
        } catch (throwable: Throwable) {
            launchedPreview = null
            viewModel.onPreviewLaunchFailed(preview, throwable)
        } finally {
            viewModel.onPreviewLaunched()
        }
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    WallpaperDetailScreen(
        uiState = uiState,
        onApplyTarget = viewModel::applyWallpaper,
        onConfirmApplyWithoutPreview = viewModel::confirmApplyWithoutPreview,
        onDismissPreviewFallback = viewModel::dismissPreviewFallback,
        onAddToLibrary = viewModel::addToLibrary,
        onRemoveFromLibrary = viewModel::removeFromLibrary,
        onDownload = viewModel::downloadWallpaper,
        onPrepareEditor = viewModel::prepareEditor,
        onRequestRemoveDownload = viewModel::promptRemoveDownload,
        onConfirmRemoveDownload = viewModel::removeDownload,
        onDismissRemoveDownload = viewModel::dismissRemoveDownloadPrompt,
        onAdjustBrightness = viewModel::updateBrightness,
        onSelectFilter = viewModel::updateFilter,
        onSelectCrop = viewModel::updateCrop,
        onResetAdjustments = viewModel::resetAdjustments,
        onRequestPermission = { permissionLauncher.launch(Manifest.permission.SET_WALLPAPER) },
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope
    )
}

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
private fun WallpaperDetailScreen(
    uiState: WallpaperDetailViewModel.WallpaperDetailUiState,
    onApplyTarget: (WallpaperTarget) -> Unit,
    onConfirmApplyWithoutPreview: () -> Unit,
    onDismissPreviewFallback: () -> Unit,
    onAddToLibrary: () -> Unit,
    onRemoveFromLibrary: () -> Unit,
    onDownload: () -> Unit,
    onPrepareEditor: () -> Unit,
    onRequestRemoveDownload: () -> Unit,
    onConfirmRemoveDownload: () -> Unit,
    onDismissRemoveDownload: () -> Unit,
    onAdjustBrightness: (Float) -> Unit,
    onSelectFilter: (WallpaperFilter) -> Unit,
    onSelectCrop: (WallpaperCrop) -> Unit,
    onResetAdjustments: () -> Unit,
    onRequestPermission: () -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?
) {
    val wallpaper = uiState.wallpaper ?: return
    val uriHandler = LocalUriHandler.current
    val canAddToLibrary = wallpaper.sourceKey != null && wallpaper.sourceKey != SourceKeys.LOCAL
    val canRemoveFromLibrary = uiState.isInLibrary && wallpaper.sourceKey != null
    val canDownload = wallpaper.sourceKey != null && wallpaper.sourceKey != SourceKeys.LOCAL
    var showTargetDialog by remember { mutableStateOf(false) }
    val aspectRatio = wallpaper.aspectRatio ?: DEFAULT_DETAIL_ASPECT_RATIO
    val sharedModifier =
        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = wallpaper.transitionKey()),
                    animatedVisibilityScope = animatedVisibilityScope,
                    // pick one:
                    resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds(),
                    // or: resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                    boundsTransform = BoundsTransform { _, _ -> tween(durationMillis = 350) }
                )
            }
        } else {
            Modifier
        }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets(left = 0.dp, top = 0.dp, right = 0.dp, bottom = 0.dp)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
                ) {
                    val previewBitmap = uiState.editedPreview
                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = sharedModifier.then(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(aspectRatio)
                            ),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        AsyncImage(
                            model = wallpaper.previewModel(),
                            contentDescription = null,
                            modifier = sharedModifier.then(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(aspectRatio)
                            ),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = wallpaper.title,
                    style = MaterialTheme.typography.titleLarge
                )

                wallpaper.sourceName?.let { sourceName ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = sourceName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (!uiState.hasWallpaperPermission) {
                    Text(
                        text = "Allow WallBase to open the system wallpaper preview so you can confirm the image.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onRequestPermission) {
                        Text(text = "Grant wallpaper access")
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (uiState.isDownloading) {
                    AssistChip(
                        onClick = {},
                        label = { Text(text = "Downloading wallpaper…") },
                        enabled = false
                    )
                    Spacer(Modifier.height(12.dp))
                }

                if (uiState.isRemovingDownload) {
                    AssistChip(
                        onClick = {},
                        label = { Text(text = "Removing download…") },
                        enabled = false
                    )
                    Spacer(Modifier.height(12.dp))
                }

                if (uiState.isAddingToLibrary) {
                    AssistChip(
                        onClick = {},
                        label = { Text(text = "Adding to library…") },
                        enabled = false
                    )
                    Spacer(Modifier.height(12.dp))
                }

                if (uiState.isRemovingFromLibrary) {
                    AssistChip(
                        onClick = {},
                        label = { Text(text = "Removing from library…") },
                        enabled = false
                    )
                    Spacer(Modifier.height(12.dp))
                }

                if (uiState.isApplying) {
                    AssistChip(
                        onClick = {},
                        label = { Text(text = "Applying wallpaper…") },
                        enabled = false
                    )
                    Spacer(Modifier.height(12.dp))
                }

                if (uiState.isProcessingEdits) {
                    AssistChip(
                        onClick = {},
                        label = { Text(text = "Updating preview…") },
                        enabled = false
                    )
                    Spacer(Modifier.height(12.dp))
                }

                Text(
                    text = "Adjust wallpaper",
                    style = MaterialTheme.typography.titleMedium
                )
                if (!uiState.isEditorReady) {
                    Spacer(Modifier.height(4.dp))
                    if (uiState.isProcessingEdits) {
                        Text(
                            text = "Preparing editor…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Load the full-quality image before editing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onPrepareEditor) {
                            Text(text = "Prepare editor")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = uiState.adjustments.brightness,
                    onValueChange = onAdjustBrightness,
                    valueRange = -0.5f..0.5f,
                    enabled = uiState.isEditorReady,
                    colors = SliderDefaults.colors()
                )
                val brightnessPercent = (uiState.adjustments.brightness * 100).roundToInt()
                Text(
                    text = "Brightness ${if (brightnessPercent >= 0) "+" else ""}$brightnessPercent%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WallpaperFilter.values().forEach { filter ->
                        FilterChip(
                            selected = uiState.adjustments.filter == filter,
                            onClick = { onSelectFilter(filter) },
                            enabled = uiState.isEditorReady,
                            label = { Text(text = filter.displayName()) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WallpaperCrop.values().forEach { crop ->
                        FilterChip(
                            selected = uiState.adjustments.crop == crop,
                            onClick = { onSelectCrop(crop) },
                            enabled = uiState.isEditorReady,
                            label = { Text(text = crop.displayName()) }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onResetAdjustments,
                    enabled = !uiState.adjustments.isIdentity
                ) {
                    Text(text = "Reset adjustments")
                }
                Spacer(Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (canDownload) {
                        if (uiState.isDownloaded) {
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onRequestRemoveDownload,
                                enabled = !uiState.isRemovingDownload
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.TaskAlt,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Downloaded")
                            }
                        } else {
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onDownload,
                                enabled = !uiState.isDownloading
                            ) {
                                Text(text = "Download")
                            }
                        }
                    }

                    val showLibraryToggle = canAddToLibrary || canRemoveFromLibrary
                    if (showLibraryToggle) {
                        val enabled = if (uiState.isInLibrary) {
                            canRemoveFromLibrary && !uiState.isRemovingFromLibrary
                        } else {
                            canAddToLibrary && !uiState.isAddingToLibrary
                        }
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (uiState.isInLibrary) {
                                    onRemoveFromLibrary()
                                } else {
                                    onAddToLibrary()
                                }
                            },
                            enabled = enabled
                        ) {
                            if (uiState.isInLibrary && canRemoveFromLibrary) {
                                Icon(
                                    imageVector = Icons.Outlined.TaskAlt,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "In your library")
                            } else {
                                Text(text = "Add to library")
                            }
                        }
                    }

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showTargetDialog = true },
                        enabled = uiState.hasWallpaperPermission && !uiState.isApplying
                    ) {
                        Text(text = "Set wallpaper")
                    }
                }

                Spacer(Modifier.height(16.dp))

                TextButton(onClick = { uriHandler.openUri(wallpaper.sourceUrl) }) {
                    Text(text = "Open original post")
                }
            }
        }
    }

    if (uiState.showRemoveDownloadConfirmation) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isRemovingDownload) {
                    onDismissRemoveDownload()
                }
            },
            title = { Text(text = "Remove downloaded file?") },
            text = {
                Text(
                    text = "Delete the downloaded copy saved locally for this wallpaper?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirmRemoveDownload,
                    enabled = !uiState.isRemovingDownload
                ) {
                    Text(text = "Remove")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissRemoveDownload,
                    enabled = !uiState.isRemovingDownload
                ) {
                    Text(text = "Cancel")
                }
            }
        )
    }

    if (showTargetDialog) {
        SetWallpaperDialog(
            onDismiss = { showTargetDialog = false },
            onTargetSelected = { target ->
                showTargetDialog = false
                onApplyTarget(target)
            },
            isApplying = uiState.isApplying
        )
    }

    uiState.pendingFallback?.let { fallback ->
        PreviewFallbackDialog(
            fallback = fallback,
            isApplying = uiState.isApplying,
            onConfirm = onConfirmApplyWithoutPreview,
            onDismiss = onDismissPreviewFallback
        )
    }
}

private fun WallpaperFilter.displayName(): String = when (this) {
    WallpaperFilter.NONE -> "Original"
    WallpaperFilter.GRAYSCALE -> "Grayscale"
    WallpaperFilter.SEPIA -> "Sepia"
}

private fun WallpaperCrop.displayName(): String = when (this) {
    WallpaperCrop.AUTO -> "Screen"
    WallpaperCrop.ORIGINAL -> "Original"
    WallpaperCrop.SQUARE -> "Square"
}

private const val DEFAULT_DETAIL_ASPECT_RATIO = 9f / 16f

@Composable
private fun SetWallpaperDialog(
    onDismiss: () -> Unit,
    onTargetSelected: (WallpaperTarget) -> Unit,
    isApplying: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Choose where to apply") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Select where you want to set this wallpaper.")
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onTargetSelected(WallpaperTarget.HOME) },
                    enabled = !isApplying
                ) {
                    Text(text = "Set as home screen")
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onTargetSelected(WallpaperTarget.LOCK) },
                    enabled = !isApplying
                ) {
                    Text(text = "Set as lock screen")
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onTargetSelected(WallpaperTarget.BOTH) },
                    enabled = !isApplying
                ) {
                    Text(text = "Set as both screens")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Back")
            }
        }
    )
}

@Composable
private fun PreviewFallbackDialog(
    fallback: WallpaperDetailViewModel.WallpaperPreviewFallback,
    isApplying: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Preview unavailable") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val reason = fallback.reason
                if (reason.isNullOrBlank()) {
                    Text(text = "Your device couldn't open the wallpaper preview.")
                } else {
                    Text(
                        text = "Your device couldn't open the wallpaper preview ($reason)."
                    )
                }
                Text(
                    text = "Apply the wallpaper directly to the ${fallback.target.label}?"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isApplying) {
                Text(text = "Apply without preview")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isApplying) {
                Text(text = "Cancel")
            }
        }
    )
}