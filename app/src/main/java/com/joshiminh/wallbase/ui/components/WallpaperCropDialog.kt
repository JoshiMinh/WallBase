@file:Suppress("DEPRECATION")

package com.joshiminh.wallbase.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlignHorizontalLeft
import androidx.compose.material.icons.outlined.AlignHorizontalRight
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.CropOriginal
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.VerticalAlignTop
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joshiminh.wallbase.data.entity.WallpaperItem
import com.joshiminh.wallbase.ui.theme.WallBaseShapes
import com.joshiminh.wallbase.util.wallpapers.WallpaperCrop
import com.joshiminh.wallbase.util.wallpapers.WallpaperCropSettings

private enum class FramingPresetOption(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val crop: WallpaperCrop
) {
    CENTER(
        title = "Center (Default)",
        subtitle = "Center the image inside the wallpaper box",
        icon = Icons.Outlined.CenterFocusStrong,
        crop = WallpaperCrop.Auto
    ),
    TOP(
        title = "Top",
        subtitle = "Align towards the top portion of the image",
        icon = Icons.Outlined.VerticalAlignTop,
        crop = WallpaperCrop.Custom(WallpaperCropSettings.FramingTop)
    ),
    BOTTOM(
        title = "Bottom",
        subtitle = "Align towards the bottom portion of the image",
        icon = Icons.Outlined.VerticalAlignBottom,
        crop = WallpaperCrop.Custom(WallpaperCropSettings.FramingBottom)
    ),
    LEFT(
        title = "Left / Start",
        subtitle = "Align towards the left portion of the image",
        icon = Icons.Outlined.AlignHorizontalLeft,
        crop = WallpaperCrop.Custom(WallpaperCropSettings.FramingLeft)
    ),
    RIGHT(
        title = "Right / End",
        subtitle = "Align towards the right portion of the image",
        icon = Icons.Outlined.AlignHorizontalRight,
        crop = WallpaperCrop.Custom(WallpaperCropSettings.FramingRight)
    ),
    SQUARE(
        title = "Square Frame (1:1)",
        subtitle = "Display inside a 1:1 square frame",
        icon = Icons.Outlined.CropSquare,
        crop = WallpaperCrop.Square
    ),
    ORIGINAL(
        title = "Original Fit",
        subtitle = "Fit the entire uncropped image within the frame",
        icon = Icons.Outlined.CropOriginal,
        crop = WallpaperCrop.Original
    )
}

@Composable
fun WallpaperCropDialog(
    currentCrop: WallpaperCrop,
    wallpaper: WallpaperItem,
    onSelectCrop: (WallpaperCrop) -> Unit,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    val currentOption = when (currentCrop) {
        WallpaperCrop.Auto -> FramingPresetOption.CENTER
        WallpaperCrop.Original -> FramingPresetOption.ORIGINAL
        WallpaperCrop.Square -> FramingPresetOption.SQUARE
        is WallpaperCrop.Custom -> {
            val s = currentCrop.settings
            when {
                s.top == 0f && s.bottom < 0.95f -> FramingPresetOption.TOP
                s.top > 0.05f && s.bottom >= 0.99f -> FramingPresetOption.BOTTOM
                s.left == 0f && s.right < 0.95f -> FramingPresetOption.LEFT
                s.left > 0.05f && s.right >= 0.99f -> FramingPresetOption.RIGHT
                else -> FramingPresetOption.CENTER
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
                text = "Frame Alignment",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FramingPresetOption.entries.forEach { option ->
                    val isSelected = currentOption == option

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = {
                                    onSelectCrop(option.crop)
                                    onDismiss()
                                }
                            ),
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
                                onClick = null,
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
