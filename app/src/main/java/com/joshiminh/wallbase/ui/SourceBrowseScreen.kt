package com.joshiminh.wallbase.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joshiminh.wallbase.data.library.AlbumItem
import com.joshiminh.wallbase.data.wallpapers.WallpaperItem
import com.joshiminh.wallbase.TopBarState
import com.joshiminh.wallbase.ui.components.WallpaperGrid

@Composable
fun SourceBrowseRoute(
    sourceKey: String,
    onWallpaperSelected: (WallpaperItem) -> Unit,
    onConfigureTopBar: (TopBarState?) -> Unit,
    viewModel: SourceBrowseViewModel = viewModel(factory = SourceBrowseViewModel.provideFactory(sourceKey))
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val overrideTitle = uiState.source?.title
    LaunchedEffect(overrideTitle) {
        onConfigureTopBar(overrideTitle?.let { TopBarState(title = it) })
    }
    DisposableEffect(Unit) {
        onDispose { onConfigureTopBar(null) }
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
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
        onQueryChange = viewModel::updateQuery,
        onClearQuery = viewModel::clearQuery,
        onSearch = viewModel::search,
        onRefresh = viewModel::refresh,
        onWallpaperClick = onCardClick,
        onWallpaperLongPress = onCardLongPress,
        onClearSelection = viewModel::clearSelection,
        onAddSelectionToLibrary = viewModel::addSelectedToLibrary,
        onAddSelectionToAlbum = viewModel::addSelectedToAlbum
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceBrowseScreen(
    state: SourceBrowseViewModel.SourceBrowseUiState,
    snackbarHostState: SnackbarHostState,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onWallpaperClick: (WallpaperItem) -> Unit,
    onWallpaperLongPress: (WallpaperItem) -> Unit,
    onClearSelection: () -> Unit,
    onAddSelectionToLibrary: () -> Unit,
    onAddSelectionToAlbum: (Long) -> Unit
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

    var showAlbumPicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            AnimatedVisibility(visible = state.isSelectionMode) {
                SelectionBar(
                    count = state.selectedIds.size,
                    enabled = !state.isActionInProgress,
                    onClear = onClearSelection,
                    onAddToLibrary = onAddSelectionToLibrary,
                    onAddToAlbum = {
                        if (!state.isActionInProgress) {
                            showAlbumPicker = true
                        }
                    }
                )
            }

            if (state.isSelectionMode) {
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (source.description.isNotBlank()) {
                Text(source.description, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = { Text("Search this source") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() })
            )
            Spacer(modifier = Modifier.height(8.dp))
            RowActions(
                query = state.query,
                onSearch = onSearch,
                onClearQuery = onClearQuery
            )
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
                            modifier = Modifier.fillMaxSize()
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
                showAlbumPicker = false
            },
            onDismiss = { showAlbumPicker = false }
        )
    }
}

@Composable
private fun RowActions(
    query: String,
    onSearch: () -> Unit,
    onClearQuery: () -> Unit
) {
    val canClear = query.isNotBlank()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = onSearch) {
            Text("Search")
        }
        if (canClear) {
            TextButton(onClick = onClearQuery) {
                Text("Clear search")
            }
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    enabled: Boolean,
    onClear: () -> Unit,
    onAddToLibrary: () -> Unit,
    onAddToAlbum: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Cancel"
                )
            }
            Text(
                text = "$count selected",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.weight(1f))
            FilledTonalButton(
                onClick = onAddToLibrary,
                enabled = enabled
            ) {
                Icon(
                    imageVector = Icons.Outlined.LibraryAdd,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Save to library")
            }
            FilledTonalButton(
                onClick = onAddToAlbum,
                enabled = enabled
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlaylistAdd,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Add to album")
            }
        }
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