package com.joshiminh.wallbase.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

    LaunchedEffect(wallpaper.id) {
        viewModel.prepareEditor()
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
    onAdjustBrightness: (Float) -> Unit,
    onSelectFilter: (WallpaperFilter) -> Unit,
    onSelectCrop: (WallpaperCrop) -> Unit,
    onResetAdjustments: () -> Unit,
) {
    val aspectRatio = wallpaper.aspectRatio?.takeIf { it > 0f } ?: DEFAULT_DETAIL_ASPECT_RATIO

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Edit wallpaper") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
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
                isProcessing = uiState.isProcessingEdits,
                isReady = uiState.isEditorReady
            )

            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                AdjustmentSection(
                    title = "Brightness",
                    subtitle = "Fine-tune the exposure",
                ) {
                    Slider(
                        value = uiState.adjustments.brightness,
                        onValueChange = onAdjustBrightness,
                        valueRange = -0.5f..0.5f,
                        enabled = uiState.isEditorReady,
                        colors = SliderDefaults.colors()
                    )
                    val brightnessPercent = (uiState.adjustments.brightness * 100).roundToInt()
                    Text(
                        text = "${if (brightnessPercent >= 0) "+" else ""}$brightnessPercent%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(16.dp))

                AdjustmentSection(
                    title = "Filters",
                    subtitle = "Change the overall mood",
                ) {
                    FlowingChipRow {
                        WallpaperFilter.values().forEach { filter ->
                            FilterChip(
                                selected = uiState.adjustments.filter == filter,
                                onClick = { onSelectFilter(filter) },
                                enabled = uiState.isEditorReady,
                                label = { Text(text = filter.displayName()) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                AdjustmentSection(
                    title = "Crop",
                    subtitle = "Select how the wallpaper is framed",
                ) {
                    FlowingChipRow {
                        WallpaperCrop.values().forEach { crop ->
                            FilterChip(
                                selected = uiState.adjustments.crop == crop,
                                onClick = { onSelectCrop(crop) },
                                enabled = uiState.isEditorReady,
                                label = { Text(text = crop.displayName()) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onResetAdjustments,
                    enabled = !uiState.adjustments.isIdentity
                ) {
                    Text(text = "Reset adjustments")
                }

                if (!uiState.isEditorReady) {
                    Spacer(Modifier.height(16.dp))
                    EditorStatusCard(isProcessing = uiState.isProcessingEdits)
                } else if (uiState.isProcessingEdits) {
                    Spacer(Modifier.height(16.dp))
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
    isProcessing: Boolean,
    isReady: Boolean,
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ) {
            Box {
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
                        clipShape = RoundedCornerShape(28.dp)
                    )
                }

                if (!isReady) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspectRatio)
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.6f))
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = if (isProcessing) "Preparing full resolution…" else "Starting editor…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdjustmentSection(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun FlowingChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun EditorStatusCard(isProcessing: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (isProcessing) "Preparing editor…" else "Fetching full-quality image…",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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

