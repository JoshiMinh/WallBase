package com.joshiminh.wallbase.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
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
    bottomBarOffsetY: Float = 0f
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
                    libraryViewModel.deleteAlbums(selectedAlbumIds)
                    selectedAlbumIds = emptySet()
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
            selectedAlbumIds = if (album.id in selectedAlbumIds) selectedAlbumIds - album.id else selectedAlbumIds + album.id
        } else {
            onAlbumSelected(album)
        }
    }

    val onAlbumLongPress: (AlbumItem) -> Unit = { album ->
        selectedAlbumIds = if (album.id in selectedAlbumIds) selectedAlbumIds - album.id else selectedAlbumIds + album.id
    }

    val albumSelection = uiState.albumSortOption.toSelection()
    val availableSortFields = remember { listOf(SortField.Alphabet, SortField.DateAdded) }

    val pullRefreshState = rememberPullToRefreshState()

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
                            gridItems(displayedAlbums, key = { it.id }) { album ->
                                AlbumGridCard(
                                    album = album,
                                    selected = album.id in selectedAlbumIds,
                                    selectionMode = isAlbumSelection,
                                    onClick = { onAlbumClick(album) },
                                    onLongPress = { onAlbumLongPress(album) }
                                )
                            }
                        }
                    }

                    AlbumLayout.CARD_LIST -> {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 8.dp,
                                end = 8.dp,
                                top = topBarInsetPadding(8.dp),
                                bottom = bottomBarInsetPadding(8.dp, hasBottomNav = true)
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(displayedAlbums, key = { it.id }) { album ->
                                AlbumRowCard(
                                    album = album,
                                    selected = album.id in selectedAlbumIds,
                                    selectionMode = isAlbumSelection,
                                    onClick = { onAlbumClick(album) },
                                    onLongPress = { onAlbumLongPress(album) }
                                )
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
                    .graphicsLayer {
                        translationY = bottomBarOffsetY * 1.5f
                    }
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
