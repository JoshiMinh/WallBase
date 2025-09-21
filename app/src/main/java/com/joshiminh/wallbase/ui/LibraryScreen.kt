package com.joshiminh.wallbase.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TaskAlt
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.joshiminh.wallbase.TopBarState
import com.joshiminh.wallbase.data.entity.album.AlbumItem
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem
import com.joshiminh.wallbase.ui.components.SortBottomSheet
import com.joshiminh.wallbase.ui.components.TopBarSearchField
import com.joshiminh.wallbase.ui.components.WallpaperGrid
import com.joshiminh.wallbase.ui.sort.SortField
import com.joshiminh.wallbase.ui.sort.SortSelection
import com.joshiminh.wallbase.ui.sort.toAlbumSortOption
import com.joshiminh.wallbase.ui.sort.toSelection
import com.joshiminh.wallbase.ui.sort.toWallpaperSortOption
import com.joshiminh.wallbase.ui.viewmodel.LibraryViewModel

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LibraryScreen(
    onWallpaperSelected: (WallpaperItem) -> Unit,
    onAlbumSelected: (AlbumItem) -> Unit,
    onConfigureTopBar: (TopBarState?) -> Unit,
    libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val uiState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showAlbumDialog by rememberSaveable { mutableStateOf(false) }
    var showSelectionAlbumDialog by rememberSaveable { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showRemoveDownloadsDialog by rememberSaveable { mutableStateOf(false) }
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }

    val selectionMode = selectedIds.isNotEmpty()
    val selectedWallpapers = remember(selectedIds, uiState.wallpapers) {
        if (selectedIds.isEmpty()) emptyList()
        else {
            val byId = uiState.wallpapers.associateBy { it.id }
            selectedIds.mapNotNull(byId::get)
        }
    }

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
    val displayedAlbums = remember(uiState.albums, trimmedQuery, isSearchActive) {
        if (!isSearchActive || trimmedQuery.isEmpty()) {
            uiState.albums
        } else {
            uiState.albums.filter { album ->
                album.title.contains(trimmedQuery, ignoreCase = true)
            }
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

    LaunchedEffect(selectedTab) {
        if (selectedTab != 0 && selectedIds.isNotEmpty()) {
            selectedIds = emptySet()
        }
    }

    LaunchedEffect(selectionMode) {
        if (selectionMode) {
            showSortSheet = false
            if (isSearchActive) {
                isSearchActive = false
                searchQuery = ""
            }
        } else {
            showRemoveDownloadsDialog = false
        }
    }

    LaunchedEffect(selectedTab) {
        showSortSheet = false
    }

    if (selectionMode) {
        val selectionTitle = "${selectedIds.size} selected"
        val removeLabel = "Remove from library"
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
        val baseTitle = if (selectedTab == 0) "Library" else "Albums"
        val searchPlaceholder = if (selectedTab == 0) {
            "Search wallpapers"
        } else {
            "Search albums"
        }
        val actions: @Composable RowScope.() -> Unit = {
            if (!isSearchActive) {
                IconButton(onClick = { isSearchActive = true }) {
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
                    searchQuery = ""
                }
            )
        } else {
            null
        }
        val titleContent: (@Composable () -> Unit)? = if (isSearchActive) {
            {
                TopBarSearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    onClear = { searchQuery = "" },
                    placeholder = searchPlaceholder,
                    focusRequester = searchFocusRequester
                )
            }
        } else {
            null
        }
        SideEffect {
            onConfigureTopBar(
                TopBarState(
                    title = if (isSearchActive) null else baseTitle,
                    navigationIcon = navigationIcon,
                    actions = actions,
                    titleContent = titleContent
                )
            )
        }
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
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            isSearching = isSearchActive,
            searchQuery = trimmedQuery,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }

    SortBottomSheet(
        visible = showSortSheet,
        title = sortSheetTitle,
        selection = activeSortSelection,
        availableFields = availableSortFields,
        onFieldSelected = { field ->
            applySortSelection(SortSelection(field, activeSortSelection.direction))
        },
        onDirectionSelected = { direction ->
            applySortSelection(SortSelection(activeSortSelection.field, direction))
        },
        onDismissRequest = { showSortSheet = false }
    )

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
    selectedIds: Set<String>,
    selectionMode: Boolean,
    onAlbumClick: (AlbumItem) -> Unit,
    onCreateAlbum: (String) -> Unit,
    onRequestCreateAlbum: () -> Unit,
    onDismissCreateAlbum: () -> Unit,
    showAlbumDialog: Boolean,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    isSearching: Boolean,
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("All Wallpapers", "Albums")
    val hasQuery = isSearching && searchQuery.isNotBlank()

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
                Column(modifier = Modifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (wallpapers.isEmpty()) {
                        val message = when {
                            uiState.wallpapers.isEmpty() -> "Your library is empty. Save wallpapers from Browse to see them here."
                            hasQuery -> "No wallpapers match your search."
                            else -> "No wallpapers available."
                        }
                        LibraryEmptyState(
                            message = message,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    } else {
                        WallpaperGrid(
                            wallpapers = wallpapers,
                            onWallpaperSelected = onWallpaperClick,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            onLongPress = onWallpaperLongPress,
                            selectedIds = selectedIds,
                            selectionMode = selectionMode,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                }
            }

            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (albums.isEmpty()) {
                        val hasAlbums = uiState.albums.isNotEmpty()
                        if (hasAlbums && hasQuery) {
                            LibraryEmptyState(
                                message = "No albums match your search.",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
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
                            onAlbumClick = onAlbumClick,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
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
    var selectedAlbumId by rememberSaveable { mutableStateOf(albums.firstOrNull()?.id) }
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