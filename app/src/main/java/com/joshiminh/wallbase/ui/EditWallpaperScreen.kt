package com.joshiminh.wallbase.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joshiminh.wallbase.ui.components.WallpaperPreviewImage
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem
import com.joshiminh.wallbase.ui.viewmodel.WallpaperDetailViewModel
import com.joshiminh.wallbase.util.wallpapers.WallpaperCrop
import com.joshiminh.wallbase.util.wallpapers.WallpaperFilter
import kotlin.math.roundToInt

@Composable
fun EditWallpaperRoute(
    onNavigateBack: () -> Unit,
    viewModel: WallpaperDetailViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val wallpaper = uiState.wallpaper
    if (wallpaper == null) {
        LaunchedEffect(Unit) { onNavigateBack() }
        return
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    BackHandler(onBack = onNavigateBack)

    EditWallpaperScreen(
        wallpaper = wallpaper,
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onPrepareEditor = viewModel::prepareEditor,
        onAdjustBrightness = viewModel::updateBrightness,
        onSelectFilter = viewModel::updateFilter,
        onSelectCrop = viewModel::updateCrop,
        onResetAdjustments = viewModel::resetAdjustments,
    )
}

@Composable
private fun EditWallpaperScreen(
    wallpaper: WallpaperItem,
    uiState: WallpaperDetailViewModel.WallpaperDetailUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onPrepareEditor: () -> Unit,
    onAdjustBrightness: (Float) -> Unit,
    onSelectFilter: (WallpaperFilter) -> Unit,
    onSelectCrop: (WallpaperCrop) -> Unit,
    onResetAdjustments: () -> Unit,
) {
    val aspectRatio = wallpaper.aspectRatio?.takeIf { it > 0f } ?: DEFAULT_DETAIL_ASPECT_RATIO

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
            WallpaperPreview(
                wallpaper = wallpaper,
                editedPreview = uiState.editedPreview,
                aspectRatio = aspectRatio,
                onNavigateBack = onNavigateBack
            )

            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
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

                if (uiState.isProcessingEdits) {
                    Spacer(Modifier.height(12.dp))
                    AssistChip(
                        onClick = {},
                        label = { Text(text = "Updating preview…") },
                        enabled = false
                    )
                }
            }
        }
    }
}

@Composable
private fun WallpaperPreview(
    wallpaper: WallpaperItem,
    editedPreview: android.graphics.Bitmap?,
    aspectRatio: Float,
    onNavigateBack: () -> Unit,
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
            if (editedPreview != null) {
                Image(
                    bitmap = editedPreview.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspectRatio),
                    contentScale = ContentScale.Crop
                )
            } else {
                WallpaperPreviewImage(
                    model = wallpaper.previewModel(),
                    contentDescription = wallpaper.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspectRatio),
                    contentScale = ContentScale.Crop,
                    clipShape = RoundedCornerShape(24.dp)
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
}

private const val DEFAULT_DETAIL_ASPECT_RATIO = 9f / 16f

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

