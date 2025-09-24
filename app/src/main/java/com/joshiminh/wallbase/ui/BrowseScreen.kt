package com.joshiminh.wallbase.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.joshiminh.wallbase.sources.reddit.RedditCommunity
import com.joshiminh.wallbase.data.entity.source.Source
import com.joshiminh.wallbase.data.entity.source.SourceKeys
import com.joshiminh.wallbase.data.repository.SourceRepository
import com.joshiminh.wallbase.ui.viewmodel.SourcesViewModel
import java.util.Locale

@Composable
fun BrowseScreen(
    uiState: SourcesViewModel.SourcesUiState,
    onGoogleDriveClick: () -> Unit,
    onGooglePhotosClick: () -> Unit,
    onUpdateSourceInput: (String) -> Unit,
    onSearchReddit: () -> Unit,
    onAddSourceFromInput: () -> Unit,
    onAddRedditCommunity: (RedditCommunity) -> Unit,
    onClearSearchResults: () -> Unit,
    onOpenSource: (Source) -> Unit,
    onRemoveSource: (Source, Boolean) -> Unit,
    onMessageShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingRemoval by remember { mutableStateOf<Source?>(null) }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage ?: return@LaunchedEffect
        try {
            snackbarHostState.showSnackbar(message)
        } finally {
            onMessageShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(left = 0.dp, top = 0.dp, right = 0.dp, bottom = 0.dp)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item("add_remote_source") {
                AddSourceFromUrlCard(
                    input = uiState.urlInput,
                    detectedType = uiState.detectedType,
                    isSearching = uiState.isSearchingReddit,
                    results = uiState.redditSearchResults,
                    searchError = uiState.redditSearchError,
                    existingConfigs = uiState.existingRedditConfigs,
                    onInputChange = onUpdateSourceInput,
                    onSearch = onSearchReddit,
                    onAddSource = onAddSourceFromInput,
                    onAddResult = onAddRedditCommunity,
                    onClearResults = onClearSearchResults
                )
            }

            if (uiState.sources.isEmpty()) {
                item("empty_sources") {
                    Text(
                        text = "No sources configured",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                val visibleSources = uiState.sources.filterNot(Source::isLocal)
                items(visibleSources, key = Source::id) { source ->
                    SourceCard(
                        source = source,
                        onOpenSource = onOpenSource,
                        onGoogleDriveClick = onGoogleDriveClick,
                        onGooglePhotosClick = onGooglePhotosClick,
                        onRequestRemove = { pendingRemoval = it }
                    )
                }
            }
        }
    }

    pendingRemoval?.let { source ->
        RemoveSourceDialog(
            source = source,
            onDismiss = { pendingRemoval = null },
            onConfirm = { removeWallpapers ->
                onRemoveSource(source, removeWallpapers)
                pendingRemoval = null
            }
        )
    }
}

@Composable
private fun AddSourceFromUrlCard(
    input: String,
    detectedType: SourceRepository.RemoteSourceType?,
    isSearching: Boolean,
    results: List<RedditCommunity>,
    searchError: String?,
    existingConfigs: Set<String>,
    onInputChange: (String) -> Unit,
    onSearch: () -> Unit,
    onAddSource: () -> Unit,
    onAddResult: (RedditCommunity) -> Unit,
    onClearResults: () -> Unit
) {
    val isReddit = detectedType == SourceRepository.RemoteSourceType.REDDIT
    val canSearch = isReddit && input.trim().length >= 2 && !isSearching
    val canAdd = detectedType != null && input.isNotBlank() && !isSearching
    var showSupportedSources by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (showSupportedSources) {
                SupportedSourcesDialog(onDismiss = { showSupportedSources = false })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add from URL",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showSupportedSources = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Supported sources"
                    )
                }
            }
            Text(
                text = "Paste a subreddit or supported wallpaper link to create a new source.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )

            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                label = { Text("Subreddit or URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (isReddit) {
                        if (canSearch) onSearch()
                    } else if (canAdd) {
                        onAddSource()
                    }
                })
            )

            if (input.isNotBlank() && detectedType == null) {
                Text(
                    text = "Enter a supported link from the sites above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isReddit) {
                    Button(onClick = onSearch, enabled = canSearch) {
                        Text("Search")
                    }
                    TextButton(onClick = onAddSource, enabled = canAdd) {
                        Text("Add directly")
                    }
                    if (results.isNotEmpty()) {
                        TextButton(onClick = onClearResults, enabled = !isSearching) {
                            Text("Clear")
                        }
                    }
                } else {
                    Button(onClick = onAddSource, enabled = canAdd) {
                        Text("Add source")
                    }
                }
            }

            when {
                isSearching -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }

                searchError != null -> {
                    Text(
                        text = searchError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                results.isNotEmpty() -> {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        results.forEach { community ->
                            RedditSearchResult(
                                community = community,
                                alreadyAdded = existingConfigs.contains(community.name.lowercase(Locale.ROOT)),
                                onAdd = onAddResult
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RedditSearchResult(
    community: RedditCommunity,
    alreadyAdded: Boolean,
    onAdd: (RedditCommunity) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(community.displayName, style = MaterialTheme.typography.titleSmall)
            Text(
                text = community.title,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp)
            )
            community.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (alreadyAdded) "Already added" else "Tap add to create a new source",
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = { onAdd(community) }, enabled = !alreadyAdded) {
                    Text(if (alreadyAdded) "Added" else "Add")
                }
            }
        }
    }
}

