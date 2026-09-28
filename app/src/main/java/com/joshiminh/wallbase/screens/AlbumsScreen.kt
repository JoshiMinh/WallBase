package com.joshiminh.wallbase.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.joshiminh.wallbase.data.entity.AlbumItem
import com.joshiminh.wallbase.data.repository.AlbumLayout
import com.joshiminh.wallbase.navigation.TopBarHandle
import com.joshiminh.wallbase.navigation.TopBarState
import com.joshiminh.wallbase.ui.AlbumGridCard
import com.joshiminh.wallbase.ui.AlbumRowCard
import com.joshiminh.wallbase.ui.components.AlbumLayoutPicker
import com.joshiminh.wallbase.ui.components.SheetTab
import com.joshiminh.wallbase.ui.components.TopBarSearchField
import com.joshiminh.wallbase.ui.components.ViewFilterSortBottomSheet
import com.joshiminh.wallbase.ui.components.bottomBarInsetPadding
import com.joshiminh.wallbase.ui.components.topBarInsetPadding
import com.joshiminh.wallbase.ui.theme.WallBaseShapes
import com.joshiminh.wallbase.ui.theme.WallBaseSpacing
import com.joshiminh.wallbase.ui.viewmodel.LibraryViewModel
import com.joshiminh.wallbase.util.SortField
import com.joshiminh.wallbase.util.toAlbumSortOption
import com.joshiminh.wallbase.util.toSelection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    onAlbumSelected: (AlbumItem) -> Unit,
    onConfigureTopBar: (TopBarState) -> TopBarHandle,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAlbumDialog by rememberSaveable { mutableStateOf(false) }
    var selectedAlbumIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showRemoveDownloadsDialog by rememberSaveable { mutableStateOf(false) }
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }
    val hapticFeedback = LocalHapticFeedback.current

    val isAlbumSelection = selectedAlbumIds.isNotEmpty()
    val albumsById = remember(uiState.albums) { uiState.albums.associateBy { it.id } }
    val selectedAlbums = remember(selectedAlbumIds, albumsById) {
        if (selectedAlbumIds.isEmpty()) emptyList()
        else selectedAlbumIds.mapNotNull(albumsById::get)
    }

    val trimmedQuery = remember(searchQuery) { searchQuery.trim() }
    val albumLayout = uiState.albumLayout
    val displayedAlbums = remember(uiState.albums, trimmedQuery, isSearchActive) {
        if (!isSearchActive || trimmedQuery.isEmpty()) {
            uiState.albums
        } else {
            uiState.albums.filter { album ->
                album.title.contains(trimmedQuery, ignoreCase = true)
            }
        }
    }

    var localAlbums by remember(displayedAlbums) { mutableStateOf(displayedAlbums) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var showDeleteConfirmDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(displayedAlbums) {
        if (draggingId == null) {
            localAlbums = displayedAlbums
        }
    }

    val onAlbumLayoutSelected: (AlbumLayout) -> Unit = { layout ->
        if (albumLayout != layout) {
            libraryViewModel.updateAlbumLayout(layout)
        }
    }

    LaunchedEffect(uiState.albums) {
        if (selectedAlbumIds.isEmpty()) return@LaunchedEffect
        val available = uiState.albums.mapTo(hashSetOf()) { it.id }
        val filtered = selectedAlbumIds.filterTo(mutableSetOf()) { it in available }
        if (filtered.size != selectedAlbumIds.size) {
            selectedAlbumIds = filtered
        }
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        } else {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            libraryViewModel.consumeMessage()
        }
    }

    BackHandler(enabled = isAlbumSelection) {
        selectedAlbumIds = emptySet()
    }

    val topBarHandleState = remember { mutableStateOf<TopBarHandle?>(null) }
    val topBarState = when {
        isAlbumSelection -> {
            val selectionTitle = "${selectedAlbumIds.size} selected"
            val removeLabel = "Delete albums"
            val selectAllLabel = "Select all"
            val clearLabel = "Clear selection"
            val actions: @Composable RowScope.() -> Unit = {
                IconButton(onClick = {
                    val allIds = displayedAlbums.map { it.id }.toSet()
                    selectedAlbumIds = if (selectedAlbumIds.size == allIds.size) emptySet() else allIds
                }) {
                    Icon(imageVector = Icons.Outlined.SelectAll, contentDescription = selectAllLabel)
                }
                IconButton(onClick = {
                    showDeleteConfirmDialog = true
                }) {
                    Icon(imageVector = Icons.Outlined.Delete, contentDescription = removeLabel)
                }
            }
            val navigationIcon = TopBarState.NavigationIcon(
                icon = Icons.Outlined.Close,
                contentDescription = clearLabel,
                onClick = { selectedAlbumIds = emptySet() }
            )
            TopBarState(
                title = selectionTitle,
                navigationIcon = navigationIcon,
                actions = actions,
                bottomContent = null,
                autoHideBars = false
            )
        }

        else -> {
            val baseTitle = "Albums"
            val searchPlaceholder = "Search albums"
            val actions: @Composable RowScope.() -> Unit = {
                if (isSearchActive) {
                    IconButton(onClick = {
                        isSearchActive = false
                        searchQuery = ""
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }) {
                        Icon(imageVector = Icons.Outlined.Close, contentDescription = "Close search")
                    }
                } else {
                    IconButton(onClick = { isSearchActive = true }) {
                        Icon(imageVector = Icons.Outlined.Search, contentDescription = "Search albums")
                    }
                    IconButton(onClick = { showSortSheet = true }) {
                        Icon(imageVector = Icons.AutoMirrored.Outlined.Sort, contentDescription = "Sort and view options")
                    }
                }
            }
            val titleContent: (@Composable () -> Unit)? = if (isSearchActive) {
                {
                    TopBarSearchField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        onClear = { searchQuery = "" },
                        placeholder = searchPlaceholder,
                        focusRequester = searchFocusRequester,
                        showClearButton = false
                    )
                }
            } else {
                null
            }
            TopBarState(
                title = if (isSearchActive) null else baseTitle,
                navigationIcon = null,
                actions = actions,
                titleContent = titleContent,
                bottomContent = null,
                autoHideBars = false
            )
        }
    }

    SideEffect {
        val handle = topBarHandleState.value
        if (handle == null) {
            topBarHandleState.value = onConfigureTopBar(topBarState)
        } else {
            handle.update(topBarState)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            topBarHandleState.value?.clear()
            topBarHandleState.value = null
        }
    }

    val onAlbumClick: (AlbumItem) -> Unit = { album ->
        if (isAlbumSelection) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
            selectedAlbumIds = if (album.id in selectedAlbumIds) selectedAlbumIds - album.id else selectedAlbumIds + album.id
        } else {
            onAlbumSelected(album)
        }
    }

    val onAlbumLongPress: (AlbumItem) -> Unit = { album ->
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        selectedAlbumIds = if (album.id in selectedAlbumIds) selectedAlbumIds - album.id else selectedAlbumIds + album.id
    }

    val albumSelection = uiState.albumSortOption.toSelection()
    val availableSortFields = remember { listOf(SortField.Custom, SortField.Alphabet, SortField.DateAdded) }

    val pullRefreshState = rememberPullToRefreshState()
    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()

    // Auto-scroll LazyColumn when dragging near viewport boundaries
    LaunchedEffect(draggingId, albumLayout) {
        if (draggingId == null || albumLayout != AlbumLayout.CARD_LIST) return@LaunchedEffect
        while (isActive && draggingId != null) {
            val itemInfo = lazyListState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == draggingId }
            if (itemInfo != null) {
                val currentCenter = itemInfo.offset + itemInfo.size / 2 + dragOffsetY
                val viewportHeight = lazyListState.layoutInfo.viewportSize.height
                val topThreshold = 100f
                val bottomThreshold = viewportHeight - 100f
                if (currentCenter < topThreshold) {
                    val scroll = -((topThreshold - currentCenter) / topThreshold * 14f).coerceAtLeast(3f)
                    lazyListState.scrollBy(scroll)
                } else if (currentCenter > bottomThreshold) {
                    val scroll = ((currentCenter - bottomThreshold) / 100f * 14f).coerceAtLeast(3f)
                    lazyListState.scrollBy(scroll)
                }
            }
            delay(16)
        }
    }

    // Auto-scroll LazyVerticalGrid when dragging near viewport boundaries
    LaunchedEffect(draggingId, albumLayout) {
        if (draggingId == null || albumLayout != AlbumLayout.GRID) return@LaunchedEffect
        while (isActive && draggingId != null) {
            val itemInfo = lazyGridState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == draggingId }
            if (itemInfo != null) {
                val currentCenterY = itemInfo.offset.y + itemInfo.size.height / 2 + dragOffsetY
                val viewportHeight = lazyGridState.layoutInfo.viewportSize.height
                val topThreshold = 100f
                val bottomThreshold = viewportHeight - 100f
                if (currentCenterY < topThreshold) {
                    val scroll = -((topThreshold - currentCenterY) / topThreshold * 14f).coerceAtLeast(3f)
                    lazyGridState.scrollBy(scroll)
                } else if (currentCenterY > bottomThreshold) {
                    val scroll = ((currentCenterY - bottomThreshold) / 100f * 14f).coerceAtLeast(3f)
                    lazyGridState.scrollBy(scroll)
                }
            }
            delay(16)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { libraryViewModel.refreshLibrary() },
            state = pullRefreshState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullRefreshState,
                    isRefreshing = uiState.isRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topBarInsetPadding(8.dp))
                )
            },
            modifier = Modifier.fillMaxSize()
        ) {
            if (displayedAlbums.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Album,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = if (isSearchActive) "No albums match \"$trimmedQuery\"" else "No albums yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!isSearchActive) {
                            Text(
                                text = "Create custom albums to organize your favorite wallpapers.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = { showAlbumDialog = true }) {
                                Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
                                Text(text = "Create Album", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            } else {
                when (albumLayout) {
                    AlbumLayout.GRID -> {
                        LazyVerticalGrid(
                            state = lazyGridState,
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(
                                start = 8.dp,
                                end = 8.dp,
                                top = topBarInsetPadding(8.dp),
                                bottom = bottomBarInsetPadding(8.dp, hasBottomNav = true)
                            ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            gridItemsIndexed(localAlbums, key = { _, album -> album.id }) { index, album ->
                                val isDragging = album.id == draggingId
                                val isReorderEnabled = !isSearchActive && !isAlbumSelection && localAlbums.size > 1
                                Box(
                                    modifier = Modifier
                                        .zIndex(if (isDragging) 10f else 1f)
                                        .graphicsLayer {
                                            if (isDragging) {
                                                translationX = dragOffsetX
                                                translationY = dragOffsetY
                                                scaleX = 1.04f
                                                scaleY = 1.04f
                                                shadowElevation = 16f
                                            }
                                        }
                                        .animateItem()
                                ) {
                                    AlbumGridCard(
                                        album = album,
                                        selected = album.id in selectedAlbumIds,
                                        selectionMode = isAlbumSelection,
                                        isDragging = isDragging,
                                        isReorderEnabled = isReorderEnabled,
                                        onClick = { onAlbumClick(album) },
                                        onLongPress = { onAlbumLongPress(album) },
                                        dragModifier = if (isReorderEnabled) {
                                            Modifier.pointerInput(album.id) {
                                                detectDragGestures(
                                                    onDragStart = {
                                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        draggingId = album.id
                                                        dragOffsetX = 0f
                                                        dragOffsetY = 0f
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragOffsetX += dragAmount.x
                                                        dragOffsetY += dragAmount.y
                                                        val currentIndex = localAlbums.indexOfFirst { it.id == draggingId }
                                                        if (currentIndex != -1) {
                                                            val currentItemInfo = lazyGridState.layoutInfo.visibleItemsInfo
                                                                .firstOrNull { it.key == draggingId }
                                                            val itemWidth = currentItemInfo?.size?.width?.toFloat() ?: 300f
                                                            val itemHeight = currentItemInfo?.size?.height?.toFloat() ?: 300f
                                                            val xThreshold = itemWidth * 0.55f
                                                            val yThreshold = itemHeight * 0.55f

                                                            // Horizontal step
                                                            val col = currentIndex % 2
                                                            if (col == 0 && dragOffsetX > xThreshold && currentIndex + 1 < localAlbums.size) {
                                                                val updated = localAlbums.toMutableList()
                                                                val moved = updated.removeAt(currentIndex)
                                                                updated.add(currentIndex + 1, moved)
                                                                localAlbums = updated
                                                                dragOffsetX -= itemWidth
                                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                                            } else if (col == 1 && dragOffsetX < -xThreshold && currentIndex - 1 >= 0) {
                                                                val updated = localAlbums.toMutableList()
                                                                val moved = updated.removeAt(currentIndex)
                                                                updated.add(currentIndex - 1, moved)
                                                                localAlbums = updated
                                                                dragOffsetX += itemWidth
                                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                                            }

                                                            // Vertical step
                                                            val recheckedIndex = localAlbums.indexOfFirst { it.id == draggingId }
                                                            if (recheckedIndex != -1) {
                                                                if (dragOffsetY > yThreshold) {
                                                                    val targetIndex = if (recheckedIndex + 2 < localAlbums.size) {
                                                                        recheckedIndex + 2
                                                                    } else if (recheckedIndex + 1 < localAlbums.size) {
                                                                        recheckedIndex + 1
                                                                    } else null

                                                                    if (targetIndex != null) {
                                                                        val updated = localAlbums.toMutableList()
                                                                        val moved = updated.removeAt(recheckedIndex)
                                                                        updated.add(targetIndex, moved)
                                                                        localAlbums = updated
                                                                        dragOffsetY -= itemHeight
                                                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                                                    }
                                                                } else if (dragOffsetY < -yThreshold) {
                                                                    val targetIndex = if (recheckedIndex - 2 >= 0) {
                                                                        recheckedIndex - 2
                                                                    } else if (recheckedIndex - 1 >= 0 && (recheckedIndex - 1) / 2 < recheckedIndex / 2) {
                                                                        recheckedIndex - 1
                                                                    } else null

                                                                    if (targetIndex != null) {
                                                                        val updated = localAlbums.toMutableList()
                                                                        val moved = updated.removeAt(recheckedIndex)
                                                                        updated.add(targetIndex, moved)
                                                                        localAlbums = updated
                                                                        dragOffsetY += itemHeight
                                                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    },
                                                    onDragEnd = {
                                                        val wasDragging = draggingId != null
                                                        draggingId = null
                                                        dragOffsetX = 0f
                                                        dragOffsetY = 0f
                                                        if (wasDragging) {
                                                            libraryViewModel.reorderAlbums(localAlbums)
                                                        }
                                                    },
                                                    onDragCancel = {
                                                        draggingId = null
                                                        dragOffsetX = 0f
                                                        dragOffsetY = 0f
                                                        localAlbums = displayedAlbums
                                                    }
                                                )
                                            }
                                        } else Modifier
                                    )
                                }
                            }
                        }
                    }

                    AlbumLayout.CARD_LIST -> {
                        LazyColumn(
                            state = lazyListState,
                            contentPadding = PaddingValues(
                                start = 8.dp,
                                end = 8.dp,
                                top = topBarInsetPadding(8.dp),
                                bottom = bottomBarInsetPadding(8.dp, hasBottomNav = true)
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(localAlbums, key = { _, album -> album.id }) { index, album ->
                                val isDragging = album.id == draggingId
                                val isReorderEnabled = !isSearchActive && !isAlbumSelection && localAlbums.size > 1
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .zIndex(if (isDragging) 10f else 1f)
                                        .graphicsLayer {
                                            if (isDragging) {
                                                translationY = dragOffsetY
                                                scaleX = 1.02f
                                                scaleY = 1.02f
                                                shadowElevation = 16f
                                            }
                                        }
                                        .animateItem()
                                ) {
                                    AlbumRowCard(
                                        album = album,
                                        selected = album.id in selectedAlbumIds,
                                        selectionMode = isAlbumSelection,
                                        isDragging = isDragging,
                                        isReorderEnabled = isReorderEnabled,
                                        onClick = { onAlbumClick(album) },
                                        onLongPress = { onAlbumLongPress(album) },
                                        dragModifier = if (isReorderEnabled) {
                                            Modifier.pointerInput(album.id) {
                                                detectDragGestures(
                                                    onDragStart = {
                                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        draggingId = album.id
                                                        dragOffsetY = 0f
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragOffsetY += dragAmount.y
                                                        val currentIndex = localAlbums.indexOfFirst { it.id == draggingId }
                                                        if (currentIndex != -1) {
                                                            val currentItemInfo = lazyListState.layoutInfo.visibleItemsInfo
                                                                .firstOrNull { it.key == draggingId }
                                                            val itemHeight = currentItemInfo?.size?.toFloat() ?: 180f
                                                            val threshold = itemHeight * 0.55f

                                                            if (dragOffsetY > threshold && currentIndex + 1 < localAlbums.size) {
                                                                val updated = localAlbums.toMutableList()
                                                                val moved = updated.removeAt(currentIndex)
                                                                updated.add(currentIndex + 1, moved)
                                                                localAlbums = updated
                                                                dragOffsetY -= itemHeight
                                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                                            } else if (dragOffsetY < -threshold && currentIndex > 0) {
                                                                val updated = localAlbums.toMutableList()
                                                                val moved = updated.removeAt(currentIndex)
                                                                updated.add(currentIndex - 1, moved)
                                                                localAlbums = updated
                                                                dragOffsetY += itemHeight
                                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                                            }
                                                        }
                                                    },
                                                    onDragEnd = {
                                                        val wasDragging = draggingId != null
                                                        draggingId = null
                                                        dragOffsetY = 0f
                                                        if (wasDragging) {
                                                            libraryViewModel.reorderAlbums(localAlbums)
                                                        }
                                                    },
                                                    onDragCancel = {
                                                        draggingId = null
                                                        dragOffsetY = 0f
                                                        localAlbums = displayedAlbums
                                                    }
                                                )
                                            }
                                        } else Modifier
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // FAB to add album
        if (!isAlbumSelection) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 96.dp)
            ) {
                val creating = uiState.isCreatingAlbum
                FloatingActionButton(
                    onClick = { if (!creating) showAlbumDialog = true },
                    modifier = Modifier.then(
                        if (creating) {
                            Modifier
                                .alpha(0.6f)
                                .semantics { disabled() }
                        } else {
                            Modifier
                        }
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Add album"
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 80.dp)
        )
    }

    if (showAlbumDialog) {
        var title by rememberSaveable { mutableStateOf("") }
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        AlertDialog(
            onDismissRequest = { showAlbumDialog = false },
            title = { Text(text = "New album") },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Album title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    shape = WallBaseShapes.control
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = title.trim()
                        if (trimmed.isNotBlank()) {
                            libraryViewModel.createAlbum(trimmed)
                            showAlbumDialog = false
                        }
                    },
                    enabled = title.trim().isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAlbumDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirmDialog) {
        val count = selectedAlbumIds.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(text = "Delete ${if (count == 1) "album" else "$count albums"}?") },
            text = {
                Text(
                    text = "Are you sure you want to delete ${if (count == 1) "this album" else "these albums"}? Wallpapers inside will not be deleted from your library."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val idsToDelete = selectedAlbumIds.toList()
                        selectedAlbumIds = emptySet()
                        showDeleteConfirmDialog = false
                        libraryViewModel.deleteAlbums(idsToDelete)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    ViewFilterSortBottomSheet(
        visible = showSortSheet,
        onDismissRequest = { showSortSheet = false },
        availableTabs = listOf(SheetTab.SORT, SheetTab.DISPLAY),
        initialTab = SheetTab.SORT,
        sortSelection = albumSelection,
        availableSortFields = availableSortFields,
        onSortSelectionChanged = { selection ->
            libraryViewModel.updateAlbumSort(selection.toAlbumSortOption())
        },
        customDisplayContent = {
            AlbumLayoutPicker(
                label = "Layout",
                selectedLayout = albumLayout,
                onLayoutSelected = onAlbumLayoutSelected
            )
        }
    )
}
