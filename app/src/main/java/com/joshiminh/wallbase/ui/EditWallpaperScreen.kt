@file:OptIn(ExperimentalMaterial3Api::class)
@file:Suppress("unused", "RemoveRedundantQualifierName")

package com.joshiminh.wallbase.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem
import com.joshiminh.wallbase.ui.components.WallpaperPreviewImage
import com.joshiminh.wallbase.ui.viewmodel.WallpaperDetailViewModel
import com.joshiminh.wallbase.util.wallpapers.WallpaperCrop
import com.joshiminh.wallbase.util.wallpapers.WallpaperCropSettings
import com.joshiminh.wallbase.util.wallpapers.WallpaperFilter
import java.util.Locale
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
    var showCropDialog by remember { mutableStateOf(false) }
    val currentCrop = uiState.adjustments.crop
    val customCropSettings = (currentCrop as? WallpaperCrop.Custom)?.settings
    val defaultCropSettings = wallpaper.cropSettings ?: WallpaperCropSettings.Full

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
                        WallpaperFilter.entries.forEach { filter ->
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
                        WallpaperCrop.presets.forEach { crop ->
                            FilterChip(
                                selected = currentCrop == crop,
                                onClick = { onSelectCrop(crop) },
                                enabled = uiState.isEditorReady,
                                label = { Text(text = crop.displayName()) }
                            )
                        }
                        FilterChip(
                            selected = currentCrop is WallpaperCrop.Custom,
                            onClick = { showCropDialog = true },
                            enabled = uiState.isEditorReady,
                            label = { Text(text = if (currentCrop is WallpaperCrop.Custom) "Custom crop" else "Custom…") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Crop,
                                    contentDescription = null
                                )
                            }
                        )
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

        if (showCropDialog) {
            val initialCrop = (customCropSettings ?: defaultCropSettings).sanitized()
            AdvancedCropDialog(
                wallpaper = wallpaper,
                previewBitmap = uiState.editedPreview,
                initial = initialCrop,
                onDismiss = { showCropDialog = false },
                onConfirm = { settings ->
                    onSelectCrop(WallpaperCrop.Custom(settings))
                    showCropDialog = false
                }
            )
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
private fun AdvancedCropDialog(
    wallpaper: WallpaperItem,
    previewBitmap: Bitmap?,
    initial: WallpaperCropSettings,
    onDismiss: () -> Unit,
    onConfirm: (WallpaperCropSettings) -> Unit,
) {
    var crop by remember(initial) { mutableStateOf(initial.sanitized()) }
    val previewImage = remember(previewBitmap) { previewBitmap?.asImageBitmap() }
    val aspectRatio = remember(previewBitmap, wallpaper) {
        previewBitmap?.let { bitmap ->
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
            if (ratio > 0f) ratio else null
        } ?: wallpaper.aspectRatio?.takeIf { it > 0f } ?: DEFAULT_DETAIL_ASPECT_RATIO
    }
    val widthPercent = (crop.widthFraction() * 100).roundToInt().coerceIn(0, 100)
    val heightPercent = (crop.heightFraction() * 100).roundToInt().coerceIn(0, 100)
    val ratioText = formatAspectRatio(crop.aspectRatio())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Adjust crop") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CropPreview(
                    imageBitmap = previewImage,
                    imageModel = wallpaper.previewModel(),
                    aspectRatio = aspectRatio,
                    crop = crop,
                    onCropChange = { updated -> crop = updated }
                )
                Text(
                    text = "Drag the handles to reposition and resize the crop.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Width ${widthPercent}% • Height ${heightPercent}% • Ratio $ratioText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = { crop = WallpaperCropSettings.Full }) {
                    Text(text = "Reset to full image")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(crop.sanitized()) }) {
                Text(text = "Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}

@Composable
private fun CropPreview(
    imageBitmap: ImageBitmap?,
    imageModel: Any,
    aspectRatio: Float,
    crop: WallpaperCropSettings,
    onCropChange: (WallpaperCropSettings) -> Unit,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var activeHandle by remember { mutableStateOf<CropHandle?>(null) }
    val handleRadius = with(density) { 14.dp.toPx() }
    val strokeWidth = with(density) { 2.dp.toPx() }
    val overlayColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black)
            .onSizeChanged { containerSize = it }
            .pointerInput(crop, containerSize) {
                if (containerSize.width == 0 || containerSize.height == 0) return@pointerInput
                var workingCrop = crop
                detectDragGestures(
                    onDragStart = { offset ->
                        activeHandle = determineHandle(offset, workingCrop, containerSize, handleRadius)
                    },
                    onDrag = { change, dragAmount ->
                        val handle = activeHandle ?: return@detectDragGestures
                        change.consume()
                        val width = containerSize.width.toFloat()
                        val height = containerSize.height.toFloat()
                        if (width == 0f || height == 0f) return@detectDragGestures
                        val dx = dragAmount.x / width
                        val dy = dragAmount.y / height
                        val updated = when (handle) {
                            CropHandle.Center -> workingCrop.offsetBy(dx, dy)
                            CropHandle.TopLeft -> WallpaperCropSettings(
                                left = workingCrop.left + dx,
                                top = workingCrop.top + dy,
                                right = workingCrop.right,
                                bottom = workingCrop.bottom
                            )
                            CropHandle.TopRight -> WallpaperCropSettings(
                                left = workingCrop.left,
                                top = workingCrop.top + dy,
                                right = workingCrop.right + dx,
                                bottom = workingCrop.bottom
                            )
                            CropHandle.BottomLeft -> WallpaperCropSettings(
                                left = workingCrop.left + dx,
                                top = workingCrop.top,
                                right = workingCrop.right,
                                bottom = workingCrop.bottom + dy
                            )
                            CropHandle.BottomRight -> WallpaperCropSettings(
                                left = workingCrop.left,
                                top = workingCrop.top,
                                right = workingCrop.right + dx,
                                bottom = workingCrop.bottom + dy
                            )
                        }.sanitized()
                        workingCrop = updated
                        onCropChange(updated)
                    },
                    onDragCancel = { activeHandle = null },
                    onDragEnd = { activeHandle = null }
                )
            }
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        CropOverlay(
            crop = crop,
            strokeWidth = strokeWidth,
            handleRadius = handleRadius,
            overlayColor = overlayColor
        )
    }
}

