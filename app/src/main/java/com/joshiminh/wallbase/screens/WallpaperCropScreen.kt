@file:Suppress("DEPRECATION")

package com.joshiminh.wallbase.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joshiminh.wallbase.data.entity.WallpaperItem
import com.joshiminh.wallbase.ui.components.FramingRatioMode
import com.joshiminh.wallbase.ui.components.WallpaperPreviewImage
import com.joshiminh.wallbase.ui.theme.WallBaseShapes
import com.joshiminh.wallbase.util.wallpapers.WallpaperCrop
import com.joshiminh.wallbase.util.wallpapers.WallpaperCropSettings
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class CropSection(val title: String, val icon: ImageVector) {
    RATIO("Ratio", Icons.Outlined.Crop),
    ZOOM("Zoom", Icons.Outlined.ZoomIn)
}

private fun computeCropSettings(
    mode: FramingRatioMode,
    targetRatio: Float,
    imageRatio: Float,
    zoom: Float,
    panX: Float,
    panY: Float,
    freeWidthFrac: Float = 1f,
    freeHeightFrac: Float = 1f
): WallpaperCropSettings {
    if (mode == FramingRatioMode.ORIGINAL) {
        return WallpaperCropSettings.Full
    }

    val imgR = if (imageRatio <= 0f) 1f else imageRatio

    val (wBase, hBase) = if (mode == FramingRatioMode.FREE) {
        Pair(freeWidthFrac.coerceIn(0.05f, 1f), freeHeightFrac.coerceIn(0.05f, 1f))
    } else {
        val targetR = if (targetRatio <= 0f) 1f else targetRatio
        val r = targetR / imgR
        if (r <= 1f) {
            // Target is narrower than image
            Pair(r.coerceIn(0.05f, 1f), 1f)
        } else {
            // Target is wider than image
            Pair(1f, (1f / r).coerceIn(0.05f, 1f))
        }
    }

    val z = zoom.coerceAtLeast(1f)
    val wFrac = (wBase / z).coerceIn(0.05f, 1f)
    val hFrac = (hBase / z).coerceIn(0.05f, 1f)

    val maxLeft = (1f - wFrac).coerceAtLeast(0f)
    val maxTop = (1f - hFrac).coerceAtLeast(0f)

    val left = (panX * maxLeft).coerceIn(0f, maxLeft)
    val top = (panY * maxTop).coerceIn(0f, maxTop)
    val right = (left + wFrac).coerceAtMost(1f)
    val bottom = (top + hFrac).coerceAtMost(1f)

    return WallpaperCropSettings(left, top, right, bottom).sanitized()
}

/**
 * Full-screen dedicated Wallpaper Crop and Framing Studio.
 */
