package com.joshiminh.wallbase.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Crop169
import androidx.compose.material.icons.outlined.CropOriginal
import androidx.compose.material.icons.outlined.CropPortrait
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joshiminh.wallbase.data.entity.WallpaperItem
import com.joshiminh.wallbase.ui.theme.WallBaseShapes
import com.joshiminh.wallbase.util.wallpapers.WallpaperCrop
import com.joshiminh.wallbase.util.wallpapers.WallpaperCropSettings

private enum class CropPresetOption(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    AUTO(
        title = "Auto (Fit Screen)",
        subtitle = "Crop to match device screen aspect ratio",
        icon = Icons.Outlined.AspectRatio
    ),
    ORIGINAL(
        title = "Original (No Crop)",
        subtitle = "Preserve full uncropped image",
        icon = Icons.Outlined.CropOriginal
    ),
    SQUARE(
        title = "Square (1:1)",
        subtitle = "Centered square crop",
        icon = Icons.Outlined.CropSquare
    ),
    PORTRAIT_9_16(
        title = "Portrait (9:16)",
        subtitle = "Standard vertical phone aspect ratio",
        icon = Icons.Outlined.CropPortrait
    ),
    LANDSCAPE_16_9(
        title = "Landscape (16:9)",
        subtitle = "Standard widescreen aspect ratio",
        icon = Icons.Outlined.Crop169
    )
}

@Composable
fun WallpaperCropDialog(
    currentCrop: WallpaperCrop,
    wallpaper: WallpaperItem,
    onSelectCrop: (WallpaperCrop) -> Unit,
    onDismiss: () -> Unit
) {
    val imageAspectRatio = wallpaper.aspectRatio ?: 1f

    val currentPreset = when (currentCrop) {
        WallpaperCrop.Auto -> CropPresetOption.AUTO
        WallpaperCrop.Original -> CropPresetOption.ORIGINAL
        WallpaperCrop.Square -> CropPresetOption.SQUARE
        is WallpaperCrop.Custom -> {
            val ratio = currentCrop.settings.aspectRatio()
            when {
                kotlin.math.abs(ratio - (9f / 16f)) < 0.05f -> CropPresetOption.PORTRAIT_9_16
                kotlin.math.abs(ratio - (16f / 9f)) < 0.05f -> CropPresetOption.LANDSCAPE_16_9
                kotlin.math.abs(ratio - 1f) < 0.05f -> CropPresetOption.SQUARE
                else -> null
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = WallBaseShapes.dialog,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Crop,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "Set Wallpaper Crop",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CropPresetOption.entries.forEach { option ->
                    val isSelected = currentPreset == option
                    val targetCrop: WallpaperCrop = when (option) {
                        CropPresetOption.AUTO -> WallpaperCrop.Auto
                        CropPresetOption.ORIGINAL -> WallpaperCrop.Original
                        CropPresetOption.SQUARE -> WallpaperCrop.Square
                        CropPresetOption.PORTRAIT_9_16 -> WallpaperCrop.Custom(
                            WallpaperCropSettings.centeredForAspectRatio(9f / 16f, imageAspectRatio)
                        )
                        CropPresetOption.LANDSCAPE_16_9 -> WallpaperCrop.Custom(
                            WallpaperCropSettings.centeredForAspectRatio(16f / 9f, imageAspectRatio)
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectCrop(targetCrop)
                                onDismiss()
                            },
                        shape = WallBaseShapes.card,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = null,
                                tint = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Text(
                                    text = option.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onSelectCrop(targetCrop)
                                    onDismiss()
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
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
