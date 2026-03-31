package com.joshiminh.wallbase.ui.library

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

@Composable
fun DownloadProgressToast(visible: Boolean, modifier: Modifier = Modifier) {
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LibraryContent(
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
fun LibraryEmptyState(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun AlbumList(
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
fun AlbumGridCard(
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
fun AlbumRowCard(
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
fun SelectionCheckmark(selected: Boolean, modifier: Modifier = Modifier) {
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
