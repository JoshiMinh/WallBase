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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*          // Tab, TextButton, etc.
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
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
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // if your compiler complains, change to mutableStateOf(0)
    var showAlbumDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            libraryViewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (selectedTab == 1) {
                val creating = uiState.isCreatingAlbum
                ExtendedFloatingActionButton(
                    onClick = { if (!creating) showAlbumDialog = true },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.add_album)
                        )
                    },
                    text = { Text(text = stringResource(R.string.add_album)) },
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
    ) { innerPadding ->
        LibraryContent(
            uiState = uiState,
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            onWallpaperSelected = onWallpaperSelected,
            onCreateAlbum = {
                libraryViewModel.createAlbum(it)
                showAlbumDialog = false
            },
            onRequestCreateAlbum = { showAlbumDialog = true },
            onDismissCreateAlbum = { showAlbumDialog = false },
            showAlbumDialog = showAlbumDialog,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

@Composable
private fun LibraryContent(
    uiState: LibraryViewModel.LibraryUiState,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onWallpaperSelected: (WallpaperItem) -> Unit,
    onCreateAlbum: (String) -> Unit,
    onRequestCreateAlbum: () -> Unit,
    onDismissCreateAlbum: () -> Unit,
    showAlbumDialog: Boolean,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("All Wallpapers", "Albums")

    Column(modifier) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTab) {
            0 -> {
                if (uiState.wallpapers.isEmpty()) {
                    LibraryEmptyState(messageRes = R.string.library_empty_state)
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
                        LibraryEmptyState(messageRes = R.string.album_empty_state)
                    } else {
                        AlbumList(
                            albums = uiState.albums,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    TextButton(
                        onClick = onRequestCreateAlbum,
                        enabled = !uiState.isCreatingAlbum
                    ) {
                        Text(text = stringResource(R.string.add_album))
                    }
                    Spacer(Modifier.height(8.dp))
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
private fun LibraryEmptyState(messageRes: Int) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = stringResource(messageRes), style = MaterialTheme.typography.bodyLarge)
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
        // Use the index-based items form to avoid any key/overload confusion.
        val count = albums.size
        items(count) { index ->
            val album = albums[index]
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
        title = { Text(text = stringResource(R.string.create_album_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(text = stringResource(R.string.album_name_label)) }
                )
                if (isCreating) {
                    Text(text = stringResource(R.string.creating_album))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(title) },
                enabled = !isCreating && title.isNotBlank()
            ) {
                Text(text = stringResource(R.string.create_album_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}