package com.joshiminh.wallbase.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.joshiminh.wallbase.R
import com.joshiminh.wallbase.data.wallpapers.WallpaperItem
import com.joshiminh.wallbase.data.wallpapers.WallpaperTarget

@Composable
fun WallpaperDetailRoute(
    wallpaper: WallpaperItem,
    viewModel: WallpaperDetailViewModel = viewModel(factory = WallpaperDetailViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(wallpaper.id) {
        viewModel.setWallpaper(wallpaper)
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    WallpaperDetailScreen(
        uiState = uiState,
        onPreviewTargetSelected = viewModel::updatePreviewTarget,
        onApplyTarget = viewModel::applyWallpaper,
        onAddToLibrary = viewModel::addToLibrary,
        snackbarHostState = snackbarHostState
    )
}

@Composable
private fun WallpaperDetailScreen(
    uiState: WallpaperDetailViewModel.WallpaperDetailUiState,
    onPreviewTargetSelected: (WallpaperTarget) -> Unit,
    onApplyTarget: (WallpaperTarget) -> Unit,
    onAddToLibrary: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val wallpaper = uiState.wallpaper ?: return
    val uriHandler = LocalUriHandler.current
    val previewTarget = if (uiState.previewTarget == WallpaperTarget.LOCK) {
        WallpaperTarget.LOCK
    } else {
        WallpaperTarget.HOME
    }
    val canAddToLibrary = wallpaper.sourceKey != null
    var showTargetDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())   // <— add this
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
                Column(modifier = Modifier.padding(16.dp)) {
                    wallpaper.sourceName?.let { sourceName ->
                        Text(
                            text = sourceName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    WallpaperPreview(imageUrl = wallpaper.imageUrl, previewTarget = previewTarget)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (previewTarget == WallpaperTarget.LOCK) {
                            stringResource(id = R.string.preview_lock_screen)
                        } else {
                            stringResource(id = R.string.preview_home_screen)
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PreviewChip(
                            text = stringResource(R.string.preview_home_short),
                            selected = previewTarget == WallpaperTarget.HOME,
                            onClick = { onPreviewTargetSelected(WallpaperTarget.HOME) }
                        )
                        PreviewChip(
                            text = stringResource(R.string.preview_lock_short),
                            selected = previewTarget == WallpaperTarget.LOCK,
                            onClick = { onPreviewTargetSelected(WallpaperTarget.LOCK) }
                        )
                    }
                }
            }

            if (uiState.isAddingToLibrary) {
                AssistChip(
                    onClick = {},
                    label = { Text(text = stringResource(R.string.adding_to_library)) },
                    enabled = false
                )
                Spacer(Modifier.height(12.dp))
            }

            if (uiState.isApplying) {
                AssistChip(
                    onClick = {},
                    label = { Text(text = stringResource(R.string.applying_wallpaper)) },
                    enabled = false
                )
                Spacer(Modifier.height(12.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { if (canAddToLibrary) onAddToLibrary() },
                    enabled = canAddToLibrary && !uiState.isAddingToLibrary
                ) {
                    Text(text = stringResource(id = R.string.add_to_library))
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showTargetDialog = true },
                    enabled = !uiState.isApplying
                ) {
                    Text(text = stringResource(id = R.string.set_wallpaper))
                }
            }

            Spacer(Modifier.height(16.dp))

            TextButton(onClick = { uriHandler.openUri(wallpaper.sourceUrl) }) {
                Text(text = stringResource(id = R.string.open_original))
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
}

@Composable
private fun PreviewChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
private fun SetWallpaperDialog(
    onDismiss: () -> Unit,
    onTargetSelected: (WallpaperTarget) -> Unit,
    isApplying: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.choose_wallpaper_target)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = stringResource(R.string.choose_wallpaper_target_message))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onTargetSelected(WallpaperTarget.HOME) },
                    enabled = !isApplying
                ) {
                    Text(text = stringResource(id = R.string.apply_home_screen))
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onTargetSelected(WallpaperTarget.LOCK) },
                    enabled = !isApplying
                ) {
                    Text(text = stringResource(id = R.string.apply_lock_screen))
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onTargetSelected(WallpaperTarget.BOTH) },
                    enabled = !isApplying
                ) {
                    Text(text = stringResource(id = R.string.apply_both_screens))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.back))
            }
        }
    )
}

@Composable
private fun WallpaperPreview(imageUrl: String, previewTarget: WallpaperTarget) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 19.5f)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        val overlay = remember(previewTarget) {
            when (previewTarget) {
                WallpaperTarget.LOCK -> lockScreenOverlay()
                else -> homeScreenOverlay()
            }
        }

        overlay()
    }
}

private fun homeScreenOverlay(): @Composable () -> Unit = {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.35f))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                    )
                )
        )
    }
}

private fun lockScreenOverlay(): @Composable () -> Unit = {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "12:45",
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Wednesday",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.7f))
        )
    }
}
