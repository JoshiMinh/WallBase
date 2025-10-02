@file:Suppress("SameParameterValue")

package com.joshiminh.wallbase.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joshiminh.wallbase.ui.viewmodel.WallpaperDetailViewModel
import com.joshiminh.wallbase.util.wallpapers.WallpaperAdjustments
import com.joshiminh.wallbase.util.wallpapers.WallpaperCrop
import com.joshiminh.wallbase.util.wallpapers.WallpaperCropSettings
import com.joshiminh.wallbase.util.wallpapers.WallpaperFilter
import com.joshiminh.wallbase.util.wallpapers.displayName
import kotlin.math.roundToInt

@Composable
fun WallpaperEditSection(
    uiState: WallpaperDetailViewModel.WallpaperDetailUiState,
    onUpdateBrightness: (Float) -> Unit,
    onUpdateHue: (Float) -> Unit,
    onUpdateFilter: (WallpaperFilter) -> Unit,
    onUpdateCrop: (WallpaperCrop) -> Unit,
    onResetAdjustments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val adjustments = uiState.adjustments
    val isProcessing = uiState.isProcessingEdits
    val controlsEnabled = uiState.isEditorReady

    Surface(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Adjustments", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Tune the wallpaper before applying it to your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isProcessing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Updating preview…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AdjustmentSlider(
                label = "Brightness",
                value = adjustments.brightness,
                valueRange = -0.5f..0.5f,
                steps = 0,
                enabled = controlsEnabled,
                onValueChange = onUpdateBrightness,
                formatter = { value -> "${(value / 0.5f * 100).roundToInt()}%" }
            )

            AdjustmentSlider(
                label = "Hue",
                value = adjustments.hue,
                valueRange = -180f..180f,
                steps = 0,
                enabled = controlsEnabled,
                onValueChange = onUpdateHue,
                formatter = { value -> "${value.roundToInt()}°" }
            )

            FilterSelection(
                selected = adjustments.filter,
                enabled = controlsEnabled,
                onFilterSelected = onUpdateFilter
            )

            CropSelection(
                adjustments = adjustments,
                enabled = controlsEnabled,
                onUpdateCrop = onUpdateCrop
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onResetAdjustments,
                    enabled = controlsEnabled && !adjustments.isIdentity
                ) {
                    Text(text = "Reset adjustments")
                }
            }
        }
    }
}

