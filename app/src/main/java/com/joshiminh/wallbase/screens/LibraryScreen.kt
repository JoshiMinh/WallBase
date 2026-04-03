package com.joshiminh.wallbase.screens

import com.joshiminh.wallbase.navigation.*
import com.joshiminh.wallbase.ui.LibraryContent
import com.joshiminh.wallbase.ui.DownloadProgressToast
import com.joshiminh.wallbase.ui.DirectAddDialog
import com.joshiminh.wallbase.ui.AlbumPickerDialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import coil3.compose.AsyncImage
import com.joshiminh.wallbase.navigation.TopBarHandle
import com.joshiminh.wallbase.navigation.TopBarState
import com.joshiminh.wallbase.data.entity.AlbumItem
import com.joshiminh.wallbase.data.entity.WallpaperItem
import com.joshiminh.wallbase.data.repository.AlbumLayout
import com.joshiminh.wallbase.data.repository.WallpaperLayout
import com.joshiminh.wallbase.ui.components.AlbumLayoutPicker
import com.joshiminh.wallbase.ui.components.GridColumnPicker
import com.joshiminh.wallbase.ui.components.SortBottomSheet
import com.joshiminh.wallbase.ui.components.TopBarSearchField
import com.joshiminh.wallbase.ui.components.WallpaperGrid
import com.joshiminh.wallbase.ui.components.WallpaperLayoutPicker
import com.joshiminh.wallbase.util.SortField
import com.joshiminh.wallbase.util.SortSelection
import com.joshiminh.wallbase.util.toAlbumSortOption
import com.joshiminh.wallbase.util.toSelection
import com.joshiminh.wallbase.util.toWallpaperSortOption
import com.joshiminh.wallbase.ui.viewmodel.LibraryViewModel
import androidx.compose.foundation.lazy.grid.items as gridItems

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LibraryScreen(
    onWallpaperSelected: (WallpaperItem, Boolean) -> Unit,
    onAlbumSelected: (AlbumItem) -> Unit,
    onConfigureTopBar: (TopBarState) -> TopBarHandle,
    libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val uiState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showAlbumDialog by rememberSaveable { mutableStateOf(false) }
    var showSelectionAlbumDialog by rememberSaveable { mutableStateOf(false) }
    var selectedWallpaperIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedAlbumIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showRemoveDownloadsDialog by rememberSaveable { mutableStateOf(false) }
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showDirectAddDialog by rememberSaveable { mutableStateOf(false) }
    var directAddUrl by rememberSaveable { mutableStateOf("") }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }

    val isWallpaperSelection = selectedWallpaperIds.isNotEmpty()
    val isAlbumSelection = selectedAlbumIds.isNotEmpty()
    val selectionMode = isWallpaperSelection || isAlbumSelection
    val wallpapersById = remember(uiState.wallpapers) {
        uiState.wallpapers.associateBy { it.id }
    }
    val albumsById = remember(uiState.albums) {
        uiState.albums.associateBy { it.id }
    }
    val selectedWallpapers = remember(selectedWallpaperIds, wallpapersById) {
        if (selectedWallpaperIds.isEmpty()) emptyList()
        else selectedWallpaperIds.mapNotNull(wallpapersById::get)
    }
    val selectedAlbums = remember(selectedAlbumIds, albumsById) {
        if (selectedAlbumIds.isEmpty()) emptyList()
        else selectedAlbumIds.mapNotNull(albumsById::get)
    }

    val trimmedQuery = remember(searchQuery) { searchQuery.trim() }
    val wallpaperGridColumns = uiState.wallpaperGridColumns
    val wallpaperLayout = uiState.wallpaperLayout
    val albumLayout = uiState.albumLayout
    val displayedWallpapers = remember(uiState.wallpapers, trimmedQuery, isSearchActive) {
        if (!isSearchActive || trimmedQuery.isEmpty()) {
            uiState.wallpapers
        } else {
            uiState.wallpapers.filter { wallpaper ->
                wallpaper.title.contains(trimmedQuery, ignoreCase = true) ||
                    (wallpaper.sourceName?.contains(trimmedQuery, ignoreCase = true) == true) ||
                    (wallpaper.sourceKey?.contains(trimmedQuery, ignoreCase = true) == true)
            }
        }
    }
    val displayedAlbums = remember(uiState.albums, trimmedQuery, isSearchActive) {
        if (!isSearchActive || trimmedQuery.isEmpty()) {
            uiState.albums
        } else {
            uiState.albums.filter { album ->
                album.title.contains(trimmedQuery, ignoreCase = true)
            }
        }
    }

    val onGridColumnsSelected: (Int) -> Unit = { columns ->
        if (wallpaperGridColumns != columns) {
            libraryViewModel.updateWallpaperGridColumns(columns)
        }
    }

    val onWallpaperLayoutSelected: (WallpaperLayout) -> Unit = { layout ->
        if (wallpaperLayout != layout) {
            libraryViewModel.updateWallpaperLayout(layout)
        }
    }

    val onAlbumLayoutSelected: (AlbumLayout) -> Unit = { layout ->
        if (albumLayout != layout) {
            libraryViewModel.updateAlbumLayout(layout)
        }
    }

    LaunchedEffect(uiState.wallpapers) {
        if (selectedWallpaperIds.isEmpty()) return@LaunchedEffect
        val available = uiState.wallpapers.mapTo(hashSetOf()) { it.id }
        val filtered = selectedWallpaperIds.filterTo(mutableSetOf()) { it in available }
        if (filtered.size != selectedWallpaperIds.size) {
            selectedWallpaperIds = filtered
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

    LaunchedEffect(uiState.directAddCompleted) {
        when (uiState.directAddCompleted) {
            true -> {
                showDirectAddDialog = false
                directAddUrl = ""
                libraryViewModel.consumeDirectAddStatus()
            }

            false -> {
                libraryViewModel.consumeDirectAddStatus()
            }

            null -> Unit
        }
    }

    LaunchedEffect(showDirectAddDialog) {
        if (!showDirectAddDialog) {
            directAddUrl = ""
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab != 0 && selectedWallpaperIds.isNotEmpty()) {
            selectedWallpaperIds = emptySet()
        }
        if (selectedTab != 1 && selectedAlbumIds.isNotEmpty()) {
            selectedAlbumIds = emptySet()
        }
        if (selectedTab != 0 && showDirectAddDialog) {
            showDirectAddDialog = false
        }
    }

    LaunchedEffect(selectionMode) {
        if (selectionMode) {
            showSortSheet = false
            if (isSearchActive) {
                isSearchActive = false
                searchQuery = ""
            }
            showDirectAddDialog = false
        } else {
            showRemoveDownloadsDialog = false
        }
    }

    LaunchedEffect(selectedTab) {
        showSortSheet = false
    }

    val topBarHandleState = remember { mutableStateOf<TopBarHandle?>(null) }
    val topBarState = when {
        isWallpaperSelection -> {
            val selectionTitle = "${selectedWallpaperIds.size} selected"
            val removeLabel = "Remove from library"
            val selectAllLabel = "Select all"
            val addLabel = "Add to album"
            val allSelectedDownloaded = selectedWallpapers.isNotEmpty() &&
                selectedWallpapers.all { it.isDownloaded && !it.localUri.isNullOrBlank() }
            val downloadLabel = if (allSelectedDownloaded) {
                "Remove downloads"
            } else {
                "Download"
            }
            val clearLabel = "Clear selection"
            val actions: @Composable RowScope.() -> Unit = {
                val selectAllEnabled = !uiState.isSelectionActionInProgress &&
                    displayedWallpapers.isNotEmpty() &&
                    selectedWallpaperIds.size < displayedWallpapers.size
                IconButton(
                    onClick = {
                        selectedWallpaperIds = displayedWallpapers.mapTo(mutableSetOf()) { it.id }
                    },
                    enabled = selectAllEnabled
                ) {
                    Icon(imageVector = Icons.Outlined.SelectAll, contentDescription = selectAllLabel)
                }
                IconButton(
                    onClick = {
                        if (selectedWallpapers.isNotEmpty()) {
                            libraryViewModel.removeWallpapers(selectedWallpapers)
                        }
                        selectedWallpaperIds = emptySet()
                    },
                    enabled = !uiState.isSelectionActionInProgress
                ) {
                    Icon(imageVector = Icons.Outlined.Delete, contentDescription = removeLabel)
                }
                if (allSelectedDownloaded) {
                    IconButton(
                        onClick = {
                            if (!uiState.isSelectionActionInProgress) {
                                showRemoveDownloadsDialog = true
                            }
                        },
                        enabled = !uiState.isSelectionActionInProgress
                    ) {
                        Icon(imageVector = Icons.Outlined.TaskAlt, contentDescription = downloadLabel)
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (selectedWallpapers.isNotEmpty()) {
                                libraryViewModel.downloadWallpapers(selectedWallpapers)
                            }
                        },
                        enabled = !uiState.isSelectionActionInProgress
                    ) {
                        Icon(imageVector = Icons.Outlined.Download, contentDescription = downloadLabel)
                    }
                }
                IconButton(
                    onClick = {
                        if (!uiState.isSelectionActionInProgress) {
                            showSelectionAlbumDialog = true
                        }
                    },
                    enabled = !uiState.isSelectionActionInProgress
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Outlined.PlaylistAdd, contentDescription = addLabel)
                }
            }
            TopBarState(
                title = selectionTitle,
                navigationIcon = TopBarState.NavigationIcon(
                    icon = Icons.Outlined.Close,
                    contentDescription = clearLabel,
                    onClick = { selectedWallpaperIds = emptySet() }
                ),
                actions = actions
            )
        }

        isAlbumSelection -> {
            val selectionTitle = "${selectedAlbumIds.size} selected"
            val selectAllLabel = "Select all"
            val deleteLabel = if (selectedAlbumIds.size > 1) "Delete albums" else "Delete album"
            val clearLabel = "Clear selection"
            val actions: @Composable RowScope.() -> Unit = {
                val selectAllEnabled = !uiState.isSelectionActionInProgress &&
                    displayedAlbums.isNotEmpty() &&
                    selectedAlbumIds.size < displayedAlbums.size
                IconButton(
                    onClick = {
                        selectedAlbumIds = displayedAlbums.mapTo(mutableSetOf()) { it.id }
                    },
                    enabled = selectAllEnabled
                ) {
                    Icon(imageVector = Icons.Outlined.SelectAll, contentDescription = selectAllLabel)
                }
                IconButton(
                    onClick = {
                        if (selectedAlbumIds.isNotEmpty() && !uiState.isSelectionActionInProgress) {
                            libraryViewModel.deleteAlbums(selectedAlbumIds)
                        }
                        selectedAlbumIds = emptySet()
                    },
                    enabled = !uiState.isSelectionActionInProgress
                ) {
                    Icon(imageVector = Icons.Outlined.Delete, contentDescription = deleteLabel)
                }
            }
            TopBarState(
                title = selectionTitle,
                navigationIcon = TopBarState.NavigationIcon(
                    icon = Icons.Outlined.Close,
                    contentDescription = clearLabel,
                    onClick = { selectedAlbumIds = emptySet() }
                ),
                actions = actions
            )
        }

        else -> {
            val baseTitle = if (selectedTab == 0) "Library" else "Albums"
            val searchPlaceholder = if (selectedTab == 0) {
                "Search wallpapers"
            } else {
                "Search albums"
            }
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
                    IconButton(onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }) {
                        Icon(imageVector = Icons.Outlined.Search, contentDescription = "Search")
                    }
                } else {
                    IconButton(onClick = { isSearchActive = true }) {
                        Icon(imageVector = Icons.Outlined.Search, contentDescription = "Search")
                    }
                }
                IconButton(onClick = { showSortSheet = true }) {
                    Icon(imageVector = Icons.AutoMirrored.Outlined.Sort, contentDescription = "Sort")
                }
            }
            val navigationIcon: TopBarState.NavigationIcon? = null
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
                navigationIcon = navigationIcon,
                actions = actions,
                titleContent = titleContent
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

    val supportsSharedTransitions = sharedTransitionScope != null && animatedVisibilityScope != null

    val onWallpaperClick: (WallpaperItem) -> Unit = { wallpaper ->
        when {
            isWallpaperSelection -> {
                selectedWallpaperIds = selectedWallpaperIds.toggle(wallpaper.id)
            }

            isAlbumSelection -> Unit

            else -> onWallpaperSelected(
                wallpaper,
                supportsSharedTransitions,
            )
        }
    }

    val onWallpaperLongPress: (WallpaperItem) -> Unit = { wallpaper ->
        if (isAlbumSelection) {
            selectedAlbumIds = emptySet()
        }
        selectedWallpaperIds = if (isWallpaperSelection) {
            selectedWallpaperIds.toggle(wallpaper.id, forceAdd = true)
        } else {
            setOf(wallpaper.id)
        }
    }

    val onAlbumClick: (AlbumItem) -> Unit = { album ->
        when {
            isAlbumSelection -> {
                selectedAlbumIds = selectedAlbumIds.toggle(album.id)
            }

            isWallpaperSelection -> Unit

            else -> onAlbumSelected(album)
        }
    }

    val onAlbumLongPress: (AlbumItem) -> Unit = { album ->
        if (isWallpaperSelection) {
            selectedWallpaperIds = emptySet()
        }
        selectedAlbumIds = if (isAlbumSelection) {
            selectedAlbumIds.toggle(album.id, forceAdd = true)
        } else {
            setOf(album.id)
        }
    }

    val wallpaperSelection = uiState.wallpaperSortOption.toSelection()
    val albumSelection = uiState.albumSortOption.toSelection()
    val availableSortFields = remember { listOf(SortField.Alphabet, SortField.DateAdded) }
    val activeSortSelection = if (selectedTab == 0) wallpaperSelection else albumSelection
    val sortSheetTitle = if (selectedTab == 0) "Sort wallpapers" else "Sort albums"
    val applySortSelection: (SortSelection) -> Unit = { selection ->
        if (selectedTab == 0) {
            libraryViewModel.updateWallpaperSort(selection.toWallpaperSortOption())
        } else {
            libraryViewModel.updateAlbumSort(selection.toAlbumSortOption())
        }
    }

    val showDownloadProgress =
        uiState.isSelectionActionInProgress &&
            uiState.selectionAction == LibraryViewModel.SelectionAction.DOWNLOAD

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            floatingActionButton = {
                when {
                    selectedTab == 0 && !selectionMode -> {
                        FloatingActionButton(onClick = { showDirectAddDialog = true }) {
                            Icon(imageVector = Icons.Outlined.Add, contentDescription = "Add wallpaper")
                        }
                    }

                    selectedTab == 1 -> {
                        val creating = uiState.isCreatingAlbum
                        ExtendedFloatingActionButton(
                            onClick = { if (!creating) showAlbumDialog = true },
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = "Add album"
                                )
                            },
                            text = { Text(text = "Add album") },
                            expanded = true,
                            modifier = Modifier.then(
                                if (creating) {
                                    Modifier
                                        .alpha(0.6f)
                                        .semantics { disabled() }
                                } else {
                                    Modifier
                                }
                            )
                        )
                    }
                }
            },
            contentWindowInsets = WindowInsets(left = 0.dp, top = 0.dp, right = 0.dp, bottom = 0.dp)
        ) { innerPadding ->
            LibraryContent(
                uiState = uiState,
                wallpapers = displayedWallpapers,
                albums = displayedAlbums,
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                onWallpaperClick = onWallpaperClick,
                onWallpaperLongPress = onWallpaperLongPress,
                wallpaperSelectionIds = selectedWallpaperIds,
                albumSelectionIds = selectedAlbumIds,
                isWallpaperSelectionMode = isWallpaperSelection,
                isAlbumSelectionMode = isAlbumSelection,
                selectionMode = selectionMode,
                onAlbumClick = onAlbumClick,
                onAlbumLongPress = onAlbumLongPress,
                onCreateAlbum = {
                    libraryViewModel.createAlbum(it)
                    showAlbumDialog = false
                },
                onRequestCreateAlbum = { showAlbumDialog = true },
                onDismissCreateAlbum = { showAlbumDialog = false },
                showAlbumDialog = showAlbumDialog,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                isSearching = isSearchActive,
                searchQuery = trimmedQuery,
                wallpaperGridColumns = wallpaperGridColumns,
                wallpaperLayout = wallpaperLayout,
                albumLayout = albumLayout,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }

        DownloadProgressToast(
            visible = showDownloadProgress,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }

    SortBottomSheet(
        visible = showSortSheet,
        title = sortSheetTitle,
        selection = activeSortSelection,
        availableFields = availableSortFields,
        onSelectionChanged = applySortSelection,
        onDismissRequest = { showSortSheet = false },
        additionalContent = {
            if (selectedTab == 0) {
                WallpaperLayoutPicker(
                    label = "Wallpaper layout",
                    selectedLayout = wallpaperLayout,
                    onLayoutSelected = onWallpaperLayoutSelected
                )
                if (wallpaperLayout == WallpaperLayout.GRID) {
                    GridColumnPicker(
                        label = "Grid columns",
                        selectedColumns = wallpaperGridColumns,
                        onColumnsSelected = onGridColumnsSelected
                    )
                }
            } else {
                AlbumLayoutPicker(
                    label = "Album layout",
                    selectedLayout = albumLayout,
                    onLayoutSelected = onAlbumLayoutSelected
                )
            }
        }
    )

    if (showSelectionAlbumDialog) {
        AlbumPickerDialog(
            albums = uiState.albums,
            isBusy = uiState.isSelectionActionInProgress,
            onAddToExisting = { albumId ->
                if (selectedWallpapers.isNotEmpty()) {
                    libraryViewModel.addWallpapersToAlbum(albumId, selectedWallpapers)
                }
                selectedWallpaperIds = emptySet()
                showSelectionAlbumDialog = false
            },
            onCreateNew = { title ->
                if (selectedWallpapers.isNotEmpty()) {
                    libraryViewModel.createAlbumAndAdd(title, selectedWallpapers)
                }
                selectedWallpaperIds = emptySet()
                showSelectionAlbumDialog = false
            },
            onDismiss = { showSelectionAlbumDialog = false }
        )
    }

    if (showRemoveDownloadsDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isSelectionActionInProgress) {
                    showRemoveDownloadsDialog = false
                }
            },
            title = { Text(text = "Remove downloaded files?") },
            text = {
                Text(
                    text = "Delete the downloaded copies for the selected wallpapers?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!uiState.isSelectionActionInProgress && selectedWallpapers.isNotEmpty()) {
                            libraryViewModel.removeDownloads(selectedWallpapers)
                            showRemoveDownloadsDialog = false
                        }
                    },
                    enabled = !uiState.isSelectionActionInProgress
                ) {
                    Text(text = "Remove")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRemoveDownloadsDialog = false },
                    enabled = !uiState.isSelectionActionInProgress
                ) {
                    Text(text = "Cancel")
                }
            }
        )
    }

    if (showDirectAddDialog) {
        DirectAddDialog(
            url = directAddUrl,
            onUrlChange = { directAddUrl = it },
            isBusy = uiState.isDirectAddInProgress,
            onConfirm = { libraryViewModel.addDirectWallpaper(directAddUrl) },
            onDismiss = { showDirectAddDialog = false }
        )
    }
}























private fun Set<String>.toggle(id: String, forceAdd: Boolean = false): Set<String> {
    val mutable = toMutableSet()
    if (!mutable.add(id) && !forceAdd) {
        mutable.remove(id)
    }
    if (forceAdd) {
        mutable.add(id)
    }
    return mutable
}

private fun Set<Long>.toggle(id: Long, forceAdd: Boolean = false): Set<Long> {
    val mutable = toMutableSet()
    if (!mutable.add(id) && !forceAdd) {
        mutable.remove(id)
    }
    if (forceAdd) {
        mutable.add(id)
    }
    return mutable
}




