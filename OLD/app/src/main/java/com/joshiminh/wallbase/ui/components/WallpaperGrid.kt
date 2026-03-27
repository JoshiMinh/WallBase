package com.joshiminh.wallbase.ui.components

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem
import com.joshiminh.wallbase.data.repository.WallpaperLayout
import kotlinx.coroutines.flow.distinctUntilChanged

private const val UNKNOWN_PROVIDER_KEY = ""
private const val MAX_JUSTIFIED_ITEMS_PER_ROW = 3

private data class JustifiedRow(
    val items: List<WallpaperItem>,
    val startIndex: Int
) {
    val endIndex: Int = if (items.isEmpty()) startIndex else startIndex + items.size - 1
}

@Composable
private fun rememberJustifiedRows(
    wallpapers: List<WallpaperItem>,
    containerWidth: Dp,
    rowSpacing: Dp,
    targetRowHeight: Dp
): List<JustifiedRow> = remember(wallpapers, containerWidth, rowSpacing, targetRowHeight) {
    buildJustifiedRows(
        wallpapers = wallpapers,
        containerWidth = containerWidth,
        rowSpacing = rowSpacing,
        targetRowHeight = targetRowHeight
    )
}

private fun buildJustifiedRows(
    wallpapers: List<WallpaperItem>,
    containerWidth: Dp,
    rowSpacing: Dp,
    targetRowHeight: Dp
): List<JustifiedRow> {
    if (wallpapers.isEmpty()) return emptyList()
    if (containerWidth <= 0.dp) {
        return wallpapers.mapIndexed { index, item ->
            JustifiedRow(items = listOf(item), startIndex = index)
        }
    }
    val effectiveTargetHeight = if (targetRowHeight > 0.dp) targetRowHeight else 1.dp
    val rows = mutableListOf<JustifiedRow>()
    var currentItems = mutableListOf<WallpaperItem>()
    var ratioSum = 0f
    var rowStartIndex = 0
    var preferredRowSize = MAX_JUSTIFIED_ITEMS_PER_ROW

    wallpapers.forEachIndexed { index, item ->
        val ratio = item.aspectRatio?.takeIf { it > 0f } ?: DEFAULT_ASPECT_RATIO
        currentItems.add(item)
        ratioSum += ratio

        val spacingCount = (currentItems.size - 1).coerceAtLeast(0)
        val spacingTotal = rowSpacing * spacingCount
        val availableWidth = (containerWidth - spacingTotal).coerceAtLeast(0.dp)
        val threshold = (availableWidth / effectiveTargetHeight).coerceAtLeast(1f)

        val isLastItem = index == wallpapers.lastIndex
        val isPreferredSizeReached = currentItems.size >= preferredRowSize
        val shouldRespectPreferredSize = isPreferredSizeReached && ratioSum >= threshold * 0.75f

        if (
            ratioSum >= threshold ||
            currentItems.size >= MAX_JUSTIFIED_ITEMS_PER_ROW ||
            shouldRespectPreferredSize ||
            isLastItem
        ) {
            rows += JustifiedRow(items = currentItems.toList(), startIndex = rowStartIndex)
            currentItems = mutableListOf()
            ratioSum = 0f
            rowStartIndex = index + 1
            preferredRowSize = if (preferredRowSize == MAX_JUSTIFIED_ITEMS_PER_ROW) 2 else MAX_JUSTIFIED_ITEMS_PER_ROW
        }
    }

    if (currentItems.isNotEmpty()) {
        rows += JustifiedRow(items = currentItems.toList(), startIndex = rowStartIndex)
    }

    return rows
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun WallpaperGrid(
    wallpapers: List<WallpaperItem>,
    onWallpaperSelected: (WallpaperItem) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: ((WallpaperItem) -> Unit)? = null,
    selectedIds: Set<String> = emptySet(),
    selectionMode: Boolean = false,
    savedWallpaperKeys: Set<String> = emptySet(),
    savedRemoteIdsByProvider: Map<String, Set<String>> = emptyMap(),
    savedImageUrls: Set<String> = emptySet(),
    showDownloadedBadge: Boolean = false,
    onLoadMore: (() -> Unit)? = null,
    isLoadingMore: Boolean = false,
    canLoadMore: Boolean = false,
    columns: Int = 2,
    layout: WallpaperLayout = WallpaperLayout.GRID,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val totalItems = wallpapers.size
    val loadMoreCallback = onLoadMore
    when (layout) {
        WallpaperLayout.GRID -> {
            val gridState = rememberLazyStaggeredGridState()
            if (loadMoreCallback != null) {
                LaunchedEffect(gridState, totalItems, isLoadingMore, canLoadMore) {
                    if (!canLoadMore) return@LaunchedEffect
                    snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                        .distinctUntilChanged()
                        .collect { lastVisible ->
                            if (!isLoadingMore && totalItems > 0 && lastVisible >= totalItems - 4) {
                                loadMoreCallback()
                            }
                        }
                }
            }

            val columnCount = columns.coerceIn(1, 3)

            LazyVerticalStaggeredGrid(
                modifier = modifier.fillMaxSize(),
                columns = StaggeredGridCells.Fixed(columnCount),
                state = gridState,
                verticalItemSpacing = 8.dp,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 24.dp)
            ) {
                items(
                    wallpapers,
                    key = WallpaperItem::id,
                    contentType = { "wallpaper" }
                ) { wallpaper ->
                    val isSelected = wallpaper.id in selectedIds
                    val isSaved = wallpaper.isSaved(
                        savedWallpaperKeys = savedWallpaperKeys,
                        savedRemoteIdsByProvider = savedRemoteIdsByProvider,
                        savedImageUrls = savedImageUrls
                    )
                    val sharedModifier = Modifier.sharedWallpaperTransitionModifier(
                        wallpaper = wallpaper,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                    WallpaperCard(
                        item = wallpaper,
                        isSelected = isSelected,
                        isSaved = isSaved,
                        selectionMode = selectionMode,
                        showDownloadedBadge = showDownloadedBadge,
                        onClick = { onWallpaperSelected(wallpaper) },
                        onLongPress = onLongPress?.let { handler -> { handler(wallpaper) } },
                        modifier = Modifier.fillMaxWidth(),
                        sharedElementModifier = sharedModifier
                    )
                }

                if (isLoadingMore) {
                    item(span = StaggeredGridItemSpan.FullLine, contentType = "loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }

        WallpaperLayout.JUSTIFIED -> {
            BoxWithConstraints(modifier = modifier.fillMaxSize()) {
                val containerWidth = maxWidth
                val rowSpacing = 8.dp
                val targetRowHeight = 180.dp
                val rows = rememberJustifiedRows(
                    wallpapers = wallpapers,
                    containerWidth = containerWidth,
                    rowSpacing = rowSpacing,
                    targetRowHeight = targetRowHeight
                )
                val listState = rememberLazyListState()

                if (loadMoreCallback != null) {
                    LaunchedEffect(
                        listState,
                        totalItems,
                        isLoadingMore,
                        canLoadMore,
                        rows.size,
                        rows.lastOrNull()?.endIndex
                    ) {
                        if (!canLoadMore) return@LaunchedEffect
                        val rowEndIndices = rows.map { it.endIndex }
                        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                            .distinctUntilChanged()
                            .collect { lastVisibleRow ->
                                val lastIndex = rowEndIndices.getOrNull(lastVisibleRow) ?: return@collect
                                if (!isLoadingMore && totalItems > 0 && lastIndex >= totalItems - 4) {
                                    loadMoreCallback()
                                }
                            }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(rowSpacing),
                    contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 24.dp)
                ) {
                    itemsIndexed(
                        rows,
                        key = { _, row -> row.startIndex },
                        contentType = { _, _ -> "justifiedRow" }
                    ) { _, row ->
                        val rowItems = row.items
                        val ratios = rowItems.map { item ->
                            val ratio = item.aspectRatio
                            when {
                                ratio == null || ratio <= 0f -> DEFAULT_ASPECT_RATIO
                                else -> ratio
                            }
                        }
                        val availableWidth = (containerWidth - rowSpacing * (rowItems.size - 1)).coerceAtLeast(0.dp)
                        val totalRatio = ratios.sum().takeIf { it > 0f } ?: DEFAULT_ASPECT_RATIO
                        val rowHeight = if (availableWidth > 0.dp) {
                            (availableWidth.value / totalRatio).dp
                        } else {
                            targetRowHeight
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowHeight),
                            horizontalArrangement = Arrangement.spacedBy(rowSpacing)
                        ) {
                            rowItems.forEachIndexed { index, wallpaper ->
                                val ratio = ratios.getOrNull(index) ?: DEFAULT_ASPECT_RATIO
                                val itemWidth = (rowHeight.value * ratio).dp
                                val isSelected = wallpaper.id in selectedIds
                                val isSaved = wallpaper.isSaved(
                                    savedWallpaperKeys = savedWallpaperKeys,
                                    savedRemoteIdsByProvider = savedRemoteIdsByProvider,
                                    savedImageUrls = savedImageUrls
                                )

                                val sharedModifier = Modifier.sharedWallpaperTransitionModifier(
                                    wallpaper = wallpaper,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                                WallpaperCard(
                                    item = wallpaper,
                                    isSelected = isSelected,
                                    isSaved = isSaved,
                                    selectionMode = selectionMode,
                                    showDownloadedBadge = showDownloadedBadge,
                                    onClick = { onWallpaperSelected(wallpaper) },
                                    onLongPress = onLongPress?.let { handler -> { handler(wallpaper) } },
                                    modifier = Modifier
                                        .height(rowHeight)
                                        .width(itemWidth),
                                    aspectRatio = null,
                                    sharedElementModifier = sharedModifier
                                )
                            }
                        }
                    }

                    if (isLoadingMore) {
                        item(contentType = "loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }

        WallpaperLayout.LIST -> {
            val listState = rememberLazyListState()
            if (loadMoreCallback != null) {
                LaunchedEffect(listState, totalItems, isLoadingMore, canLoadMore) {
                    if (!canLoadMore) return@LaunchedEffect
                    snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                        .distinctUntilChanged()
                        .collect { lastVisible ->
                            if (!isLoadingMore && totalItems > 0 && lastVisible >= totalItems - 4) {
                                loadMoreCallback()
                            }
                        }
                }
            }

            LazyColumn(
                modifier = modifier.fillMaxSize(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 24.dp)
            ) {
                lazyItems(
                    wallpapers,
                    key = WallpaperItem::id,
                    contentType = { "wallpaper" }
                ) { wallpaper ->
                    val isSelected = wallpaper.id in selectedIds
                    val isSaved = wallpaper.isSaved(
                        savedWallpaperKeys = savedWallpaperKeys,
                        savedRemoteIdsByProvider = savedRemoteIdsByProvider,
                        savedImageUrls = savedImageUrls
                    )
                    val sharedModifier = Modifier.sharedWallpaperTransitionModifier(
                        wallpaper = wallpaper,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                    WallpaperListRow(
                        item = wallpaper,
                        isSelected = isSelected,
                        isSaved = isSaved,
                        selectionMode = selectionMode,
                        showDownloadedBadge = showDownloadedBadge,
                        onClick = { onWallpaperSelected(wallpaper) },
                        onLongPress = onLongPress?.let { handler -> { handler(wallpaper) } },
                        sharedElementModifier = sharedModifier
                    )
                }

                if (isLoadingMore) {
                    item(contentType = "loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

private fun WallpaperItem.isSaved(
    savedWallpaperKeys: Set<String>,
    savedRemoteIdsByProvider: Map<String, Set<String>>,
    savedImageUrls: Set<String>
): Boolean {
    val libraryKey = libraryKey()
    if (libraryKey != null && libraryKey in savedWallpaperKeys) {
        return true
    }
    val providerKey = providerKey() ?: UNKNOWN_PROVIDER_KEY
    val remoteId = remoteIdentifierWithinSource()
    if (remoteId != null) {
        val providerSet = savedRemoteIdsByProvider[providerKey]
        val fallbackSet = if (providerKey != UNKNOWN_PROVIDER_KEY) {
            savedRemoteIdsByProvider[UNKNOWN_PROVIDER_KEY]
        } else {
            null
        }
        if (providerSet?.contains(remoteId) == true || fallbackSet?.contains(remoteId) == true) {
            return true
        }
    }
    return savedImageUrls.contains(imageUrl)
}

private fun resolutionText(width: Int?, height: Int?): String? {
    if (width == null || height == null) return null
    if (width <= 0 || height <= 0) return null
    return "$width × $height"
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun WallpaperCard(
    item: WallpaperItem,
    isSelected: Boolean,
    isSaved: Boolean,
    selectionMode: Boolean,
    showDownloadedBadge: Boolean,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    aspectRatio: Float? = item.aspectRatio?.takeIf { it > 0f } ?: DEFAULT_ASPECT_RATIO,
    sharedElementModifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .then(if (aspectRatio != null) Modifier.aspectRatio(aspectRatio) else Modifier)
            .graphicsLayer {
                if (isSelected) {
                    scaleX = 0.97f
                    scaleY = 0.97f
                }
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onLongPress?.invoke() }
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 4.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box {
            WallpaperPreviewImage(
                model = item.previewModel(),
                contentDescription = item.title,
                modifier = sharedElementModifier.then(Modifier.fillMaxSize()),
                contentScale = ContentScale.Crop,
                clipShape = RoundedCornerShape(18.dp)
            )

            if (selectionMode && !isSelected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.1f))
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                )
                Icon(
                    imageVector = Icons.Filled.CheckBox,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                )
            }

            val isDownloaded = showDownloadedBadge && item.isDownloaded && !item.localUri.isNullOrBlank()
            if (isSaved || isDownloaded) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isDownloaded) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Downloaded",
                            tint = Color(0xFF2E7D32)
                        )
                    }
                    if (isSaved) {
                        Icon(
                            imageVector = Icons.Outlined.TaskAlt,
                            contentDescription = "In your library",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xCC000000))
                        )
                    )
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    item.sourceName?.let { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun WallpaperListRow(
    item: WallpaperItem,
    isSelected: Boolean,
    isSaved: Boolean,
    selectionMode: Boolean,
    showDownloadedBadge: Boolean,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    sharedElementModifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 4.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = { onLongPress?.invoke() }
                    )
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = sharedElementModifier.then(
                        Modifier
                            .height(96.dp)
                            .aspectRatio(item.aspectRatio?.takeIf { it > 0f } ?: DEFAULT_ASPECT_RATIO)
                            .clip(RoundedCornerShape(14.dp))
                    )
                ) {
                    WallpaperPreviewImage(
                        model = item.previewModel(),
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        clipShape = RoundedCornerShape(14.dp)
                    )
                    if (selectionMode && !isSelected) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.1f))
                        )
                    }
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                        )
                        Icon(
                            imageVector = Icons.Filled.CheckBox,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                        )
                    }
                    val isDownloaded = showDownloadedBadge && item.isDownloaded && !item.localUri.isNullOrBlank()
                    if (isSaved || isDownloaded) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isDownloaded) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Downloaded",
                                    tint = Color(0xFF2E7D32)
                                )
                            }
                            if (isSaved) {
                                Icon(
                                    imageVector = Icons.Outlined.TaskAlt,
                                    contentDescription = "In your library",
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    item.sourceName?.let { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    resolutionText(item.width, item.height)?.let { dimensions ->
                        Text(
                            text = dimensions,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private const val DEFAULT_ASPECT_RATIO = 9f / 16f