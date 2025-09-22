package com.joshiminh.wallbase.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joshiminh.wallbase.TopBarState
import com.joshiminh.wallbase.data.entity.album.AlbumItem
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem
import com.joshiminh.wallbase.ui.components.GridColumnPicker
import com.joshiminh.wallbase.ui.components.SortBottomSheet
import com.joshiminh.wallbase.ui.components.TopBarSearchField
import com.joshiminh.wallbase.ui.components.WallpaperGrid
import com.joshiminh.wallbase.ui.sort.SortField
import com.joshiminh.wallbase.ui.sort.SortSelection
import com.joshiminh.wallbase.ui.sort.toSelection
import com.joshiminh.wallbase.ui.sort.toWallpaperSortOption
import com.joshiminh.wallbase.ui.viewmodel.SourceBrowseViewModel

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SourceBrowseRoute(
    sourceKey: String,
    onWallpaperSelected: (WallpaperItem) -> Unit,
    onConfigureTopBar: (TopBarState?) -> Unit,
    viewModel: SourceBrowseViewModel = viewModel(factory = SourceBrowseViewModel.provideFactory(sourceKey)),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var showAlbumPicker by rememberSaveable { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }

    val overrideTitle = uiState.source?.title
    val selectionCount = uiState.selectedIds.size
    val availableSortFields = remember { listOf(SortField.Alphabet, SortField.DateAdded) }
    val sortSelection = uiState.wallpaperSortOption.toSelection()
    val topBarState = when {
        uiState.isSelectionMode -> {
            TopBarState(
                title = "$selectionCount selected",
                navigationIcon = TopBarState.NavigationIcon(
                    icon = Icons.Outlined.Close,
                    contentDescription = "Cancel selection",
                    onClick = {
                        showAlbumPicker = false
                        viewModel.clearSelection()
                    }
                ),
                actions = {
                    IconButton(
                        onClick = viewModel::addSelectedToLibrary,
                        enabled = !uiState.isActionInProgress
                    ) {
                        Icon(imageVector = Icons.Outlined.LibraryAdd, contentDescription = "Save to library")
                    }
                    IconButton(
                        onClick = { showAlbumPicker = true },
                        enabled = !uiState.isActionInProgress
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.PlaylistAdd,
                            contentDescription = "Add to album"
                        )
                    }
                }
            )
        }

        overrideTitle != null -> {
            val actions: @Composable RowScope.() -> Unit = {
                if (!isSearchActive) {
                    IconButton(onClick = { isSearchActive = true }) {
                        Icon(imageVector = Icons.Outlined.Search, contentDescription = "Search")
                    }
                } else {
                    IconButton(onClick = {
                        viewModel.search()
                        keyboardController?.hide()
                    }) {
                        Icon(imageVector = Icons.Outlined.Search, contentDescription = "Search")
                    }
                }
                IconButton(onClick = { showSortSheet = true }) {
                    Icon(imageVector = Icons.AutoMirrored.Outlined.Sort, contentDescription = "Sort")
                }
            }
            val navigationIcon = if (isSearchActive) {
                TopBarState.NavigationIcon(
                    icon = Icons.Outlined.Close,
                    contentDescription = "Close search",
                    onClick = {
                        isSearchActive = false
                        viewModel.clearQuery()
                    }
                )
            } else {
                null
            }
            val titleContent: (@Composable () -> Unit)? = if (isSearchActive) {
                {
                    TopBarSearchField(
                        value = uiState.query,
                        onValueChange = viewModel::updateQuery,
                        onClear = {
                            viewModel.clearQuery()
                            isSearchActive = false
                        },
                        placeholder = "Search…",
                        focusRequester = searchFocusRequester,
                        onSearch = viewModel::search
                    )
                }
            } else {
                null
            }
            TopBarState(
                title = if (isSearchActive) null else overrideTitle,
                navigationIcon = navigationIcon,
                actions = actions,
                titleContent = titleContent
            )
        }

        else -> null
    }
    SideEffect {
        onConfigureTopBar(topBarState)
    }
    DisposableEffect(Unit) {
        onDispose { onConfigureTopBar(null) }
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

    LaunchedEffect(uiState.isSelectionMode) {
        if (uiState.isSelectionMode) {
            showSortSheet = false
            if (isSearchActive) {
                isSearchActive = false
            }
        } else {
            showAlbumPicker = false
        }
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        try {
            snackbarHostState.showSnackbar(message)
        } finally {
            viewModel.consumeMessage()
        }
    }

    val onCardClick: (WallpaperItem) -> Unit = { wallpaper ->
        if (uiState.isSelectionMode) {
            viewModel.toggleSelection(wallpaper)
        } else {
            onWallpaperSelected(wallpaper)
        }
    }

    val onCardLongPress: (WallpaperItem) -> Unit = { wallpaper ->
        viewModel.beginSelection(wallpaper)
    }

    SourceBrowseScreen(
        state = uiState,
        snackbarHostState = snackbarHostState,
        onRefresh = viewModel::refresh,
        onWallpaperClick = onCardClick,
        onWallpaperLongPress = onCardLongPress,
        onAddSelectionToAlbum = viewModel::addSelectedToAlbum,
        onDismissAlbumPicker = { showAlbumPicker = false },
        showAlbumPicker = showAlbumPicker,
        onLoadMore = viewModel::loadMore,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope
    )

    SortBottomSheet(
        visible = showSortSheet,
        title = "Sort wallpapers",
        selection = sortSelection,
        availableFields = availableSortFields,
        onFieldSelected = { field ->
            val updated = SortSelection(field, sortSelection.direction)
            viewModel.updateSort(updated.toWallpaperSortOption())
        },
        onDirectionSelected = { direction ->
            val updated = SortSelection(sortSelection.field, direction)
            viewModel.updateSort(updated.toWallpaperSortOption())
        },
        onDismissRequest = { showSortSheet = false },
        additionalContent = {
            GridColumnPicker(
                label = "Grid columns",
                selectedColumns = uiState.wallpaperGridColumns,
                onColumnsSelected = viewModel::updateGridColumns
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun SourceBrowseScreen(
    state: SourceBrowseViewModel.SourceBrowseUiState,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onWallpaperClick: (WallpaperItem) -> Unit,
    onWallpaperLongPress: (WallpaperItem) -> Unit,
    onAddSelectionToAlbum: (Long) -> Unit,
    onDismissAlbumPicker: () -> Unit,
    showAlbumPicker: Boolean,
    onLoadMore: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?
) {
    val source = state.source
    if (source == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = state.errorMessage ?: "Source not available",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets(left = 0.dp, top = 0.dp, right = 0.dp, bottom = 0.dp)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            state.errorMessage?.takeIf { state.wallpapers.isEmpty() }?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    state.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    state.wallpapers.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.errorMessage != null) {
                                ErrorMessage(message = state.errorMessage, onRetry = onRefresh)
                            } else {
                                Text(
                                    text = "No wallpapers found.",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }

                    else -> {
                        WallpaperGrid(
                            wallpapers = state.wallpapers,
                            onWallpaperSelected = onWallpaperClick,
                            onLongPress = onWallpaperLongPress,
                            selectedIds = state.selectedIds,
                            selectionMode = state.isSelectionMode,
                            savedWallpaperKeys = state.savedWallpaperKeys,
                            savedRemoteIdsByProvider = state.savedRemoteIdsByProvider,
                            savedImageUrls = state.savedImageUrls,
                            onLoadMore = onLoadMore.takeIf { state.canLoadMore },
                            isLoadingMore = state.isAppending,
                            canLoadMore = state.canLoadMore,
                            modifier = Modifier.fillMaxSize(),
                            columns = state.wallpaperGridColumns,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                }
            }

            state.errorMessage?.takeIf { state.wallpapers.isNotEmpty() }?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }

    if (showAlbumPicker) {
        AlbumPickerDialog(
            albums = state.albums,
            onAlbumSelected = { album ->
                onAddSelectionToAlbum(album.id)
                onDismissAlbumPicker()
            },
            onDismiss = onDismissAlbumPicker
        )
    }
}

@Composable
private fun AlbumPickerDialog(
    albums: List<AlbumItem>,
    onAlbumSelected: (AlbumItem) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Choose an album") },
        text = {
            if (albums.isEmpty()) {
                Text(text = "Create an album in your library to start organizing wallpapers.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    albums.forEach { album ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            tonalElevation = 1.dp,
                            onClick = { onAlbumSelected(album) }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = album.title,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${album.wallpaperCount} wallpapers",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}

@Composable
private fun ErrorMessage(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}