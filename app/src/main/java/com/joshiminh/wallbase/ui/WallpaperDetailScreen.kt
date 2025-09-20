package com.joshiminh.wallbase.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.joshiminh.wallbase.data.entity.source.SourceKeys
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem
import com.joshiminh.wallbase.util.wallpapers.WallpaperTarget
import com.joshiminh.wallbase.ui.viewmodel.WallpaperDetailViewModel

@Composable
fun WallpaperDetailRoute(
    wallpaper: WallpaperItem,
    viewModel: WallpaperDetailViewModel = viewModel(factory = WallpaperDetailViewModel.Factory)
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
        onRequestPermission = { permissionLauncher.launch(Manifest.permission.SET_WALLPAPER) },
        snackbarHostState = snackbarHostState
    )
}

@Composable
private fun WallpaperDetailScreen(
    uiState: WallpaperDetailViewModel.WallpaperDetailUiState,
    onApplyTarget: (WallpaperTarget) -> Unit,
    onConfirmApplyWithoutPreview: () -> Unit,
    onDismissPreviewFallback: () -> Unit,
    onAddToLibrary: () -> Unit,
    onRemoveFromLibrary: () -> Unit,
    onRequestPermission: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val wallpaper = uiState.wallpaper ?: return
    val uriHandler = LocalUriHandler.current
    val canAddToLibrary = wallpaper.sourceKey != null && wallpaper.sourceKey != SourceKeys.LOCAL
    val canRemoveFromLibrary = uiState.isInLibrary && wallpaper.sourceKey != null
    var showTargetDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = wallpaper.title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    wallpaper.sourceName?.let { sourceName ->
                        Text(
                            text = sourceName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(9f / 19.5f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
                    ) {
                        AsyncImage(
                            model = wallpaper.imageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            if (!uiState.hasWallpaperPermission) {
                Text(
                    text = "Allow WallBase to open the system wallpaper preview so you can confirm the image.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                TextButton(onClick = onRequestPermission) {
                    Text(text = "Grant wallpaper access")
                }
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

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (canAddToLibrary) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onAddToLibrary,
                        enabled = !uiState.isAddingToLibrary && !uiState.isInLibrary
                    ) {
                        val label = if (uiState.isInLibrary) {
                            "In your library"
                        } else {
                            "Add to library"
                        }
                        Text(text = label)
                    }
                }

                if (canRemoveFromLibrary) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onRemoveFromLibrary,
                        enabled = !uiState.isRemovingFromLibrary
                    ) {
                        Text(text = "Remove from library")
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