package com.joshiminh.wallbase.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.joshiminh.wallbase.TopBarHandle
import com.joshiminh.wallbase.TopBarState
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem
import com.joshiminh.wallbase.ui.components.SortBottomSheet
import com.joshiminh.wallbase.ui.components.TopBarSearchField
import com.joshiminh.wallbase.ui.components.WallpaperGrid
import com.joshiminh.wallbase.ui.sort.SortField
import com.joshiminh.wallbase.ui.sort.SortSelection
import com.joshiminh.wallbase.ui.sort.toSelection
import com.joshiminh.wallbase.ui.sort.toWallpaperSortOption
import com.joshiminh.wallbase.ui.viewmodel.AlbumDetailViewModel

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AlbumDetailRoute(
    albumId: Long,
    onWallpaperSelected: (WallpaperItem) -> Unit,
    onConfigureTopBar: (TopBarState) -> TopBarHandle,
    viewModel: AlbumDetailViewModel = viewModel(factory = AlbumDetailViewModel.provideFactory(albumId)),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val title = uiState.albumTitle ?: "Album"

    val snackbarHostState = remember { SnackbarHostState() }
    val canSort = uiState.wallpapers.isNotEmpty()
    val actionEnabled = canSort &&
        !uiState.isDownloading &&
        !uiState.isRemovingDownloads &&
        !uiState.notFound
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    val availableSortFields = remember { listOf(SortField.Alphabet, SortField.DateAdded) }
    val sortSelection = uiState.wallpaperSortOption.toSelection()

    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }
    val trimmedQuery = remember(searchQuery) { searchQuery.trim() }
    val displayedWallpapers = remember(uiState.wallpapers, trimmedQuery, isSearchActive) {
        if (!isSearchActive || trimmedQuery.isEmpty()) {
            uiState.wallpapers
        } else {
            uiState.wallpapers.filter { wallpaper ->
                wallpaper.title.contains(trimmedQuery, ignoreCase = true) ||
                    (wallpaper.sourceName?.contains(trimmedQuery, ignoreCase = true) == true)
            }
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

    LaunchedEffect(canSort) {
        if (!canSort && isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        }
    }

    val navigationIcon = if (isSearchActive) {
        TopBarState.NavigationIcon(
            icon = Icons.Outlined.Close,
            contentDescription = "Close search",
            onClick = {
                isSearchActive = false
                searchQuery = ""
            }
        )
    } else {
        null
    }
    val topBarActions: @Composable RowScope.() -> Unit = {
        if (!isSearchActive) {
            IconButton(
                onClick = { isSearchActive = true },
                enabled = canSort
            ) {
                Icon(imageVector = Icons.Outlined.Search, contentDescription = "Search")
            }
        }
        IconButton(
            onClick = { showSortSheet = true },
            enabled = canSort
        ) {
            Icon(imageVector = Icons.AutoMirrored.Outlined.Sort, contentDescription = "Sort")
        }
        if (uiState.isAlbumDownloaded) {
            IconButton(
                onClick = viewModel::promptRemoveDownloads,
                enabled = actionEnabled
            ) {
                Icon(
                    imageVector = Icons.Outlined.TaskAlt,
                    contentDescription = "Remove downloaded files"
                )
            }
        } else {
            IconButton(
                onClick = viewModel::downloadAlbum,
                enabled = actionEnabled
            ) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = "Download album"
                )
            }
        }
    }
    val titleContent: (@Composable () -> Unit)? = if (isSearchActive) {
        {
            TopBarSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                onClear = { searchQuery = "" },
                placeholder = "Search album",
                focusRequester = searchFocusRequester
            )
        }
    } else {
        null
    }
    val topBarState = TopBarState(
        title = if (isSearchActive) null else title,
        navigationIcon = navigationIcon,
        actions = topBarActions,
        titleContent = titleContent
    )
    val topBarHandleState = remember { mutableStateOf<TopBarHandle?>(null) }
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

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    AlbumDetailScreen(
        state = uiState,
        wallpapers = displayedWallpapers,
        isSearching = isSearchActive,
        searchQuery = trimmedQuery,
        onWallpaperSelected = onWallpaperSelected,
        snackbarHostState = snackbarHostState,
        onConfirmRemoveDownloads = viewModel::removeAlbumDownloads,
        onDismissRemoveDownloads = viewModel::dismissRemoveDownloadsPrompt,
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
        onDismissRequest = { showSortSheet = false }
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AlbumDetailScreen(
    state: AlbumDetailViewModel.AlbumDetailUiState,
    wallpapers: List<WallpaperItem>,
    isSearching: Boolean,
    searchQuery: String,
    onWallpaperSelected: (WallpaperItem) -> Unit,
    snackbarHostState: SnackbarHostState,
    onConfirmRemoveDownloads: () -> Unit,
    onDismissRemoveDownloads: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?
) {
    val hasQuery = isSearching && searchQuery.isNotBlank()

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets(left = 0.dp, top = 0.dp, right = 0.dp, bottom = 0.dp)
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.notFound -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Album not found.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (state.isDownloading) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("Downloading…") }
                        )
                    }
                    if (state.isRemovingDownloads) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("Removing downloads…") }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (wallpapers.isEmpty()) {
                        val message = when {
                            state.wallpapers.isEmpty() -> "This album doesn't have any wallpapers yet."
                            hasQuery -> "No wallpapers match your search."
                            else -> "No wallpapers available."
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else {
                        WallpaperGrid(
                            wallpapers = wallpapers,
                            onWallpaperSelected = onWallpaperSelected,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                }
            }
        }
    }

    if (state.showRemoveDownloadsConfirmation) {
        AlertDialog(
            onDismissRequest = {
                if (!state.isRemovingDownloads) {
                    onDismissRemoveDownloads()
                }
            },
            title = { Text(text = "Remove downloaded files?") },
            text = {
                Text(
                    text = "Delete the downloaded copies saved for this album?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirmRemoveDownloads,
                    enabled = !state.isRemovingDownloads
                ) {
                    Text(text = "Remove")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissRemoveDownloads,
                    enabled = !state.isRemovingDownloads
                ) {
                    Text(text = "Cancel")
                }
            }
        )
    }
}