@Composable
private fun SupportedSourcesDialog(onDismiss: () -> Unit) {
    val sources = listOf(
        SupportedSourceInfo("Reddit", "reddit.com"),
        SupportedSourceInfo("Wallhaven", "wallhaven.cc"),
        SupportedSourceInfo("Danbooru", "danbooru.donmai.us"),
        SupportedSourceInfo("Unsplash", "unsplash.com"),
        SupportedSourceInfo("AlphaCoders (Wallpaper Abyss)", "wall.alphacoders.com"),
        SupportedSourceInfo("Pinterest", "pinterest.com")
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Supported URL sources") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                sources.forEach { source ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = source.faviconUrl,
                            contentDescription = source.label,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = source.label,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
            }
        }
    )
}

private data class SupportedSourceInfo(val label: String, val domain: String) {
    val faviconUrl: String = "https://www.google.com/s2/favicons?sz=128&domain=$domain"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceCard(
    source: Source,
    onOpenSource: (Source) -> Unit,
    onGoogleDriveClick: () -> Unit,
    onGooglePhotosClick: () -> Unit,
    onRequestRemove: (Source) -> Unit
) {
    val isGoogleDrive = source.providerKey == SourceKeys.GOOGLE_DRIVE
    val isGooglePhotos = source.providerKey == SourceKeys.GOOGLE_PHOTOS
    val isGoogleDrivePicker = isGoogleDrive && source.config.isNullOrBlank()
    val isGooglePhotosPicker = isGooglePhotos && source.config.isNullOrBlank()
    val isRemovable = when {
        isGoogleDrivePicker || isGooglePhotosPicker -> false
        isGoogleDrive || isGooglePhotos -> true
        else -> source.providerKey in setOf(
            SourceKeys.REDDIT,
            SourceKeys.PINTEREST,
            SourceKeys.WEBSITES,
            SourceKeys.WALLHAVEN,
            SourceKeys.DANBOORU,
            SourceKeys.UNSPLASH,
            SourceKeys.ALPHA_CODERS
        )
    }

    val cardModifier = when {
        isGoogleDrivePicker -> Modifier
            .fillMaxWidth()
            .clickable { onGoogleDriveClick() }
        isGooglePhotosPicker -> Modifier
            .fillMaxWidth()
            .clickable { onGooglePhotosClick() }
        else -> Modifier
            .fillMaxWidth()
            .clickable { onOpenSource(source) }
    }

    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (source.iconUrl != null) {
                    AsyncImage(
                        model = source.iconUrl,
                        contentDescription = source.title,
                        modifier = Modifier.size(28.dp),
                        placeholder = source.iconRes?.let { painterResource(id = it) },
                        error = source.iconRes?.let { painterResource(id = it) }
                    )
                } else {
                    source.iconRes?.let { iconRes ->
                        Image(
                            painter = painterResource(id = iconRes),
                            contentDescription = source.title,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(Modifier.size(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(source.title, style = MaterialTheme.typography.titleMedium)
                    Text(source.description, style = MaterialTheme.typography.bodyMedium)
                }
                if (isRemovable) {
                    IconButton(onClick = { onRequestRemove(source) }) {
                        Icon(imageVector = Icons.Outlined.Delete, contentDescription = "Remove ${source.title}")
                    }
                }
            }

            if (isGoogleDrivePicker || isGooglePhotosPicker) {
                Spacer(Modifier.size(12.dp))
                val instructions = if (isGoogleDrivePicker) {
                    "Choose Drive folders to browse their wallpapers."
                } else {
                    "Pick Google Photos albums to browse their images."
                }
                Text(instructions, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.size(4.dp))
                val buttonLabel = if (isGoogleDrivePicker) "Select folders" else "Select albums"
                val onClick = if (isGoogleDrivePicker) onGoogleDriveClick else onGooglePhotosClick
                TextButton(onClick = onClick) {
                    Text(buttonLabel)
                }
            }
        }
    }
}

@Composable
private fun RemoveSourceDialog(
    source: Source,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit
) {
    var removeWallpapers by remember(source.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove ${source.title}?") },
        text = {
            Column {
                Text("Do you also want to remove wallpapers saved from this source?")
                Spacer(modifier = Modifier.size(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = removeWallpapers,
                        onCheckedChange = { removeWallpapers = it }
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Also remove wallpapers")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(removeWallpapers) }) {
                Text("Remove")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
