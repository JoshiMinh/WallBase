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
import androidx.compose.material3.Button
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
import com.joshiminh.wallbase.navigation.TopBarHandle
import com.joshiminh.wallbase.navigation.TopBarState
import com.joshiminh.wallbase.data.entity.AlbumItem
import com.joshiminh.wallbase.data.entity.WallpaperItem
import com.joshiminh.wallbase.data.repository.AlbumLayout
import com.joshiminh.wallbase.data.repository.WallpaperLayout
import com.joshiminh.wallbase.ui.components.AlbumLayoutPicker
import com.joshiminh.wallbase.ui.components.GridColumnPicker
import com.joshiminh.wallbase.ui.components.SortBottomSheet
import com.joshiminh.wallbase.ui.components.TopBarSearchField
import com.joshiminh.wallbase.ui.components.WallpaperGrid
import com.joshiminh.wallbase.ui.components.WallpaperLayoutPicker
import com.joshiminh.wallbase.util.SortField
import com.joshiminh.wallbase.util.SortSelection
import com.joshiminh.wallbase.util.toAlbumSortOption
import com.joshiminh.wallbase.util.toSelection
import com.joshiminh.wallbase.util.toWallpaperSortOption
import com.joshiminh.wallbase.ui.viewmodel.LibraryViewModel
import androidx.compose.foundation.lazy.grid.items as gridItems

@Composable
fun DirectAddDialog(
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
        title = { Text(text = "New Album/Wallpaper") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                placeholder = { Text("Paste wallpaper URL") },
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
                Text(text = if (isBusy) "Creating..." else "Create")
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

@Composable
fun CreateAlbumDialog(
    isCreating: Boolean,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    val trimmedTitle = title.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "New Album/Wallpaper") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("Enter album name") },
                enabled = !isCreating,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(trimmedTitle) },
                enabled = !isCreating && trimmedTitle.isNotBlank()
            ) {
                Text(text = if (isCreating) "Creating..." else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isCreating) {
                Text(text = "Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumPickerDialog(
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
fun AlbumSelectionRow(
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