@Composable
private fun CropOverlay(
    crop: WallpaperCropSettings,
    strokeWidth: Float,
    handleRadius: Float,
    overlayColor: Color,
) {
    Canvas(Canvas(Modifier.fillMaxSize())) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val left = crop.left.coerceIn(0f, 1f) * size.width
        val top = crop.top.coerceIn(0f, 1f) * size.height
        val right = crop.right.coerceIn(0f, 1f) * size.width
        val bottom = crop.bottom.coerceIn(0f, 1f) * size.height
        val cropWidth = (right - left).coerceAtLeast(1f)
        val cropHeight = (bottom - top).coerceAtLeast(1f)

        drawRect(color = overlayColor, size = Size(size.width, top))
        drawRect(color = overlayColor, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
        drawRect(color = overlayColor, topLeft = Offset(0f, top), size = Size(left, cropHeight))
        drawRect(color = overlayColor, topLeft = Offset(right, top), size = Size(size.width - right, cropHeight))

        drawRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(cropWidth, cropHeight),
            style = Stroke(width = strokeWidth)
        )

        val handles = listOf(
            Offset(left, top),
            Offset(right, top),
            Offset(left, bottom),
            Offset(right, bottom)
        )
        handles.forEach { center ->
            drawCircle(color = Color.White, radius = handleRadius, center = center)
        }
    }
}

private fun determineHandle(
    offset: Offset,
    crop: WallpaperCropSettings,
    size: IntSize,
    radius: Float,
): CropHandle? {
    if (size.width <= 0 || size.height <= 0) return null
    val left = crop.left.coerceIn(0f, 1f) * size.width
    val top = crop.top.coerceIn(0f, 1f) * size.height
    val right = crop.right.coerceIn(0f, 1f) * size.width
    val bottom = crop.bottom.coerceIn(0f, 1f) * size.height
    val corners = listOf(
        CropHandle.TopLeft to Offset(left, top),
        CropHandle.TopRight to Offset(right, top),
        CropHandle.BottomLeft to Offset(left, bottom),
        CropHandle.BottomRight to Offset(right, bottom)
    )
    val radiusSquared = radius * radius
    corners.forEach { (handle, point) ->
        if ((offset - point).getDistanceSquared() <= radiusSquared) {
            return handle
        }
    }
    if (offset.x in left..right && offset.y in top..bottom) {
        return CropHandle.Center
    }
    return null
}

private fun formatAspectRatio(ratio: Float): String {
    if (ratio <= 0f) return "1:1"
    return if (ratio >= 1f) {
        String.format(Locale.getDefault(), "%.2f:1", ratio)
    } else {
        String.format(Locale.getDefault(), "1:%.2f", 1f / ratio)
    }
}

private enum class CropHandle {
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight,
    Center,
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
    WallpaperCrop.Auto -> "Screen"
    WallpaperCrop.Original -> "Original"
    WallpaperCrop.Square -> "Square"
    is WallpaperCrop.Custom -> "Custom"
}