package com.joshiminh.wallbase.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.outlined.AlignHorizontalLeft
import androidx.compose.material.icons.automirrored.outlined.AlignHorizontalRight
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Crop169
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.CropLandscape
import androidx.compose.material.icons.outlined.CropOriginal
import androidx.compose.material.icons.outlined.CropPortrait
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.VerticalAlignTop
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joshiminh.wallbase.data.entity.WallpaperItem
import com.joshiminh.wallbase.ui.theme.WallBaseShapes
import com.joshiminh.wallbase.util.wallpapers.WallpaperCrop
import com.joshiminh.wallbase.util.wallpapers.WallpaperCropSettings
import kotlin.math.abs
import kotlin.math.roundToInt

enum class FramingRatioMode(
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
    val fixedRatio: Float? = null
) {
    SCREEN("Screen", "Device screen", Icons.Outlined.Smartphone, null),
    PORTRAIT_9_16("9:16", "Story / Reel", Icons.Outlined.CropPortrait, 9f / 16f),
    SQUARE_1_1("1:1", "Square", Icons.Outlined.CropSquare, 1f),
    LANDSCAPE_16_9("16:9", "Widescreen", Icons.Outlined.Crop169, 16f / 9f),
    PORTRAIT_3_4("3:4", "Portrait 3:4", Icons.Outlined.CropPortrait, 3f / 4f),
    LANDSCAPE_4_3("4:3", "Landscape 4:3", Icons.Outlined.CropLandscape, 4f / 3f),
    ORIGINAL("Original", "Full image", Icons.Outlined.CropOriginal, null),
    FREE("Free", "Custom frame", Icons.Outlined.CropFree, null)
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

@Composable
fun WallpaperCropDialog(
    currentCrop: WallpaperCrop,
    wallpaper: WallpaperItem,
    previewBitmap: Bitmap? = null,
    onSelectCrop: (WallpaperCrop) -> Unit,
    onDismiss: () -> Unit
) {
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
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Quick Align, 1 = Precision Sliders

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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0C0D14))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // 1. Top Header Bar (Frosted glass)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    tonalElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
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

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Frame Alignment",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${selectedMode.label} • Zoom ${(zoom * 100).toInt()}% • Drag or pinch",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = {
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
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // 2. Interactive Canvas Viewport
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF090A0F))
                        .pointerInput(selectedMode, zoom, panX, panY, cropSettings) {
                            detectTransformGestures { _, pan, zoomChange, _ ->
                                if (selectedMode != FramingRatioMode.ORIGINAL) {
                                    // Update zoom
                                    val newZoom = (zoom * zoomChange).coerceIn(1f, 4f)
                                    zoom = newZoom

                                    // Update pan
                                    val wFrac = cropSettings.widthFraction()
                                    val hFrac = cropSettings.heightFraction()
                                    val availX = (1f - wFrac).coerceAtLeast(0.001f)
                                    val availY = (1f - hFrac).coerceAtLeast(0.001f)

                                    val sens = 1.3f
                                    val deltaPanX = (-pan.x / (size.width * availX)) * sens
                                    val deltaPanY = (-pan.y / (size.height * availY)) * sens

                                    panX = (panX + deltaPanX).coerceIn(0f, 1f)
                                    panY = (panY + deltaPanY).coerceIn(0f, 1f)
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (zoom > 1.1f) {
                                        zoom = 1f
                                        panX = 0.5f
                                        panY = 0.5f
                                    } else {
                                        zoom = 2f
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val containerWidth = constraints.maxWidth.toFloat()
                    val containerHeight = constraints.maxHeight.toFloat()

                    val (imgWidthPx, imgHeightPx) = remember(containerWidth, containerHeight, imageRatio) {
                        val containerRatio = if (containerHeight > 0) containerWidth / containerHeight else 1f
                        if (containerRatio > imageRatio) {
                            val h = containerHeight * 0.90f
                            Pair(h * imageRatio, h)
                        } else {
                            val w = containerWidth * 0.90f
                            Pair(w, w / imageRatio)
                        }
                    }

                    val density = LocalDensity.current
                    val imgWidthDp = with(density) { imgWidthPx.toDp() }
                    val imgHeightDp = with(density) { imgHeightPx.toDp() }

                    // Image and Crop Overlay
                    Box(
                        modifier = Modifier
                            .size(width = imgWidthDp, height = imgHeightDp)
                            .clip(RoundedCornerShape(6.dp))
                    ) {
                        // Wallpaper image
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap.asImageBitmap(),
                                contentDescription = wallpaper.displayTitle,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.FillBounds
                            )
                        } else {
                            WallpaperPreviewImage(
                                model = wallpaper.previewModel(),
                                contentDescription = wallpaper.displayTitle,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.FillBounds,
                                clipShape = RoundedCornerShape(0.dp)
                            )
                        }

                        // Canvas overlay for crop box, scrim, 3x3 grid, and corner handles
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
                            if (cropTop > 0f) {
                                drawRect(scrimColor, topLeft = Offset(0f, 0f), size = Size(w, cropTop))
                            }
                            if (cropBottom < h) {
                                drawRect(scrimColor, topLeft = Offset(0f, cropBottom), size = Size(w, h - cropBottom))
                            }
                            if (cropLeft > 0f) {
                                drawRect(scrimColor, topLeft = Offset(0f, cropTop), size = Size(cropLeft, cropH))
                            }
                            if (cropRight < w) {
                                drawRect(scrimColor, topLeft = Offset(cropRight, cropTop), size = Size(w - cropRight, cropH))
                            }

                            // 3x3 Rule-of-Thirds Grid inside active frame
                            val gridColor = Color.White.copy(alpha = 0.30f)
                            val strokeGrid = Stroke(width = 1.dp.toPx())
                            // Horizontal lines
                            drawLine(gridColor, Offset(cropLeft, cropTop + cropH / 3f), Offset(cropRight, cropTop + cropH / 3f), strokeWidth = strokeGrid.width)
                            drawLine(gridColor, Offset(cropLeft, cropTop + cropH * 2f / 3f), Offset(cropRight, cropTop + cropH * 2f / 3f), strokeWidth = strokeGrid.width)
                            // Vertical lines
                            drawLine(gridColor, Offset(cropLeft + cropW / 3f, cropTop), Offset(cropLeft + cropW / 3f, cropBottom), strokeWidth = strokeGrid.width)
                            drawLine(gridColor, Offset(cropLeft + cropW * 2f / 3f, cropTop), Offset(cropLeft + cropW * 2f / 3f, cropBottom), strokeWidth = strokeGrid.width)

                            // Center crosshair
                            val crosshairColor = Color.White.copy(alpha = 0.55f)
                            val centerX = cropLeft + cropW / 2f
                            val centerY = cropTop + cropH / 2f
                            val crosshairLen = 7.dp.toPx()
                            drawLine(crosshairColor, Offset(centerX - crosshairLen, centerY), Offset(centerX + crosshairLen, centerY), strokeWidth = 1.5.dp.toPx())
                            drawLine(crosshairColor, Offset(centerX, centerY - crosshairLen), Offset(centerX, centerY + crosshairLen), strokeWidth = 1.5.dp.toPx())

                            // Active Frame Border (Fixed Pink / Brand Accent)
                            val frameBorderColor = Color(0xFFE91E63)
                            drawRect(
                                color = frameBorderColor,
                                topLeft = Offset(cropLeft, cropTop),
                                size = Size(cropW, cropH),
                                style = Stroke(width = 2.dp.toPx())
                            )

                            // Corner Brackets for tactile aesthetic
                            val bracketLen = 16.dp.toPx()
                            val bracketStroke = 3.5.dp.toPx()
                            val bracketColor = Color.White

                            // Top-Left
                            drawLine(bracketColor, Offset(cropLeft - 1f, cropTop), Offset(cropLeft + bracketLen, cropTop), strokeWidth = bracketStroke)
                            drawLine(bracketColor, Offset(cropLeft, cropTop - 1f), Offset(cropLeft, cropTop + bracketLen), strokeWidth = bracketStroke)

                            // Top-Right
                            drawLine(bracketColor, Offset(cropRight - bracketLen, cropTop), Offset(cropRight + 1f, cropTop), strokeWidth = bracketStroke)
                            drawLine(bracketColor, Offset(cropRight, cropTop - 1f), Offset(cropRight, cropTop + bracketLen), strokeWidth = bracketStroke)

                            // Bottom-Left
                            drawLine(bracketColor, Offset(cropLeft - 1f, cropBottom), Offset(cropLeft + bracketLen, cropBottom), strokeWidth = bracketStroke)
                            drawLine(bracketColor, Offset(cropLeft, cropBottom - bracketLen), Offset(cropLeft, cropBottom + 1f), strokeWidth = bracketStroke)

                            // Bottom-Right
                            drawLine(bracketColor, Offset(cropRight - bracketLen, cropBottom), Offset(cropRight + 1f, cropBottom), strokeWidth = bracketStroke)
                            drawLine(bracketColor, Offset(cropRight, cropBottom - bracketLen), Offset(cropRight, cropBottom + 1f), strokeWidth = bracketStroke)
                        }
                    }

                    // Floating info chip at bottom of canvas
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.75f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        Text(
                            text = if (selectedMode == FramingRatioMode.ORIGINAL) "Original uncropped view"
                            else "Pos: ${((panX - 0.5f) * 200).roundToInt()}% X, ${((panY - 0.5f) * 200).roundToInt()}% Y",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                // 3. Bottom Controls Panel (Frosted glass)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Ratio Chips Scrollable Row
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
                                        borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    ),
                                    shape = WallBaseShapes.pill
                                )
                            }
                        }

                        // Tab Switcher between Quick Align and Precision Sliders
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.primary,
                            indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            divider = {}
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.CenterFocusStrong,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text("Quick Align", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Tune,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text("Precision & Zoom", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            )
                        }

                        // Tab Content
                        if (selectedTab == 0) {
                            // Quick Alignment Buttons Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                QuickAlignAction(
                                    icon = Icons.AutoMirrored.Outlined.AlignHorizontalLeft,
                                    label = "Left",
                                    onClick = { panX = 0f }
                                )
                                QuickAlignAction(
                                    icon = Icons.Outlined.VerticalAlignTop,
                                    label = "Top",
                                    onClick = { panY = 0f }
                                )
                                QuickAlignAction(
                                    icon = Icons.Outlined.CenterFocusStrong,
                                    label = "Center",
                                    onClick = {
                                        panX = 0.5f
                                        panY = 0.5f
                                    }
                                )
                                QuickAlignAction(
                                    icon = Icons.Outlined.VerticalAlignBottom,
                                    label = "Bottom",
                                    onClick = { panY = 1f }
                                )
                                QuickAlignAction(
                                    icon = Icons.AutoMirrored.Outlined.AlignHorizontalRight,
                                    label = "Right",
                                    onClick = { panX = 1f }
                                )
                            }
                        } else {
                            // Precision Sliders Column
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Zoom Slider
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ZoomIn,
                                        contentDescription = "Zoom",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Scale ${(zoom * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.width(76.dp)
                                    )
                                    Slider(
                                        value = zoom,
                                        onValueChange = { zoom = it },
                                        valueRange = 1f..4f,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }

                                // Horizontal Pan Slider
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.AlignHorizontalLeft,
                                        contentDescription = "Pan X",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "X: ${(panX * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.width(76.dp)
                                    )
                                    Slider(
                                        value = panX,
                                        onValueChange = { panX = it },
                                        valueRange = 0f..1f,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }

                                // Vertical Pan Slider
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.VerticalAlignTop,
                                        contentDescription = "Pan Y",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Y: ${(panY * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.width(76.dp)
                                    )
                                    Slider(
                                        value = panY,
                                        onValueChange = { panY = it },
                                        valueRange = 0f..1f,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }

                                if (selectedMode == FramingRatioMode.FREE) {
                                    // Free Width & Height Sliders
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "Width ${(freeWidthFrac * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.width(86.dp)
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
                                }
                            }
                        }

                        // 4. Primary CTA: Apply Framing Button
                        Button(
                            onClick = {
                                val finalCrop = when {
                                    selectedMode == FramingRatioMode.ORIGINAL -> WallpaperCrop.Original
                                    selectedMode == FramingRatioMode.SQUARE_1_1 && zoom <= 1.01f && abs(panX - 0.5f) < 0.01f && abs(panY - 0.5f) < 0.01f -> WallpaperCrop.Square
                                    selectedMode == FramingRatioMode.SCREEN && zoom <= 1.01f && abs(panX - 0.5f) < 0.01f && abs(panY - 0.5f) < 0.01f -> WallpaperCrop.Auto
                                    else -> WallpaperCrop.Custom(cropSettings)
                                }
                                onSelectCrop(finalCrop)
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Apply Framing",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAlignAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.size(54.dp, 48.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
