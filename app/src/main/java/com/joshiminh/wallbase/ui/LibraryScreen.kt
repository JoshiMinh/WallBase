package com.joshiminh.wallbase.ui

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
import com.joshiminh.wallbase.TopBarHandle
import com.joshiminh.wallbase.TopBarState
import com.joshiminh.wallbase.data.entity.album.AlbumItem
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem
import com.joshiminh.wallbase.data.repository.AlbumLayout
import com.joshiminh.wallbase.data.repository.WallpaperLayout
import com.joshiminh.wallbase.ui.components.AlbumLayoutPicker
import com.joshiminh.wallbase.ui.components.GridColumnPicker
import com.joshiminh.wallbase.ui.components.SortBottomSheet
import com.joshiminh.wallbase.ui.components.TopBarSearchField
import com.joshiminh.wallbase.ui.components.WallpaperGrid
import com.joshiminh.wallbase.ui.components.WallpaperLayoutPicker
import com.joshiminh.wallbase.ui.sort.SortField
import com.joshiminh.wallbase.ui.sort.SortSelection
import com.joshiminh.wallbase.ui.sort.toAlbumSortOption
import com.joshiminh.wallbase.ui.sort.toSelection
import com.joshiminh.wallbase.ui.sort.toWallpaperSortOption
import com.joshiminh.wallbase.ui.viewmodel.LibraryViewModel
import androidx.compose.foundation.lazy.grid.items as gridItems

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LibraryScreen(
    onWallpaperSelected: (WallpaperItem) -> Unit,
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

    val onWallpaperClick: (WallpaperItem) -> Unit = { wallpaper ->
        when {
            isWallpaperSelection -> {
                selectedWallpaperIds = selectedWallpaperIds.toggle(wallpaper.id)
            }

            isAlbumSelection -> Unit

            else -> onWallpaperSelected(wallpaper)
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

@Composable
private fun DownloadProgressToast(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = "Downloading wallpapers…",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun DirectAddDialog(
    url: String,
    onUrlChange: (String) -> Unit,
    isBusy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val canSubmit = url.isNotBlank() && !isBusy

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = {
            if (!isBusy) {
                focusManager.clearFocus()
                keyboardController?.hide()
                onDismiss()
            }
        },
        title = { Text(text = "Add wallpaper") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Paste a direct image link to save it in your library.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    label = { Text("Image link") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (canSubmit) {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                onConfirm()
                            }
                        }
                    )
                )
                Text(
                    text = "Example: https://www.reddit.com/media?url=https%3A%2F%2Fpreview.redd.it%2Fshare-your-wallpapers-high-quality-is-appreciated-v0-mfolekrpzsnf1.jpeg%3Fwidth%3D1170%26auto%3Dwebp%26s%3D7f528b4d58a0d35285312494e5a9279fbb5bc9bc",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onConfirm()
                },
                enabled = canSubmit
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(text = "Add")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onDismiss()
                },
                enabled = !isBusy
            ) {
                Text(text = "Cancel")
            }
        }
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun LibraryContent(
    uiState: LibraryViewModel.LibraryUiState,
    wallpapers: List<WallpaperItem>,
    albums: List<AlbumItem>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onWallpaperClick: (WallpaperItem) -> Unit,
    onWallpaperLongPress: (WallpaperItem) -> Unit,
    wallpaperSelectionIds: Set<String>,
    albumSelectionIds: Set<Long>,
    isWallpaperSelectionMode: Boolean,
    isAlbumSelectionMode: Boolean,
    selectionMode: Boolean,
    onAlbumClick: (AlbumItem) -> Unit,
    onAlbumLongPress: (AlbumItem) -> Unit,
    onCreateAlbum: (String) -> Unit,
    onRequestCreateAlbum: () -> Unit,
    onDismissCreateAlbum: () -> Unit,
    showAlbumDialog: Boolean,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    isSearching: Boolean,
    searchQuery: String,
    wallpaperGridColumns: Int,
    wallpaperLayout: WallpaperLayout,
    albumLayout: AlbumLayout,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        "Wallpapers" to uiState.wallpapers.size,
        "Albums" to uiState.albums.size
    )
    val hasQuery = isSearching && searchQuery.isNotBlank()

    Column(modifier) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, (title, count) ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    text = { Text("$title · $count") }
                )
            }
        }

        when (selectedTab) {
            0 -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 8.dp)
                ) {
                    if (wallpapers.isEmpty()) {
                        val message = when {
                            uiState.wallpapers.isEmpty() -> "Your library is empty. Save wallpapers from Browse to see them here."
                            hasQuery -> "No wallpapers match your search."
                            else -> "No wallpapers available."
                        }
                        LibraryEmptyState(
                            message = message,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        WallpaperGrid(
                            wallpapers = wallpapers,
                            onWallpaperSelected = onWallpaperClick,
                            modifier = Modifier.fillMaxSize(),
                            onLongPress = onWallpaperLongPress,
                            selectedIds = wallpaperSelectionIds,
                            selectionMode = isWallpaperSelectionMode,
                            columns = wallpaperGridColumns,
                            layout = wallpaperLayout,
                            showDownloadedBadge = true,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                }
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 8.dp)
                ) {
                    if (albums.isEmpty()) {
                        val hasAlbums = uiState.albums.isNotEmpty()
                        if (hasAlbums && hasQuery) {
                            LibraryEmptyState(
                                message = "No albums match your search.",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        text = "Organize wallpapers by creating your first album.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        textAlign = TextAlign.Center
                                    )
                                    TextButton(
                                        onClick = onRequestCreateAlbum,
                                        enabled = !uiState.isCreatingAlbum
                                    ) {
                                        Text(text = "Add album")
                                    }
                                }
                            }
                        }
                    } else {
                        AlbumList(
                            albums = albums,
                            layout = albumLayout,
                            onAlbumClick = onAlbumClick,
                            onAlbumLongPress = onAlbumLongPress,
                            selectedAlbumIds = albumSelectionIds,
                            selectionMode = isAlbumSelectionMode,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    if (showAlbumDialog) {
        CreateAlbumDialog(
            isCreating = uiState.isCreatingAlbum,
            onCreate = onCreateAlbum,
            onDismiss = onDismissCreateAlbum
        )
    }
}

@Composable
private fun LibraryEmptyState(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AlbumList(
    albums: List<AlbumItem>,
    layout: AlbumLayout,
    onAlbumClick: (AlbumItem) -> Unit,
    onAlbumLongPress: (AlbumItem) -> Unit,
    selectedAlbumIds: Set<Long>,
    selectionMode: Boolean,
    modifier: Modifier = Modifier
) {
    when (layout) {
        AlbumLayout.GRID -> {
            LazyVerticalGrid(
                modifier = modifier.fillMaxSize(),
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                gridItems(albums, key = AlbumItem::id) { album ->
                    val isSelected = album.id in selectedAlbumIds
                    AlbumGridCard(
                        album = album,
                        selected = isSelected,
                        selectionMode = selectionMode,
                        onClick = { onAlbumClick(album) },
                        onLongPress = { onAlbumLongPress(album) }
                    )
                }
            }
        }

        AlbumLayout.CARD_LIST -> {
            LazyColumn(
                modifier = modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(albums, key = AlbumItem::id) { album ->
                    val isSelected = album.id in selectedAlbumIds
                    AlbumRowCard(
                        album = album,
                        selected = isSelected,
                        selectionMode = selectionMode,
                        onClick = { onAlbumClick(album) },
                        onLongPress = { onAlbumLongPress(album) }
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AlbumGridCard(
    album: AlbumItem,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val indicatorColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Card(
        shape = RoundedCornerShape(24.dp),
        border = if (selected) BorderStroke(2.dp, indicatorColor) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)),
        modifier = Modifier
            .aspectRatio(3f / 4f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!album.coverImageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = album.coverImageUrl,
                    contentDescription = album.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f))
                        )
                    )
            )

            if (selectionMode) {
                SelectionCheckmark(
                    selected = selected,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ) {
                    Text(
                        text = "${album.wallpaperCount} wallpapers",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumRowCard(
    album: AlbumItem,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongPress
                )
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (!album.coverImageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = album.coverImageUrl,
                        contentDescription = album.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Wallpaper,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${album.wallpaperCount} wallpapers",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (selectionMode) {
                SelectionCheckmark(selected = selected)
            }
        }
    }
}

@Composable
private fun SelectionCheckmark(selected: Boolean, modifier: Modifier = Modifier) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor,
        tonalElevation = if (selected) 6.dp else 2.dp
    ) {
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.padding(6.dp)
        )
    }
}

@Composable
private fun CreateAlbumDialog(
    isCreating: Boolean,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "New album") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(text = "Album name") },
                    enabled = !isCreating
                )
                if (isCreating) {
                    Text(text = "Creating album…")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(title) },
                enabled = !isCreating && title.isNotBlank()
            ) {
                Text(text = "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumPickerDialog(
    albums: List<AlbumItem>,
    isBusy: Boolean,
    onAddToExisting: (Long) -> Unit,
    onCreateNew: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedAlbumId by rememberSaveable { mutableStateOf(albums.firstOrNull()?.id) }
    var newAlbumTitle by rememberSaveable { mutableStateOf("") }

    val existingTab = "Existing"
    val newTab = "New"
    val addLabel = "Add"
    val noAlbumsMessage = "Create an album in your library to start organizing wallpapers."

    val tabs = listOf(existingTab, newTab)

    LaunchedEffect(albums) {
        selectedAlbumId = when {
            albums.isEmpty() -> null
            selectedAlbumId != null && albums.any { it.id == selectedAlbumId } -> selectedAlbumId
            else -> albums.first().id
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Add to album") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    tabs.forEachIndexed { index, title ->
                        SegmentedButton(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
                            icon = {
                                val icon = if (index == 0) Icons.Outlined.Album else Icons.Outlined.Add
                                Icon(imageVector = icon, contentDescription = null)
                            },
                            label = { Text(title) }
                        )
                    }
                }
                when (selectedTab) {
                    0 -> {
                        if (albums.isEmpty()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Album,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = noAlbumsMessage,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.heightIn(max = 280.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(albums, key = AlbumItem::id) { album ->
                                    AlbumSelectionRow(
                                        album = album,
                                        isSelected = album.id == selectedAlbumId,
                                        onClick = { selectedAlbumId = album.id }
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = newAlbumTitle,
                                onValueChange = { newAlbumTitle = it },
                                label = { Text(text = "Album name") },
                                placeholder = { Text(text = "Enter a name") },
                                singleLine = true,
                                enabled = !isBusy,
                                shape = RoundedCornerShape(16.dp),
                                supportingText = if (isBusy) {
                                    { Text(text = "Creating album…") }
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedTab == 0) {
                        selectedAlbumId?.let(onAddToExisting)
                    } else {
                        onCreateNew(newAlbumTitle)
                    }
                },
                enabled = when (selectedTab) {
                    0 -> selectedAlbumId != null && !isBusy
                    else -> newAlbumTitle.isNotBlank() && !isBusy
                }
            ) {
                Text(text = addLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumSelectionRow(
    album: AlbumItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        tonalElevation = if (isSelected) 6.dp else 1.dp,
        shadowElevation = if (isSelected) 2.dp else 0.dp,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Text(
                    text = "${album.wallpaperCount} wallpapers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                RadioButton(
                    selected = isSelected,
                    onClick = onClick
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
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