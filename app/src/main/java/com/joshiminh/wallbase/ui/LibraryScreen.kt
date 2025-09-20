package com.joshiminh.wallbase.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.joshiminh.wallbase.TopBarState
import com.joshiminh.wallbase.data.entity.album.AlbumItem
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem
import com.joshiminh.wallbase.ui.components.WallpaperGrid
import com.joshiminh.wallbase.ui.viewmodel.LibraryViewModel

@Composable
fun LibraryScreen(
    onWallpaperSelected: (WallpaperItem) -> Unit,
    onAlbumSelected: (AlbumItem) -> Unit,
    onConfigureTopBar: (TopBarState?) -> Unit,
    libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
) {
    val uiState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showAlbumDialog by rememberSaveable { mutableStateOf(false) }
    var showSelectionAlbumDialog by rememberSaveable { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val selectionMode = selectedIds.isNotEmpty()
    val selectedWallpapers = remember(selectedIds, uiState.wallpapers) {
        if (selectedIds.isEmpty()) emptyList()
        else {
            val byId = uiState.wallpapers.associateBy { it.id }
            selectedIds.mapNotNull(byId::get)
        }
    }

    LaunchedEffect(uiState.wallpapers) {
        if (selectedIds.isEmpty()) return@LaunchedEffect
        val available = uiState.wallpapers.mapTo(hashSetOf()) { it.id }
        val filtered = selectedIds.filterTo(mutableSetOf()) { it in available }
        if (filtered.size != selectedIds.size) {
            selectedIds = filtered
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            libraryViewModel.consumeMessage()
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab != 0 && selectedIds.isNotEmpty()) {
            selectedIds = emptySet()
        }
    }

    if (selectionMode) {
        val selectionTitle = "${selectedIds.size} selected"
        val removeLabel = "Remove from library"
        val addLabel = "Add to album"
        val clearLabel = "Clear selection"
        val actions: @Composable RowScope.() -> Unit = {
            IconButton(
                onClick = {
                    if (selectedWallpapers.isNotEmpty()) {
                        libraryViewModel.removeWallpapers(selectedWallpapers)
                    }
                    selectedIds = emptySet()
                },
                enabled = !uiState.isSelectionActionInProgress
            ) {
                Icon(imageVector = Icons.Outlined.Delete, contentDescription = removeLabel)
            }
            IconButton(
                onClick = {
                    if (!uiState.isSelectionActionInProgress) {
                        showSelectionAlbumDialog = true
                    }
                },
                enabled = !uiState.isSelectionActionInProgress
            ) {
                Icon(imageVector = Icons.Outlined.PlaylistAdd, contentDescription = addLabel)
            }
        }
        SideEffect {
            onConfigureTopBar(
                TopBarState(
                    title = selectionTitle,
                    navigationIcon = TopBarState.NavigationIcon(
                        icon = Icons.Outlined.Close,
                        contentDescription = clearLabel,
                        onClick = { selectedIds = emptySet() }
                    ),
                    actions = actions
                )
            )
        }
    } else {
        SideEffect { onConfigureTopBar(null) }
    }

    DisposableEffect(Unit) {
        onDispose { onConfigureTopBar(null) }
    }

    val onWallpaperClick: (WallpaperItem) -> Unit = { wallpaper ->
        if (selectionMode) {
            selectedIds = selectedIds.toggle(wallpaper.id)
        } else {
            onWallpaperSelected(wallpaper)
        }
    }

    val onWallpaperLongPress: (WallpaperItem) -> Unit = { wallpaper ->
        selectedIds = if (selectionMode) {
            selectedIds.toggle(wallpaper.id, forceAdd = true)
        } else {
            setOf(wallpaper.id)
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
    ) { innerPadding ->
        LibraryContent(
            uiState = uiState,
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            onWallpaperClick = onWallpaperClick,
            onWallpaperLongPress = onWallpaperLongPress,
            selectedIds = selectedIds,
            selectionMode = selectionMode,
            onAlbumClick = onAlbumSelected,
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

    if (showSelectionAlbumDialog) {
        AlbumPickerDialog(
            albums = uiState.albums,
            isBusy = uiState.isSelectionActionInProgress,
            onAddToExisting = { albumId ->
                if (selectedWallpapers.isNotEmpty()) {
                    libraryViewModel.addWallpapersToAlbum(albumId, selectedWallpapers)
                }
                selectedIds = emptySet()
                showSelectionAlbumDialog = false
            },
            onCreateNew = { title ->
                if (selectedWallpapers.isNotEmpty()) {
                    libraryViewModel.createAlbumAndAdd(title, selectedWallpapers)
                }
                selectedIds = emptySet()
                showSelectionAlbumDialog = false
            },
            onDismiss = { showSelectionAlbumDialog = false }
        )
    }
}

@Composable
private fun LibraryContent(
    uiState: LibraryViewModel.LibraryUiState,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onWallpaperClick: (WallpaperItem) -> Unit,
    onWallpaperLongPress: (WallpaperItem) -> Unit,
    selectedIds: Set<String>,
    selectionMode: Boolean,
    onAlbumClick: (AlbumItem) -> Unit,
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
                    LibraryEmptyState(message = "Your library is empty. Save wallpapers from Browse to see them here.")
                } else {
                    WallpaperGrid(
                        wallpapers = uiState.wallpapers,
                        onWallpaperSelected = onWallpaperClick,
                        modifier = Modifier.fillMaxSize(),
                        onLongPress = onWallpaperLongPress,
                        selectedIds = selectedIds,
                        selectionMode = selectionMode
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
                        LibraryEmptyState(message = "Organize wallpapers by creating your first album.")
                    } else {
                        AlbumList(
                            albums = uiState.albums,
                            onAlbumClick = onAlbumClick,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    TextButton(
                        onClick = onRequestCreateAlbum,
                        enabled = !uiState.isCreatingAlbum
                    ) {
                        Text(text = "Add album")
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
    onAlbumClick: (AlbumItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(albums, key = AlbumItem::id) { album ->
            AlbumCard(album = album, onClick = { onAlbumClick(album) })
        }
    }
}

@Composable
private fun AlbumCard(
    album: AlbumItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${album.wallpaperCount} wallpapers",
                style = MaterialTheme.typography.bodyMedium
            )
            album.coverImageUrl?.let { cover ->
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
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

@Composable
private fun AlbumPickerDialog(
    albums: List<AlbumItem>,
    isBusy: Boolean,
    onAddToExisting: (Long) -> Unit,
    onCreateNew: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedAlbumId by rememberSaveable { mutableStateOf<Long?>(albums.firstOrNull()?.id) }
    var newAlbumTitle by rememberSaveable { mutableStateOf("") }

    val existingTab = "Existing"
    val newTab = "New"
    val addLabel = "Add"
    val noAlbumsMessage = "Create an album in your library to start organizing wallpapers."

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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TabRow(selectedTabIndex = selectedTab) {
                    listOf(existingTab, newTab).forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
                when (selectedTab) {
                    0 -> {
                        if (albums.isEmpty()) {
                            Text(text = noAlbumsMessage, style = MaterialTheme.typography.bodyMedium)
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.heightIn(max = 240.dp)
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
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                TextField(
                                    value = newAlbumTitle,
                                    onValueChange = { newAlbumTitle = it },
                                    label = { Text(text = "Album name") },
                                    enabled = !isBusy
                                )
                                if (isBusy) {
                                    Text(text = "Creating album…")
                                }
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

@Composable
private fun AlbumSelectionRow(
    album: AlbumItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val background = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${album.wallpaperCount} wallpapers",
                style = MaterialTheme.typography.bodySmall
            )
        }
        RadioButton(
            selected = isSelected,
            onClick = onClick
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