package com.joshiminh.wallbase.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewQuilt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.joshiminh.wallbase.data.repository.AlbumLayout
import com.joshiminh.wallbase.data.repository.WallpaperLayout

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GridColumnPicker(
    label: String,
    selectedColumns: Int,
    onColumnsSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 1..3,
    enabled: Boolean = true
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            range.forEach { count ->
                val selected = selectedColumns == count
                FilterChip(
                    selected = selected,
                    onClick = { if (enabled) onColumnsSelected(count) },
                    enabled = enabled,
                    label = {
                        val suffix = if (count == 1) "column" else "columns"
                        Text(text = "$count $suffix")
                    },
                    leadingIcon = if (selected) {
                        { Icon(imageVector = Icons.Outlined.Check, contentDescription = null) }
                    } else null
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WallpaperLayoutPicker(
    label: String,
    selectedLayout: WallpaperLayout,
    onLayoutSelected: (WallpaperLayout) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            WallpaperLayout.entries.forEach { layout ->
                val (title, description, icon) = when (layout) {
                    WallpaperLayout.GRID -> Triple("Grid", "Balanced rows", Icons.Outlined.GridView)
                    WallpaperLayout.JUSTIFIED -> Triple("Justified", "Adaptive collage",
                        Icons.AutoMirrored.Outlined.ViewQuilt
                    )
                    WallpaperLayout.LIST -> Triple("List", "Large previews", Icons.Outlined.ViewAgenda)
                }
                LayoutChoiceCard(
                    title = title,
                    description = description,
                    selected = selectedLayout == layout,
                    onClick = { onLayoutSelected(layout) },
                    icon = icon
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlbumLayoutPicker(
    label: String,
    selectedLayout: AlbumLayout,
    onLayoutSelected: (AlbumLayout) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AlbumLayout.entries.forEach { layout ->
                val (title, description, icon) = when (layout) {
                    AlbumLayout.GRID -> Triple("Grid", "Compact tiles", Icons.Outlined.GridView)
                    AlbumLayout.CARD_LIST -> Triple("Card list", "Spacious previews", Icons.Outlined.ViewAgenda)
                }
                LayoutChoiceCard(
                    title = title,
                    description = description,
                    selected = selectedLayout == layout,
                    onClick = { onLayoutSelected(layout) },
                    icon = icon
                )
            }
        }
    }
}

@Composable
private fun LayoutChoiceCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.widthIn(min = 160.dp),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = if (selected) 8.dp else 0.dp,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .widthIn(min = 160.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = selected) {
                Icon(imageVector = Icons.Outlined.CheckCircle, contentDescription = null)
            }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}