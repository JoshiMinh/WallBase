package com.joshiminh.wallbase.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewQuilt
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.ViewStream
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joshiminh.wallbase.data.repository.WallpaperLayout
import com.joshiminh.wallbase.ui.theme.WallBaseShapes
import com.joshiminh.wallbase.util.DownloadedFilter
import com.joshiminh.wallbase.util.SortDirection
import com.joshiminh.wallbase.util.SortField
import com.joshiminh.wallbase.util.SortSelection
import com.joshiminh.wallbase.util.defaultDirection
import com.joshiminh.wallbase.util.displayName
import com.joshiminh.wallbase.util.toggle

enum class SheetTab(val label: String) {
    FILTER("Filter"),
    SORT("Sort"),
    DISPLAY("Display")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewFilterSortBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    // Tabs configuration
    availableTabs: List<SheetTab> = listOf(SheetTab.FILTER, SheetTab.SORT, SheetTab.DISPLAY),
    initialTab: SheetTab = SheetTab.SORT,
    // Sort parameters
    sortSelection: SortSelection,
    availableSortFields: List<SortField>,
    onSortSelectionChanged: (SortSelection) -> Unit,
    // Filter parameters (optional)
    downloadedFilter: DownloadedFilter? = null,
    onDownloadedFilterChanged: ((DownloadedFilter) -> Unit)? = null,
    favoritesOnly: Boolean = false,
    onFavoritesOnlyChanged: ((Boolean) -> Unit)? = null,
    // Display parameters (optional)
    wallpaperLayout: WallpaperLayout? = null,
    onWallpaperLayoutChanged: ((WallpaperLayout) -> Unit)? = null,
    gridColumns: Int? = null,
    onGridColumnsChanged: ((Int) -> Unit)? = null,
    // Additional custom tab or content if needed
    customDisplayContent: (@Composable () -> Unit)? = null
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTabIndex by rememberSaveable(visible) {
        val defaultIdx = availableTabs.indexOf(initialTab).takeIf { it >= 0 } ?: 0
        mutableIntStateOf(defaultIdx)
    }

