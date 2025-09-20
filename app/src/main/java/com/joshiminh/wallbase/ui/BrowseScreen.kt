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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
    onAddLocalWallpapers: () -> Unit,
    onUpdateSourceInput: (String) -> Unit,
    onSearchReddit: () -> Unit,
    onAddSourceFromInput: () -> Unit,
    onAddRedditCommunity: (RedditCommunity) -> Unit,
    onClearSearchResults: () -> Unit,
    onOpenSource: (Source) -> Unit,
    onRemoveSource: (Source) -> Unit,
    onMessageShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onMessageShown()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                items(uiState.sources, key = Source::id) { source ->
                    SourceCard(
                        source = source,
                        onOpenSource = onOpenSource,
                        onGoogleDriveClick = onGoogleDriveClick,
                        onGooglePhotosClick = onGooglePhotosClick,
                        onAddLocalWallpapers = onAddLocalWallpapers,
                        onRemoveSource = onRemoveSource
                    )
                }
            }
        }
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Add from URL",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Paste a subreddit or wallpaper link to create a new source.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )

            Column(modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)) {
                SupportedSourceBullet("Reddit Subs")
                SupportedSourceBullet("Pinterest Boards")
                SupportedSourceBullet("Wallpaper Websites")
            }

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
private fun SupportedSourceBullet(text: String) {
    Text(
        text = "• $text",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 2.dp)
    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceCard(
    source: Source,
    onOpenSource: (Source) -> Unit,
    onGoogleDriveClick: () -> Unit,
    onGooglePhotosClick: () -> Unit,
    onAddLocalWallpapers: () -> Unit,
    onRemoveSource: (Source) -> Unit
) {
    val isGoogleDrive = source.providerKey == SourceKeys.GOOGLE_DRIVE
    val isGooglePhotos = source.providerKey == SourceKeys.GOOGLE_PHOTOS
    val isGoogleDrivePicker = isGoogleDrive && source.config.isNullOrBlank()
    val isGooglePhotosPicker = isGooglePhotos && source.config.isNullOrBlank()
    val isLocal = source.isLocal
    val isRemovable = when {
        isLocal -> false
        isGoogleDrivePicker || isGooglePhotosPicker -> false
        isGoogleDrive || isGooglePhotos -> true
        else -> source.providerKey == SourceKeys.REDDIT ||
            source.providerKey == SourceKeys.WEBSITES ||
            source.providerKey == SourceKeys.PINTEREST
    }

    val cardModifier = when {
        isGoogleDrivePicker -> Modifier
            .fillMaxWidth()
            .clickable { onGoogleDriveClick() }
        isGooglePhotosPicker -> Modifier
            .fillMaxWidth()
            .clickable { onGooglePhotosClick() }
        !isLocal -> Modifier
            .fillMaxWidth()
            .clickable { onOpenSource(source) }
        else -> Modifier.fillMaxWidth()
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
                    IconButton(onClick = { onRemoveSource(source) }) {
                        Icon(imageVector = Icons.Outlined.Delete, contentDescription = "Remove ${source.title}")
                    }
                }
            }
            if (isLocal) {
                Spacer(Modifier.size(12.dp))
                Text(
                    text = "Import local images into your library.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.size(4.dp))
                TextButton(onClick = onAddLocalWallpapers) {
                    Text("Add from device")
                }
            } else if (isGoogleDrivePicker || isGooglePhotosPicker) {
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
