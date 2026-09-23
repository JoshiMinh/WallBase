package com.joshiminh.wallbase.screens

import com.joshiminh.wallbase.navigation.*
import com.joshiminh.wallbase.ui.components.topBarInsetPadding
import com.joshiminh.wallbase.ui.components.bottomBarInsetPadding
import android.annotation.SuppressLint
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.joshiminh.wallbase.data.entity.Source
import com.joshiminh.wallbase.data.entity.SourceKeys
import com.joshiminh.wallbase.data.repository.SourceRepository
import com.joshiminh.wallbase.sources.RedditCommunity
import com.joshiminh.wallbase.ui.viewmodel.SourcesViewModel
import com.joshiminh.wallbase.ui.theme.WallBaseShapes
import com.joshiminh.wallbase.ui.theme.WallBaseSpacing
import java.util.Locale

@Composable
fun BrowseScreen(
    uiState: SourcesViewModel.SourcesUiState,
    onUpdateSourceInput: (String) -> Unit,
    onSearchReddit: () -> Unit,
    onAddSourceFromInput: () -> Unit,
    onAddRedditCommunity: (RedditCommunity) -> Unit,
    onClearSearchResults: () -> Unit,
    onOpenSource: (Source) -> Unit,
    onRemoveSource: (Source, Boolean) -> Unit,
    onMessageShown: () -> Unit,
    onSourceUrlCopied: (String) -> Unit,
    onOpenExtensions: () -> Unit = {},
    onConfigureTopBar: (TopBarState) -> TopBarHandle
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingRemoval by remember { mutableStateOf<Source?>(null) }
    var showAddSourceModal by remember { mutableStateOf(false) }

    // Configure TopBar with Extensions & Add Source button
    val topBarState = TopBarState(
        title = "Browse",
        actions = {
            IconButton(onClick = onOpenExtensions) {
                Icon(
                    imageVector = Icons.Outlined.Extension,
                    contentDescription = "Extensions & Community Sources"
                )
            }
            IconButton(onClick = { showAddSourceModal = true }) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Add source"
                )
            }
        }
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
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = WallBaseSpacing.md,
                top = topBarInsetPadding(WallBaseSpacing.md, hasTabBar = false),
                end = WallBaseSpacing.md,
                bottom = bottomBarInsetPadding(WallBaseSpacing.md, hasBottomNav = true)
            ),
            verticalArrangement = Arrangement.spacedBy(WallBaseSpacing.sm)
        ) {
            if (uiState.sources.isEmpty()) {
                item("empty_sources") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(WallBaseSpacing.lg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No sources configured. Add a subreddit or wallpaper source to begin.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val visibleSources = uiState.sources.filterNot(Source::isLocal)
                items(visibleSources, key = Source::id) { source ->
                    SourceCard(
                        source = source,
                        onOpenSource = onOpenSource,
                        onRequestRemove = { pendingRemoval = it },
                        onSourceUrlCopied = onSourceUrlCopied
                    )
                }
            }
        }
    }

    if (showAddSourceModal) {
        AddSourceBottomSheet(
            input = uiState.urlInput,
            detectedType = uiState.detectedType,
            isSearching = uiState.isSearchingReddit,
            results = uiState.redditSearchResults,
            searchError = uiState.redditSearchError,
            existingConfigs = uiState.existingRedditConfigs,
            onInputChange = onUpdateSourceInput,
            onSearch = onSearchReddit,
            onAddSource = {
                onAddSourceFromInput()
                showAddSourceModal = false
            },
            onAddResult = { community ->
                onAddRedditCommunity(community)
                showAddSourceModal = false
            },
            onQuickAdd = { quickInput ->
                onUpdateSourceInput(quickInput)
                onAddSourceFromInput()
                showAddSourceModal = false
            },
            onClearResults = onClearSearchResults,
            onOpenExtensions = {
                showAddSourceModal = false
                onOpenExtensions()
            },
            onDismiss = { showAddSourceModal = false }
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSourceBottomSheet(
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
    onQuickAdd: (String) -> Unit,
    onClearResults: () -> Unit,
    onOpenExtensions: () -> Unit,
    onDismiss: () -> Unit
) {
    val isReddit = detectedType == SourceRepository.RemoteSourceType.REDDIT
    val canSearch = isReddit && input.trim().length >= 2 && !isSearching
    val canAdd = input.trim().isNotBlank() && !isSearching

    val sheetTitle = when (detectedType) {
        SourceRepository.RemoteSourceType.REDDIT -> "Add Reddit source"
        SourceRepository.RemoteSourceType.WALLHAVEN -> "Add Wallhaven source"
        SourceRepository.RemoteSourceType.PINTEREST -> "Add Pinterest source"
        SourceRepository.RemoteSourceType.EXTENSION -> "Add Extension source"
        SourceRepository.RemoteSourceType.WEBSITE -> "Add Website source"
        null -> "Add source"
    }
    val sheetSubtitle = when (detectedType) {
        SourceRepository.RemoteSourceType.REDDIT -> "Enter a subreddit name (e.g. r/wallpapers) or Reddit link."
        SourceRepository.RemoteSourceType.WALLHAVEN -> "Paste a public Wallhaven search or collection link."
        SourceRepository.RemoteSourceType.PINTEREST -> "Enter a Pinterest board, profile (@username), or URL."
        SourceRepository.RemoteSourceType.EXTENSION -> "Install or enable this community declarative extension."
        SourceRepository.RemoteSourceType.WEBSITE -> "Paste a wallpaper website link."
        null -> "Enter a subreddit name, extension name (e.g. AlphaCoders, Unsplash, Pexels, Pixiv, Safebooru), or paste a wallpaper link."
    }
    val inputLabel = when (detectedType) {
        SourceRepository.RemoteSourceType.REDDIT -> "Subreddit name or URL"
        SourceRepository.RemoteSourceType.WALLHAVEN -> "Wallhaven URL"
        SourceRepository.RemoteSourceType.PINTEREST -> "Pinterest board, @username, or URL"
        SourceRepository.RemoteSourceType.EXTENSION -> "Extension name or ID"
        SourceRepository.RemoteSourceType.WEBSITE -> "Website URL"
        null -> "Subreddit, extension, or wallpaper URL"
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item("title") {
                Text(text = sheetTitle, style = MaterialTheme.typography.titleLarge)
            }
            item("subtitle") {
                Text(
                    text = sheetSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item("input") {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    label = { Text(inputLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (canAdd) {
                            onAddSource()
                        }
                    })
                )
            }
            item("add_action") {
                Button(
                    onClick = onAddSource,
                    enabled = canAdd,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Source")
                }
            }

            item("supported_sources") {
                SupportedSourcesList(
                    onSelectSourceInput = onInputChange,
                    onQuickAdd = onQuickAdd,
                    onOpenExtensions = onOpenExtensions
                )
            }

            if (isReddit) {
                item("reddit_actions") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onSearch, enabled = canSearch) {
                            Text("Search communities")
                        }
                        if (results.isNotEmpty()) {
                            TextButton(onClick = onClearResults, enabled = !isSearching) {
                                Text("Clear")
                            }
                        }
                    }
                }
            }

            when {
                isSearching -> {
                    item("searching") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    }
                }

                searchError != null -> {
                    item("search_error") {
                        Text(
                            text = searchError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                results.isNotEmpty() -> {
                    items(results, key = RedditCommunity::name) { community ->
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupportedSourcesList(
    onSelectSourceInput: (String) -> Unit,
    onQuickAdd: (String) -> Unit,
    onOpenExtensions: () -> Unit
) {
    val sources = listOf(
        SupportedSourceInfo(
            label = "AlphaCoders",
            faviconDomain = "wall.alphacoders.com",
            quickAddInput = "alphacoders",
            requirement = "Declarative Scraper • Anime & Gaming wallpapers"
        ),
        SupportedSourceInfo(
            label = "Unsplash",
            faviconDomain = "unsplash.com",
            quickAddInput = "unsplash",
            requirement = "Declarative API • High-res curated photography"
        ),
        SupportedSourceInfo(
            label = "Pexels",
            faviconDomain = "pexels.com",
            quickAddInput = "pexels",
            requirement = "Declarative API • Free stock wallpapers"
        ),
        SupportedSourceInfo(
            label = "Pixiv",
            faviconDomain = "pixiv.net",
            quickAddInput = "pixiv",
            requirement = "Declarative Scraper • Japanese anime art catalog"
        ),
        SupportedSourceInfo(
            label = "Safebooru",
            faviconDomain = "safebooru.org",
            quickAddInput = "safebooru",
            requirement = "Declarative API • Tagged anime illustrations"
        ),
        SupportedSourceInfo(
            label = "Wallhaven",
            faviconDomain = "wallhaven.cc",
            quickAddInput = "https://wallhaven.cc/toplist",
            requirement = "Declarative API • Toplist & tag searches"
        ),
        SupportedSourceInfo(
            label = "Reddit",
            faviconDomain = "reddit.com",
            quickAddInput = "r/wallpapers",
            requirement = "Subreddits like r/wallpapers, r/AnimeWallpaper"
        ),
        SupportedSourceInfo(
            label = "Pinterest",
            faviconDomain = "pinterest.com",
            quickAddInput = "https://www.pinterest.com/wallpapersden/ultra-hd-wallpapers-collections/",
            requirement = "Boards, profiles (@username), or URLs"
        ),
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Supported sources & extensions",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        sources.forEach { source ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                onClick = { source.quickAddInput?.let(onSelectSourceInput) },
                enabled = source.quickAddInput != null
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (source.faviconUrl != null) {
                        AsyncImage(
                            model = source.faviconUrl,
                            contentDescription = source.label,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Public,
                            contentDescription = source.label,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = source.label, style = MaterialTheme.typography.bodyMedium)
                        source.requirement?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (source.quickAddInput != null) {
                        TextButton(onClick = { onQuickAdd(source.quickAddInput) }) {
                            Text("Add")
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            onClick = onOpenExtensions
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Extension,
                    contentDescription = "Extensions & Custom Repos",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Extensions & Community Repos",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Import custom JSON scrapers or subscribe to repo.json feeds",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
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
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
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

private data class SupportedSourceInfo(
    val label: String,
    val faviconDomain: String? = null,
    val quickAddInput: String? = null,
    val requirement: String? = null,
) {
    val faviconUrl: String? = faviconDomain?.let { domain ->
        "https://www.google.com/s2/favicons?sz=128&domain=$domain"
    }
}

@Composable
private fun SourceCard(
    source: Source,
    onOpenSource: (Source) -> Unit,
    onRequestRemove: (Source) -> Unit,
    onSourceUrlCopied: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val shareUrl = remember(source) { sourceShareUrl(source) }
    val isRemovable = source.providerKey !in setOf(
        SourceKeys.LOCAL
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onOpenSource(source) },
                onLongClick = {
                    shareUrl?.let {
                        clipboardManager.setText(AnnotatedString(it))
                        onSourceUrlCopied(it)
                    }
                }
            ),
        shape = WallBaseShapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(WallBaseSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val iconUrl = source.iconUrl?.takeUnless { it.isBlank() }
                val fallbackPainter = safePainterResource(source.iconRes)
                val defaultPainter = rememberVectorPainter(image = Icons.Outlined.Public)

                when {
                    iconUrl != null -> {
                        AsyncImage(
                            model = iconUrl,
                            contentDescription = source.title,
                            modifier = Modifier.size(36.dp),
                            placeholder = fallbackPainter ?: defaultPainter,
                            error = fallbackPainter ?: defaultPainter
                        )
                    }

                    fallbackPainter != null -> {
                        Image(
                            painter = fallbackPainter,
                            contentDescription = source.title,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    else -> {
                        Icon(
                            imageVector = Icons.Outlined.Public,
                            contentDescription = source.title,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Spacer(Modifier.size(WallBaseSpacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(source.title, style = MaterialTheme.typography.titleMedium)
                    Text(source.description, style = MaterialTheme.typography.bodyMedium)
                }
                if (isRemovable) {
                    IconButton(
                        onClick = { onRequestRemove(source) }
                    ) {
                        Icon(imageVector = Icons.Outlined.Delete, contentDescription = "Remove ${source.title}")
                    }
                }
            }

            shareUrl?.let { url ->
                Spacer(Modifier.size(6.dp))
                Text(
                    text = "Long-press to copy $url",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun sourceShareUrl(source: Source): String? {
    val config = source.config?.takeIf { it.isNotBlank() } ?: return null
    return when (source.providerKey) {
        SourceKeys.REDDIT -> {
            val slug = config.trim('/').ifBlank { return null }
            "https://www.reddit.com/r/$slug/"
        }

        SourceKeys.PINTEREST,
        SourceKeys.WALLHAVEN,
        SourceKeys.WEBSITES -> config

        else -> null
    }
}

@SuppressLint("LocalContextResourcesRead")
@Composable
fun safePainterResource(@DrawableRes resId: Int?): Painter? {
    if (resId == null || resId == 0) return null
    val context = androidx.compose.ui.platform.LocalContext.current
    val isValid = remember(resId, context) {
        runCatching { context.resources.getResourceName(resId) }.isSuccess
    }
    return if (isValid) painterResource(resId) else null
}

@Composable
private fun RemoveSourceDialog(
    source: Source,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit
) {
    var removeWallpapers by remember(source.id) { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove ${source.title}?") },
        text = {
            Column {
                Text("Do you also want to remove wallpapers saved from this source?")
                Spacer(modifier = Modifier.size(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = removeWallpapers,
                        onCheckedChange = { removeWallpapers = it }
                    )
                    Spacer(modifier = Modifier.size(6.dp))
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