    val currentTab = availableTabs.getOrElse(selectedTabIndex) { availableTabs.firstOrNull() ?: SheetTab.SORT }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            // Header Tab Row
            if (availableTabs.size > 1) {
                PrimaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) }
                ) {
                    availableTabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }

            // Tab Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (currentTab) {
                    SheetTab.FILTER -> {
                        FilterTabContent(
                            downloadedFilter = downloadedFilter,
                            onDownloadedFilterChanged = onDownloadedFilterChanged,
                            favoritesOnly = favoritesOnly,
                            onFavoritesOnlyChanged = onFavoritesOnlyChanged
                        )
                    }
                    SheetTab.SORT -> {
                        SortTabContent(
                            selection = sortSelection,
                            availableFields = availableSortFields,
                            onSelectionChanged = onSortSelectionChanged
                        )
                    }
                    SheetTab.DISPLAY -> {
                        DisplayTabContent(
                            wallpaperLayout = wallpaperLayout,
                            onWallpaperLayoutChanged = onWallpaperLayoutChanged,
                            gridColumns = gridColumns,
                            onGridColumnsChanged = onGridColumnsChanged,
                            customContent = customDisplayContent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterTabContent(
    downloadedFilter: DownloadedFilter?,
    onDownloadedFilterChanged: ((DownloadedFilter) -> Unit)?,
    favoritesOnly: Boolean,
    onFavoritesOnlyChanged: ((Boolean) -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (downloadedFilter != null && onDownloadedFilterChanged != null) {
            Text(
                text = "Download Status",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Surface(
                shape = WallBaseShapes.card,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    DownloadedFilterOption(
                        label = "All Wallpapers",
                        selected = downloadedFilter == DownloadedFilter.SHOW_ALL,
                        onClick = { onDownloadedFilterChanged(DownloadedFilter.SHOW_ALL) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    DownloadedFilterOption(
                        label = "Downloaded Only",
                        selected = downloadedFilter == DownloadedFilter.SHOW_DOWNLOADED,
                        onClick = { onDownloadedFilterChanged(DownloadedFilter.SHOW_DOWNLOADED) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    DownloadedFilterOption(
                        label = "Not Downloaded",
                        selected = downloadedFilter == DownloadedFilter.HIDE_DOWNLOADED,
                        onClick = { onDownloadedFilterChanged(DownloadedFilter.HIDE_DOWNLOADED) }
                    )
                }
            }
        }

        if (onFavoritesOnlyChanged != null) {
            Text(
                text = "Collection",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Surface(
                shape = WallBaseShapes.card,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(WallBaseShapes.card)
                        .clickable(role = Role.Checkbox) { onFavoritesOnlyChanged(!favoritesOnly) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (favoritesOnly) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            tint = if (favoritesOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Favorites Only",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Checkbox(
                        checked = favoritesOnly,
                        onCheckedChange = null
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadedFilterOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
        RadioButton(
            selected = selected,
            onClick = null
        )
    }
}

@Composable
private fun SortTabContent(
    selection: SortSelection,
    availableFields: List<SortField>,
    onSelectionChanged: (SortSelection) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Sort Order",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )

        Surface(
            shape = WallBaseShapes.card,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                availableFields.forEachIndexed { index, field ->
                    val isSelected = selection.field == field
                    val direction = selection.direction.takeIf { isSelected }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val newDirection = if (isSelected) {
                                    selection.direction.toggle()
                                } else {
                                    field.defaultDirection()
                                }
                                onSelectionChanged(SortSelection(field, newDirection))
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = field.icon(),
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column {
                                Text(
                                    text = field.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                                if (direction != null) {
                                    Text(
                                        text = direction.description(field),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (direction != null) {
                            Icon(
                                imageVector = if (direction == SortDirection.Ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                                contentDescription = direction.description(field),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (index < availableFields.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DisplayTabContent(
    wallpaperLayout: WallpaperLayout?,
    onWallpaperLayoutChanged: ((WallpaperLayout) -> Unit)?,
    gridColumns: Int?,
    onGridColumnsChanged: ((Int) -> Unit)?,
    customContent: (@Composable () -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (wallpaperLayout != null && onWallpaperLayoutChanged != null) {
            Text(
                text = "Display Mode",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LayoutModeChip(
                    label = "Grid",
                    icon = Icons.Outlined.GridView,
                    selected = wallpaperLayout == WallpaperLayout.GRID,
                    onClick = { onWallpaperLayoutChanged(WallpaperLayout.GRID) },
                    modifier = Modifier.weight(1f)
                )
                LayoutModeChip(
                    label = "Justified",
                    icon = Icons.AutoMirrored.Outlined.ViewQuilt,
                    selected = wallpaperLayout == WallpaperLayout.JUSTIFIED,
                    onClick = { onWallpaperLayoutChanged(WallpaperLayout.JUSTIFIED) },
                    modifier = Modifier.weight(1f)
                )
                LayoutModeChip(
                    label = "List",
                    icon = Icons.Outlined.ViewStream,
                    selected = wallpaperLayout == WallpaperLayout.LIST,
                    onClick = { onWallpaperLayoutChanged(WallpaperLayout.LIST) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (gridColumns != null && onGridColumnsChanged != null && (wallpaperLayout == null || wallpaperLayout == WallpaperLayout.GRID)) {
            Text(
                text = "Items per row",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Surface(
                shape = WallBaseShapes.card,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Grid Columns",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$gridColumns",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = gridColumns.toFloat(),
                        onValueChange = { onGridColumnsChanged(it.toInt()) },
                        valueRange = 1f..3f,
                        steps = 1,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        customContent?.invoke()
    }
}

@Composable
private fun LayoutModeChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        shape = WallBaseShapes.control,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = modifier.height(44.dp)
    )
}

private fun SortField.icon(): ImageVector = when (this) {
    SortField.Alphabet -> Icons.Outlined.SortByAlpha
    SortField.DateAdded -> Icons.Outlined.Schedule
}

private fun SortDirection.description(field: SortField): String = when (field) {
    SortField.Alphabet -> if (this == SortDirection.Ascending) "A → Z" else "Z → A"
    SortField.DateAdded -> if (this == SortDirection.Descending) "Newest first" else "Oldest first"
}