@Composable
private fun AdjustmentSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    formatter: (Float) -> String,
) {
    val safeValue = if (value.isFinite()) value.coerceIn(valueRange.start, valueRange.endInclusive) else 0f

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = formatter(safeValue),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = safeValue,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSelection(
    selected: WallpaperFilter,
    enabled: Boolean,
    onFilterSelected: (WallpaperFilter) -> Unit,
) {
    val filters = WallpaperFilter.entries.toTypedArray()
    val maxItemsPerRow = 3
    val maxLines = ((filters.size + maxItemsPerRow - 1) / maxItemsPerRow).coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "Filter", style = MaterialTheme.typography.titleSmall)

        BoxWithConstraints {
            val density = LocalDensity.current
            val boundedWidth: Dp? = if (constraints.hasBoundedWidth) {
                with(density) { constraints.maxWidth.toDp() }
            } else {
                null // No widthIn when unbounded
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (boundedWidth != null) Modifier.widthIn(max = boundedWidth) else Modifier
                    ),
                maxItemsInEachRow = maxItemsPerRow,
                maxLines = maxLines,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.forEach { filter ->
                    FilterChip(
                        selected = selected == filter,
                        onClick = { if (enabled) onFilterSelected(filter) },
                        enabled = enabled,
                        label = { Text(text = filter.displayName()) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CropSelection(
    adjustments: WallpaperAdjustments,
    enabled: Boolean,
    onUpdateCrop: (WallpaperCrop) -> Unit,
) {
    val cropPresets = WallpaperCrop.presets
    val cropChipsCount = cropPresets.size + 1 // include "Custom"
    val maxItemsPerRow = 3
    val maxLines = ((cropChipsCount + maxItemsPerRow - 1) / maxItemsPerRow).coerceAtLeast(1)
    val isCustomSelected = adjustments.crop is WallpaperCrop.Custom

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "Crop", style = MaterialTheme.typography.titleSmall)

        BoxWithConstraints {
            val density = LocalDensity.current
            val boundedWidth: Dp? = if (constraints.hasBoundedWidth) {
                with(density) { constraints.maxWidth.toDp() }
            } else {
                null // No widthIn when unbounded
            }

            val currentSettings = when (val crop = adjustments.crop) {
                is WallpaperCrop.Custom -> crop.settings
                else -> adjustments.cropSettings ?: WallpaperCropSettings()
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (boundedWidth != null) Modifier.widthIn(max = boundedWidth) else Modifier
                    ),
                maxItemsInEachRow = maxItemsPerRow,
                maxLines = maxLines,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                cropPresets.forEach { crop ->
                    FilterChip(
                        selected = adjustments.crop == crop,
                        onClick = { if (enabled) onUpdateCrop(crop) },
                        enabled = enabled,
                        label = { Text(text = crop.displayName()) }
                    )
                }
                FilterChip(
                    selected = isCustomSelected,
                    onClick = { if (enabled) onUpdateCrop(WallpaperCrop.Custom(currentSettings)) },
                    enabled = enabled,
                    label = { Text(text = "Custom") }
                )
            }
        }

        if (isCustomSelected) {
            CustomCropControls(
                settings = adjustments.crop.settings,
                enabled = enabled,
                onUpdateCrop = onUpdateCrop
            )
        }
    }
}

@Composable
private fun CustomCropControls(
    settings: WallpaperCropSettings,
    enabled: Boolean,
    onUpdateCrop: (WallpaperCrop) -> Unit,
) {
    var horizontal by remember(settings) {
        mutableStateOf(settings.left..settings.right)
    }
    var vertical by remember(settings) {
        mutableStateOf(settings.top..settings.bottom)
    }

    LaunchedEffect(settings) {
        horizontal = settings.left..settings.right
        vertical = settings.top..settings.bottom
    }

    fun applyCrop(
        horizontalRange: ClosedFloatingPointRange<Float>,
        verticalRange: ClosedFloatingPointRange<Float>
    ) {
        val sanitized = WallpaperCropSettings(
            left = horizontalRange.start,
            top = verticalRange.start,
            right = horizontalRange.endInclusive,
            bottom = verticalRange.endInclusive
        ).sanitized()

        val sanitizedHorizontal = sanitized.left..sanitized.right
        val sanitizedVertical = sanitized.top..sanitized.bottom

        if (horizontal == sanitizedHorizontal && vertical == sanitizedVertical) return

        horizontal = sanitizedHorizontal
        vertical = sanitizedVertical
        onUpdateCrop(WallpaperCrop.Custom(sanitized))
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Adjust the crop window",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Horizontal", style = MaterialTheme.typography.labelLarge)
            RangeSlider(
                value = horizontal.coerceToFiniteIn(0f..1f),
                onValueChange = { range ->
                    if (!enabled) return@RangeSlider
                    if (range.isFiniteIn(0f..1f)) applyCrop(range, vertical)
                },
                valueRange = 0f..1f,
                steps = 0,
                enabled = enabled
            )
            RangeLabel(range = horizontal.coerceToFiniteIn(0f..1f))
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Vertical", style = MaterialTheme.typography.labelLarge)
            RangeSlider(
                value = vertical.coerceToFiniteIn(0f..1f),
                onValueChange = { range ->
                    if (!enabled) return@RangeSlider
                    if (range.isFiniteIn(0f..1f)) applyCrop(horizontal, range)
                },
                valueRange = 0f..1f,
                steps = 0,
                enabled = enabled
            )
            RangeLabel(range = vertical.coerceToFiniteIn(0f..1f))
        }
    }
}

@Composable
private fun RangeLabel(range: ClosedFloatingPointRange<Float>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Start: ${formatPercentage(range.start)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "End: ${formatPercentage(range.endInclusive)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun WallpaperFilter.displayName(): String = when (this) {
    WallpaperFilter.NONE -> "None"
    WallpaperFilter.GRAYSCALE -> "Grayscale"
    WallpaperFilter.SEPIA -> "Sepia"
}

/* ---------- Small safety helpers ---------- */

private fun Float.isFinite(): Boolean = !(isNaN() || this == Float.POSITIVE_INFINITY || this == Float.NEGATIVE_INFINITY)

private fun ClosedFloatingPointRange<Float>.isFiniteIn(
    range: ClosedFloatingPointRange<Float>
): Boolean =
    start.isFinite() && endInclusive.isFinite() &&
            start >= range.start && endInclusive <= range.endInclusive && start <= endInclusive

private fun ClosedFloatingPointRange<Float>.coerceToFiniteIn(
    range: ClosedFloatingPointRange<Float>
): ClosedFloatingPointRange<Float> {
    val s = if (start.isFinite()) start.coerceIn(range.start, range.endInclusive) else range.start
    val e = if (endInclusive.isFinite()) endInclusive.coerceIn(range.start, range.endInclusive) else range.endInclusive
    return if (s <= e) s..e else range.start..range.endInclusive
}

private fun formatPercentage(value: Float): String =
    if (value.isFinite()) "${(value * 100).roundToInt()}%" else "—"