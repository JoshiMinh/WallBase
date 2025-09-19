package com.joshiminh.wallbase.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joshiminh.wallbase.R
import com.joshiminh.wallbase.data.library.AlbumItem
import com.joshiminh.wallbase.data.wallpapers.WallpaperItem
import com.joshiminh.wallbase.ui.components.WallpaperGrid

@Composable
fun LibraryScreen(
    onWallpaperSelected: (WallpaperItem) -> Unit,
    libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
) {
    val uiState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        libraryViewModel.consumeMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        LibraryContent(
            uiState = uiState,
            onWallpaperSelected = onWallpaperSelected,
            onCreateAlbum = libraryViewModel::createAlbum,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

@Composable
private fun LibraryContent(
    uiState: LibraryViewModel.LibraryUiState,
    onWallpaperSelected: (WallpaperItem) -> Unit,
    onCreateAlbum: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("All Wallpapers", "Albums")
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var showAlbumDialog by rememberSaveable { mutableStateOf(false) }

    Column(modifier) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTab) {
            0 -> {
                if (uiState.wallpapers.isEmpty()) {
                    LibraryEmptyState(
                        message = stringResource(id = R.string.library_empty_state)
                    )
                } else {
                    WallpaperGrid(
                        wallpapers = uiState.wallpapers,
                        onWallpaperSelected = onWallpaperSelected,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            else -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (uiState.albums.isEmpty()) {
                        LibraryEmptyState(
                            message = stringResource(id = R.string.album_empty_state)
                        )
                    } else {
                        AlbumList(
                            albums = uiState.albums,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    TextButton(
                        onClick = { showAlbumDialog = true },
                        enabled = !uiState.isCreatingAlbum
                    ) {
                        Text(text = stringResource(id = R.string.add_album))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (showAlbumDialog) {
        CreateAlbumDialog(
            isCreating = uiState.isCreatingAlbum,
            onCreate = {
                onCreateAlbum(it)
                showAlbumDialog = false
            },
            onDismiss = { showAlbumDialog = false }
        )
    }
}

@Composable
private fun LibraryEmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AlbumList(
    albums: List<AlbumItem>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(albums, key = AlbumItem::id) { album ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(album.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.album_wallpaper_count, album.wallpaperCount),
                    style = MaterialTheme.typography.bodyMedium
                )
                album.coverImageUrl?.let { cover ->
                    AsyncImage(
                        model = cover,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateAlbumDialog(
    isCreating: Boolean,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.create_album_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(text = stringResource(id = R.string.album_name_label)) }
                )
                if (isCreating) {
                    Text(text = stringResource(id = R.string.creating_album))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(title) }, enabled = !isCreating && title.isNotBlank()) {
                Text(text = stringResource(id = R.string.create_album_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.cancel))
            }
        }
    )
}