@Composable
fun WallpaperCropScreen(
    currentCrop: WallpaperCrop,
    wallpaper: WallpaperItem,
    previewBitmap: Bitmap? = null,
    onSelectCrop: (WallpaperCrop) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismiss)

    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val deviceRatio = remember(configuration) {
        configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat().coerceAtLeast(0.1f)
    }

    val imageRatio = remember(wallpaper, previewBitmap) {
        val w = previewBitmap?.width ?: wallpaper.width ?: 0
        val h = previewBitmap?.height ?: wallpaper.height ?: 0
        if (w > 0 && h > 0) w.toFloat() / h.toFloat() else 9f / 16f
    }

    // Initial values deduced from currentCrop
    val initialMode = remember(currentCrop) {
        when (currentCrop) {
            WallpaperCrop.Auto -> FramingRatioMode.SCREEN
            WallpaperCrop.Original -> FramingRatioMode.ORIGINAL
            WallpaperCrop.Square -> FramingRatioMode.SQUARE_1_1
            is WallpaperCrop.Custom -> {
                val s = currentCrop.settings.sanitized()
                val wF = s.widthFraction()
                val hF = s.heightFraction()
                val cropR = (wF * imageRatio) / hF.coerceAtLeast(0.01f)
                when {
                    abs(cropR - 1f) < 0.05f -> FramingRatioMode.SQUARE_1_1
                    abs(cropR - (9f / 16f)) < 0.05f -> FramingRatioMode.PORTRAIT_9_16
                    abs(cropR - (16f / 9f)) < 0.05f -> FramingRatioMode.LANDSCAPE_16_9
                    abs(cropR - (3f / 4f)) < 0.05f -> FramingRatioMode.PORTRAIT_3_4
                    abs(cropR - (4f / 3f)) < 0.05f -> FramingRatioMode.LANDSCAPE_4_3
                    abs(cropR - deviceRatio) < 0.05f -> FramingRatioMode.SCREEN
                    wF >= 0.98f && hF >= 0.98f -> FramingRatioMode.ORIGINAL
                    else -> FramingRatioMode.FREE
                }
            }
        }
    }

    val initialPanX = remember(currentCrop) {
        if (currentCrop is WallpaperCrop.Custom) {
            val s = currentCrop.settings.sanitized()
            val avail = 1f - s.widthFraction()
            if (avail > 0.001f) (s.left / avail).coerceIn(0f, 1f) else 0.5f
        } else 0.5f
    }

    val initialPanY = remember(currentCrop) {
        if (currentCrop is WallpaperCrop.Custom) {
            val s = currentCrop.settings.sanitized()
            val avail = 1f - s.heightFraction()
            if (avail > 0.001f) (s.top / avail).coerceIn(0f, 1f) else 0.5f
        } else 0.5f
    }

    val initialZoom = remember(currentCrop) {
        if (currentCrop is WallpaperCrop.Custom) {
            val s = currentCrop.settings.sanitized()
            val maxFrac = maxOf(s.widthFraction(), s.heightFraction()).coerceAtLeast(0.05f)
            (1f / maxFrac).coerceIn(1f, 4f)
        } else 1f
    }

    var selectedMode by remember { mutableStateOf(initialMode) }
    var zoom by remember { mutableFloatStateOf(initialZoom) }
    var panX by remember { mutableFloatStateOf(initialPanX) }
    var panY by remember { mutableFloatStateOf(initialPanY) }
    var freeWidthFrac by remember { mutableFloatStateOf(1f) }
    var freeHeightFrac by remember { mutableFloatStateOf(1f) }
    var selectedSection by remember { mutableStateOf(CropSection.RATIO) }

    val activeTargetRatio = remember(selectedMode, deviceRatio) {
        selectedMode.fixedRatio ?: when (selectedMode) {
            FramingRatioMode.SCREEN -> deviceRatio
            FramingRatioMode.ORIGINAL -> imageRatio
            FramingRatioMode.FREE -> deviceRatio
            else -> 1f
        }
    }

    val cropSettings = remember(selectedMode, activeTargetRatio, imageRatio, zoom, panX, panY, freeWidthFrac, freeHeightFrac) {
        computeCropSettings(
            mode = selectedMode,
            targetRatio = activeTargetRatio,
            imageRatio = imageRatio,
            zoom = zoom,
            panX = panX,
            panY = panY,
            freeWidthFrac = freeWidthFrac,
            freeHeightFrac = freeHeightFrac
        )
    }

    val applyFramingAction = {
        val finalCrop = when {
            selectedMode == FramingRatioMode.ORIGINAL -> WallpaperCrop.Original
            selectedMode == FramingRatioMode.SQUARE_1_1 && zoom <= 1.01f && abs(panX - 0.5f) < 0.01f && abs(panY - 0.5f) < 0.01f -> WallpaperCrop.Square
            selectedMode == FramingRatioMode.SCREEN && zoom <= 1.01f && abs(panX - 0.5f) < 0.01f && abs(panY - 0.5f) < 0.01f -> WallpaperCrop.Auto
            else -> WallpaperCrop.Custom(cropSettings)
        }
        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        onSelectCrop(finalCrop)
        onDismiss()
    }

    // State snapshots for gesture callbacks
    val currentZoom by rememberUpdatedState(zoom)
    val currentPanX by rememberUpdatedState(panX)
    val currentPanY by rememberUpdatedState(panY)
    val currentMode by rememberUpdatedState(selectedMode)
    val currentTargetRatio by rememberUpdatedState(activeTargetRatio)
    val currentFreeWidth by rememberUpdatedState(freeWidthFrac)
    val currentFreeHeight by rememberUpdatedState(freeHeightFrac)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090A10))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Top Bar: fully centred title via Box overlay ──────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                // Centred title
                Text(
                    text = "Crop & Frame",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                // Left: close
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel framing",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Right: reset + Apply
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 8.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                selectedMode = FramingRatioMode.SCREEN
                                zoom = 1f
                                panX = 0.5f
                                panY = 0.5f
                                freeWidthFrac = 1f
                                freeHeightFrac = 1f
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Reset framing",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = applyFramingAction,
                            shape = WallBaseShapes.pill,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            modifier = Modifier.height(40.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                text = "Apply",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ── Interactive Canvas Viewport ───────────────────────────────────
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF07080D)),
                contentAlignment = Alignment.Center
            ) {
                val containerWidth = constraints.maxWidth.toFloat()
                val containerHeight = constraints.maxHeight.toFloat()

                val (imgWidthPx, imgHeightPx) = remember(containerWidth, containerHeight, imageRatio) {
                    val containerRatio = if (containerHeight > 0) containerWidth / containerHeight else 1f
                    if (containerRatio > imageRatio) {
                        val h = containerHeight * 0.94f
                        Pair(h * imageRatio, h)
                    } else {
                        val w = containerWidth * 0.94f
                        Pair(w, w / imageRatio)
                    }
                }

                val density = LocalDensity.current
                val imgWidthDp = with(density) { imgWidthPx.toDp() }
                val imgHeightDp = with(density) { imgHeightPx.toDp() }

                // Full-viewport touch area for pan + pinch-zoom
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(imgWidthPx, imgHeightPx, imageRatio) {
                            detectTransformGestures(panZoomLock = false) { _, pan, zoomChange, _ ->
                                if (currentMode != FramingRatioMode.ORIGINAL) {
                                    // 1. Zoom — pinch to zoom in/out (1x – 8x)
                                    val newZoom = if (zoomChange != 1f) {
                                        (currentZoom * zoomChange).coerceIn(1f, 8f).also { zoom = it }
                                    } else {
                                        currentZoom
                                    }

                                    // 2. Compute current crop window size fractions
                                    val (wBase, hBase) = if (currentMode == FramingRatioMode.FREE) {
                                        Pair(
                                            currentFreeWidth.coerceIn(0.05f, 1f),
                                            currentFreeHeight.coerceIn(0.05f, 1f)
                                        )
                                    } else {
                                        val targetR = if (currentTargetRatio <= 0f) 1f else currentTargetRatio
                                        val imgR = if (imageRatio <= 0f) 1f else imageRatio
                                        val r = targetR / imgR
                                        if (r <= 1f) Pair(r.coerceIn(0.05f, 1f), 1f)
                                        else Pair(1f, (1f / r).coerceIn(0.05f, 1f))
                                    }

                                    val wFrac = (wBase / newZoom).coerceIn(0.05f, 1f)
                                    val hFrac = (hBase / newZoom).coerceIn(0.05f, 1f)

                                    val maxLeft = (1f - wFrac).coerceAtLeast(0f)
                                    val maxTop = (1f - hFrac).coerceAtLeast(0f)

                                    // 3. Pan — drag follows finger 1:1 in image-relative space
                                    //    pan.x > 0 means finger moved right → crop window moves right → panX increases
                                    //    pan.y > 0 means finger moved down  → crop window moves down  → panY increases
                                    if (maxLeft > 0.0001f && imgWidthPx > 0f) {
                                        val imageCropWidthPx = imgWidthPx * wFrac
                                        val availableImageWidthPx = imgWidthPx - imageCropWidthPx
                                        if (availableImageWidthPx > 0f) {
                                            val deltaPanX = pan.x / availableImageWidthPx
                                            panX = (currentPanX + deltaPanX).coerceIn(0f, 1f)
                                        }
                                    }
                                    if (maxTop > 0.0001f && imgHeightPx > 0f) {
                                        val imageCropHeightPx = imgHeightPx * hFrac
                                        val availableImageHeightPx = imgHeightPx - imageCropHeightPx
                                        if (availableImageHeightPx > 0f) {
                                            val deltaPanY = pan.y / availableImageHeightPx
                                            panY = (currentPanY + deltaPanY).coerceIn(0f, 1f)
                                        }
                                    }
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    if (zoom > 1.05f) {
                                        zoom = 1f
                                        panX = 0.5f
                                        panY = 0.5f
                                    } else {
                                        zoom = 2.5f
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Image + crop overlay
                    Box(
                        modifier = Modifier
                            .size(width = imgWidthDp, height = imgHeightDp)
                            .clip(RoundedCornerShape(6.dp))
                    ) {
                        // Wallpaper image preview
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap.asImageBitmap(),
                                contentDescription = wallpaper.displayTitle,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.FillBounds
                            )
                        } else {
                            WallpaperPreviewImage(
                                model = wallpaper.fullModel(),
                                placeholderModel = wallpaper.thumbnailUrl,
                                contentDescription = wallpaper.displayTitle,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.FillBounds,
                                clipShape = RoundedCornerShape(0.dp)
                            )
                        }

                        // Scrim, grid, border, corner handles
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            val cropLeft = (cropSettings.left * w).coerceIn(0f, w)
                            val cropTop = (cropSettings.top * h).coerceIn(0f, h)
                            val cropRight = (cropSettings.right * w).coerceIn(cropLeft, w)
                            val cropBottom = (cropSettings.bottom * h).coerceIn(cropTop, h)
                            val cropW = cropRight - cropLeft
                            val cropH = cropBottom - cropTop

                            // Outer dimmed scrim
                            val scrimColor = Color.Black.copy(alpha = 0.65f)
                            if (cropTop > 0f) drawRect(scrimColor, topLeft = Offset(0f, 0f), size = Size(w, cropTop))
                            if (cropBottom < h) drawRect(scrimColor, topLeft = Offset(0f, cropBottom), size = Size(w, h - cropBottom))
                            if (cropLeft > 0f) drawRect(scrimColor, topLeft = Offset(0f, cropTop), size = Size(cropLeft, cropH))
                            if (cropRight < w) drawRect(scrimColor, topLeft = Offset(cropRight, cropTop), size = Size(w - cropRight, cropH))

                            // Rule-of-thirds grid
                            val gridColor = Color.White.copy(alpha = 0.28f)
                            val gridStroke = 1.dp.toPx()
                            drawLine(gridColor, Offset(cropLeft, cropTop + cropH / 3f), Offset(cropRight, cropTop + cropH / 3f), gridStroke)
                            drawLine(gridColor, Offset(cropLeft, cropTop + cropH * 2f / 3f), Offset(cropRight, cropTop + cropH * 2f / 3f), gridStroke)
                            drawLine(gridColor, Offset(cropLeft + cropW / 3f, cropTop), Offset(cropLeft + cropW / 3f, cropBottom), gridStroke)
                            drawLine(gridColor, Offset(cropLeft + cropW * 2f / 3f, cropTop), Offset(cropLeft + cropW * 2f / 3f, cropBottom), gridStroke)

                            // Center crosshair
                            val crossColor = Color.White.copy(alpha = 0.55f)
                            val cx = cropLeft + cropW / 2f
                            val cy = cropTop + cropH / 2f
                            val cLen = 7.dp.toPx()
                            drawLine(crossColor, Offset(cx - cLen, cy), Offset(cx + cLen, cy), 1.5.dp.toPx())
                            drawLine(crossColor, Offset(cx, cy - cLen), Offset(cx, cy + cLen), 1.5.dp.toPx())

                            // Active frame border (brand pink)
                            drawRect(
                                color = Color(0xFFE91E63),
                                topLeft = Offset(cropLeft, cropTop),
                                size = Size(cropW, cropH),
                                style = Stroke(width = 2.dp.toPx())
                            )

                            // Corner L-brackets
                            val bLen = 16.dp.toPx()
                            val bStroke = 3.5.dp.toPx()
                            val bColor = Color.White
                            // Top-Left
                            drawLine(bColor, Offset(cropLeft, cropTop), Offset(cropLeft + bLen, cropTop), bStroke)
                            drawLine(bColor, Offset(cropLeft, cropTop), Offset(cropLeft, cropTop + bLen), bStroke)
                            // Top-Right
                            drawLine(bColor, Offset(cropRight - bLen, cropTop), Offset(cropRight, cropTop), bStroke)
                            drawLine(bColor, Offset(cropRight, cropTop), Offset(cropRight, cropTop + bLen), bStroke)
                            // Bottom-Left
                            drawLine(bColor, Offset(cropLeft, cropBottom - bLen), Offset(cropLeft, cropBottom), bStroke)
                            drawLine(bColor, Offset(cropLeft, cropBottom), Offset(cropLeft + bLen, cropBottom), bStroke)
                            // Bottom-Right
                            drawLine(bColor, Offset(cropRight, cropBottom - bLen), Offset(cropRight, cropBottom), bStroke)
                            drawLine(bColor, Offset(cropRight - bLen, cropBottom), Offset(cropRight, cropBottom), bStroke)
                        }
                    }

                    // Floating info chip
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.78f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        val infoText = if (selectedMode == FramingRatioMode.ORIGINAL) {
                            "Original uncropped view"
                        } else {
                            val pX = ((panX - 0.5f) * 200).roundToInt()
                            val pY = ((panY - 0.5f) * 200).roundToInt()
                            val signX = if (pX > 0) "+$pX%" else "$pX%"
                            val signY = if (pY > 0) "+$pY%" else "$pY%"
                            "X $signX  Y $signY  •  ${(zoom * 100).toInt()}%"
                        }
                        Text(
                            text = infoText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.88f),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // ── Bottom Controls Panel ─────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(top = 10.dp, bottom = 12.dp, start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Active section content
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (selectedSection) {
                            CropSection.RATIO -> {
                                val scrollState = rememberScrollState()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(scrollState),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FramingRatioMode.entries.forEach { mode ->
                                        val isSelected = selectedMode == mode
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                                selectedMode = mode
                                                if (mode == FramingRatioMode.ORIGINAL) {
                                                    zoom = 1f
                                                    panX = 0.5f
                                                    panY = 0.5f
                                                }
                                            },
                                            label = {
                                                Text(
                                                    text = mode.label,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = mode.icon,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                                                selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled = true,
                                                selected = isSelected,
                                                borderColor = if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                            ),
                                            shape = WallBaseShapes.pill
                                        )
                                    }
                                }
                            }

                            CropSection.ZOOM -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ZoomIn,
                                            contentDescription = "Zoom",
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "${(zoom * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.width(44.dp)
                                        )
                                        Slider(
                                            value = zoom,
                                            onValueChange = { zoom = it },
                                            valueRange = 1f..8f,
                                            modifier = Modifier.weight(1f),
                                            colors = SliderDefaults.colors(
                                                thumbColor = MaterialTheme.colorScheme.primary,
                                                activeTrackColor = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                        if (zoom > 1.02f) {
                                            TextButton(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                                    zoom = 1f
                                                    panX = 0.5f
                                                    panY = 0.5f
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp)
                                            ) {
                                                Text("1x", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }

                                    if (selectedMode == FramingRatioMode.FREE) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = "W: ${(freeWidthFrac * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.width(56.dp)
                                            )
                                            Slider(
                                                value = freeWidthFrac,
                                                onValueChange = { freeWidthFrac = it },
                                                valueRange = 0.1f..1f,
                                                modifier = Modifier.weight(1f),
                                                colors = SliderDefaults.colors(
                                                    thumbColor = MaterialTheme.colorScheme.primary,
                                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                                )
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = "H: ${(freeHeightFrac * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.width(56.dp)
                                            )
                                            Slider(
                                                value = freeHeightFrac,
                                                onValueChange = { freeHeightFrac = it },
                                                valueRange = 0.1f..1f,
                                                modifier = Modifier.weight(1f),
                                                colors = SliderDefaults.colors(
                                                    thumbColor = MaterialTheme.colorScheme.primary,
                                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section tabs (Ratio / Zoom only)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CropSection.entries.forEach { section ->
                            val isSelected = selectedSection == section
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    selectedSection = section
                                },
                                shape = WallBaseShapes.pill,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                } else {
                                    Color.Transparent
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = section.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = section.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
