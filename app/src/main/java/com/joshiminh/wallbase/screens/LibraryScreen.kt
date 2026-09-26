package com.joshiminh.wallbase.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joshiminh.wallbase.data.entity.AlbumItem
import com.joshiminh.wallbase.data.entity.WallpaperItem
import com.joshiminh.wallbase.data.repository.WallpaperLayout
import com.joshiminh.wallbase.navigation.TopBarHandle
import com.joshiminh.wallbase.navigation.TopBarState
import com.joshiminh.wallbase.ui.AlbumPickerDialog
import com.joshiminh.wallbase.ui.DirectAddDialog
import com.joshiminh.wallbase.ui.DownloadProgressToast
import com.joshiminh.wallbase.ui.components.GridColumnPicker
import com.joshiminh.wallbase.ui.components.RenameWallpaperDialog
import com.joshiminh.wallbase.ui.components.SheetTab
import com.joshiminh.wallbase.ui.components.TopBarSearchField
import com.joshiminh.wallbase.ui.components.ViewFilterSortBottomSheet
import com.joshiminh.wallbase.ui.components.WallpaperGrid
import com.joshiminh.wallbase.ui.components.WallpaperLayoutPicker
import com.joshiminh.wallbase.ui.components.bottomBarInsetPadding
import com.joshiminh.wallbase.ui.components.topBarInsetPadding
import com.joshiminh.wallbase.ui.viewmodel.LibraryViewModel
import com.joshiminh.wallbase.util.SortField
import com.joshiminh.wallbase.util.toSelection
import com.joshiminh.wallbase.util.toWallpaperSortOption

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onWallpaperSelected: (WallpaperItem, Boolean, List<WallpaperItem>) -> Unit,
    onConfigureTopBar: (TopBarState) -> TopBarHandle,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val uiState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showSelectionAlbumDialog by rememberSaveable { mutableStateOf(false) }
    var selectedWallpaperIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showRemoveDownloadsDialog by rememberSaveable { mutableStateOf(false) }
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showDirectAddDialog by rememberSaveable { mutableStateOf(false) }
    var directAddUrl by rememberSaveable { mutableStateOf("") }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }

    val isWallpaperSelection = selectedWallpaperIds.isNotEmpty()
    val wallpapersById = remember(uiState.wallpapers) {
        uiState.wallpapers.associateBy { it.id }
    }
    val selectedWallpapers = remember(selectedWallpaperIds, wallpapersById) {
        if (selectedWallpaperIds.isEmpty()) emptyList()
        else selectedWallpaperIds.mapNotNull(wallpapersById::get)
    }

    val trimmedQuery = remember(searchQuery) { searchQuery.trim() }
    val wallpaperGridColumns = uiState.wallpaperGridColumns
    val wallpaperLayout = uiState.wallpaperLayout

    val displayedWallpapers = remember(uiState.wallpapers, trimmedQuery, isSearchActive) {
        if (!isSearchActive || trimmedQuery.isEmpty()) {
            uiState.wallpapers
        } else {
            uiState.wallpapers.filter { wallpaper ->
                wallpaper.displayTitle.contains(trimmedQuery, ignoreCase = true) ||
                    (wallpaper.sourceName?.contains(trimmedQuery, ignoreCase = true) == true) ||
                    (wallpaper.sourceKey?.contains(trimmedQuery, ignoreCase = true) == true)
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

    LaunchedEffect(uiState.wallpapers) {
        if (selectedWallpaperIds.isEmpty()) return@LaunchedEffect
        val available = uiState.wallpapers.mapTo(hashSetOf()) { it.id }
        val filtered = selectedWallpaperIds.filterTo(mutableSetOf()) { it in available }
        if (filtered.size != selectedWallpaperIds.size) {
            selectedWallpaperIds = filtered
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
            false -> libraryViewModel.consumeDirectAddStatus()
            null -> Unit
        }
    }

    LaunchedEffect(showDirectAddDialog) {
        if (!showDirectAddDialog) directAddUrl = ""
    }

    LaunchedEffect(isWallpaperSelection) {
        if (isWallpaperSelection) {
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

    val topBarHandleState = remember { mutableStateOf<TopBarHandle?>(null) }
    val topBarState = when {
        isWallpaperSelection -> {
            val selectionTitle = "${selectedWallpaperIds.size} selected"
            val removeLabel = "Remove from library"
            val selectAllLabel = "Select all"
            val allSelectedDownloaded = selectedWallpapers.isNotEmpty() &&
                selectedWallpapers.all { it.isDownloaded && !it.localUri.isNullOrBlank() }
            val downloadLabel = if (allSelectedDownloaded) "Remove downloads" else "Download"
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
                if (selectedWallpaperIds.size == 1) {
                    IconButton(
                        onClick = {
                            if (!uiState.isSelectionActionInProgress) {
                                showRenameDialog = true
                            }
                        },
                        enabled = !uiState.isSelectionActionInProgress
                    ) {
                        Icon(imageVector = Icons.Outlined.Edit, contentDescription = "Rename wallpaper")
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
                    Icon(imageVector = Icons.AutoMirrored.Outlined.PlaylistAdd, contentDescription = "Add to album")
                }
            }
            TopBarState(
                title = selectionTitle,
                navigationIcon = TopBarState.NavigationIcon(
                    icon = Icons.Outlined.Close,
                    contentDescription = clearLabel,
                    onClick = { selectedWallpaperIds = emptySet() }
                ),
                actions = actions,
                autoHideBars = false
            )
        }

        else -> {
            val baseTitle = "Library"
            val searchPlaceholder = "Search wallpapers"
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
                autoHideBars = !isSearchActive
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
        if (isWallpaperSelection) {
            selectedWallpaperIds = if (wallpaper.id in selectedWallpaperIds) selectedWallpaperIds - wallpaper.id else selectedWallpaperIds + wallpaper.id
        } else {
            onWallpaperSelected(wallpaper, supportsSharedTransitions, displayedWallpapers)
        }
    }

    val onWallpaperLongPress: (WallpaperItem) -> Unit = { wallpaper ->
        selectedWallpaperIds = if (wallpaper.id in selectedWallpaperIds) selectedWallpaperIds - wallpaper.id else selectedWallpaperIds + wallpaper.id
    }

    val wallpaperSelection = uiState.wallpaperSortOption.toSelection()
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
                        .padding(top = topBarInsetPadding(8.dp, hasTabBar = false))
                )
            },
            modifier = Modifier.fillMaxSize()
        ) {
            if (displayedWallpapers.isEmpty()) {
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
                                    imageVector = Icons.Outlined.Collections,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = if (isSearchActive) "No wallpapers match \"$trimmedQuery\""
                            else "Your library is empty",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!isSearchActive) {
                            Text(
                                text = "Save wallpapers from Browse, Search, or add directly using the + button.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = { showDirectAddDialog = true }) {
                                Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
                                Text(text = "Add wallpaper", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            } else {
                WallpaperGrid(
                    wallpapers = displayedWallpapers,
                    onWallpaperSelected = onWallpaperClick,
                    onLongPress = onWallpaperLongPress,
                    modifier = Modifier.fillMaxSize(),
                    columns = wallpaperGridColumns,
                    layout = wallpaperLayout,
                    selectedIds = selectedWallpaperIds,
                    selectionMode = isWallpaperSelection,
                    showDownloadedBadge = uiState.showDownloadBadge,
                    contentPadding = PaddingValues(
                        start = 4.dp,
                        top = topBarInsetPadding(4.dp, hasTabBar = false),
                        end = 4.dp,
                        bottom = bottomBarInsetPadding(4.dp, hasBottomNav = true)
                    ),
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }
        }

        // FAB to add wallpaper
        if (!isWallpaperSelection) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 96.dp)
            ) {
                FloatingActionButton(onClick = { showDirectAddDialog = true }) {
                    Icon(imageVector = Icons.Outlined.Add, contentDescription = "Add wallpaper")
                }
            }
        }

        DownloadProgressToast(
            visible = uiState.isSelectionActionInProgress &&
                uiState.selectionAction == LibraryViewModel.SelectionAction.DOWNLOAD,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 80.dp)
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

    if (showSelectionAlbumDialog && selectedWallpapers.isNotEmpty()) {
        AlbumPickerDialog(
            albums = uiState.albums,
            initialSelectedAlbumIds = emptySet(),
            isBusy = uiState.isCreatingAlbum || uiState.isSelectionActionInProgress,
            onConfirm = { albumIds ->
                if (albumIds.isNotEmpty()) {
                    libraryViewModel.addWallpapersToAlbums(albumIds, selectedWallpapers)
                }
                showSelectionAlbumDialog = false
                selectedWallpaperIds = emptySet()
            },
            onCreateNew = { title ->
                libraryViewModel.createAlbumAndAdd(title, selectedWallpapers)
                showSelectionAlbumDialog = false
                selectedWallpaperIds = emptySet()
            },
            onDismiss = { showSelectionAlbumDialog = false }
        )
    }

    if (showRenameDialog && selectedWallpapers.size == 1) {
        val wallpaper = selectedWallpapers.first()
        RenameWallpaperDialog(
            wallpaper = wallpaper,
            onConfirm = { newTitle ->
                libraryViewModel.renameWallpaper(wallpaper, newTitle)
                showRenameDialog = false
                selectedWallpaperIds = emptySet()
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    ViewFilterSortBottomSheet(
        visible = showSortSheet,
        onDismissRequest = { showSortSheet = false },
        availableTabs = listOf(SheetTab.FILTER, SheetTab.SORT, SheetTab.DISPLAY),
        initialTab = SheetTab.SORT,
        sortSelection = wallpaperSelection,
        availableSortFields = availableSortFields,
        onSortSelectionChanged = { selection ->
            libraryViewModel.updateWallpaperSort(selection.toWallpaperSortOption())
        },
        downloadedFilter = uiState.downloadedFilter,
        onDownloadedFilterChanged = libraryViewModel::updateDownloadedFilter,
        favoritesOnly = uiState.favoritesOnly,
        onFavoritesOnlyChanged = libraryViewModel::updateFavoritesFilter,
        wallpaperLayout = wallpaperLayout,
        onWallpaperLayoutChanged = onWallpaperLayoutSelected,
        gridColumns = wallpaperGridColumns,
        onGridColumnsChanged = onGridColumnsSelected
    )
}
