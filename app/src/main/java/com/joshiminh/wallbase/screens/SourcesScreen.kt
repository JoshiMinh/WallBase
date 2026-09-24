package com.joshiminh.wallbase.screens

import android.annotation.SuppressLint
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.joshiminh.wallbase.data.entity.Source
import com.joshiminh.wallbase.data.entity.SourceKeys
import com.joshiminh.wallbase.data.repository.SourceRepository
import com.joshiminh.wallbase.navigation.TopBarHandle
import com.joshiminh.wallbase.navigation.TopBarState
import com.joshiminh.wallbase.scraper.model.ExtensionRepoItem
import com.joshiminh.wallbase.sources.RedditCommunity
import com.joshiminh.wallbase.ui.components.bottomBarInsetPadding
import com.joshiminh.wallbase.ui.components.topBarInsetPadding
import com.joshiminh.wallbase.ui.theme.WallBaseShapes
import com.joshiminh.wallbase.ui.theme.WallBaseSpacing
import com.joshiminh.wallbase.ui.viewmodel.ExtensionsViewModel
import com.joshiminh.wallbase.ui.viewmodel.SourcesViewModel
import java.util.Locale

@Composable
fun SourcesScreen(
    uiState: SourcesViewModel.SourcesUiState,
    extensionsViewModel: ExtensionsViewModel = viewModel(factory = ExtensionsViewModel.Factory),
    onUpdateSourceInput: (String) -> Unit,
    onSearchReddit: () -> Unit,
    onAddSourceFromInput: () -> Unit,
    onAddRedditCommunity: (RedditCommunity) -> Unit,
    onClearSearchResults: () -> Unit,
    onOpenSource: (Source) -> Unit,
    onRemoveSource: (Source, Boolean) -> Unit,
    onMessageShown: () -> Unit,
    onSourceUrlCopied: (String) -> Unit,
    onOpenRepoScreen: () -> Unit,
    onConfigureTopBar: (TopBarState) -> TopBarHandle
) {
    val extensionsState by extensionsViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var pendingRemoval by remember { mutableStateOf<Source?>(null) }
    var showAddSourceModal by remember { mutableStateOf(false) }
    var catalogSearchQuery by rememberSaveable { mutableStateOf("") }

    val visibleSources = remember(uiState.sources) {
        uiState.sources.filterNot(Source::isLocal)
    }

    val installedExtensionIds = remember(visibleSources, extensionsState.installedExtensions) {
        val fromSources = visibleSources.mapNotNull {
            if (it.providerKey == SourceKeys.EXTENSION) it.config ?: it.key.removePrefix("${SourceKeys.EXTENSION}:")
            else it.providerKey
        }.toSet()
        val fromManifests = extensionsState.installedExtensions.map { it.id }.toSet()
        fromSources + fromManifests
    }

    val filteredCatalog = remember(extensionsState.communityCatalog, catalogSearchQuery) {
        if (catalogSearchQuery.isBlank()) {
            extensionsState.communityCatalog
        } else {
            val q = catalogSearchQuery.trim().lowercase(Locale.ROOT)
            extensionsState.communityCatalog.filter {
                it.name.lowercase(Locale.ROOT).contains(q) ||
                        (it.description?.lowercase(Locale.ROOT)?.contains(q) == true) ||
                        (it.author?.lowercase(Locale.ROOT)?.contains(q) == true)
            }
        }
    }

    // Configure TopBar with Repositories & Add Source button
    val topBarState = remember(selectedTab) {
        TopBarState(
            title = "Sources",
            actions = {
                IconButton(onClick = onOpenRepoScreen) {
                    Icon(
                        imageVector = Icons.Outlined.Storage,
                        contentDescription = "Manage Repositories"
                    )
                }
                IconButton(onClick = { showAddSourceModal = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Add custom source"
                    )
                }
            }
        )
    }

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

    LaunchedEffect(extensionsState.snackbarMessage) {
        val message = extensionsState.snackbarMessage ?: return@LaunchedEffect
        try {
            snackbarHostState.showSnackbar(message)
        } finally {
            extensionsViewModel.consumeMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            // 2-tab navigation: Installed & Available
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topBarInsetPadding(0.dp, hasTabBar = false))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Installed (${visibleSources.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "Available (${extensionsState.communityCatalog.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (selectedTab) {
                    0 -> InstalledTabContent(
                        sources = visibleSources,
                        onOpenSource = onOpenSource,
                        onRequestRemove = { pendingRemoval = it },
                        onSourceUrlCopied = onSourceUrlCopied,
                        onGoToAvailable = { selectedTab = 1 },
                        onAddSourceClick = { showAddSourceModal = true }
                    )

                    1 -> AvailableTabContent(
                        catalog = filteredCatalog,
                        searchQuery = catalogSearchQuery,
                        onSearchQueryChange = { catalogSearchQuery = it },
                        installedIds = installedExtensionIds,
                        isLoading = extensionsState.isLoading,
                        onInstall = extensionsViewModel::installFromCatalog,
                        onOpenRepoScreen = onOpenRepoScreen,
                        onRefresh = extensionsViewModel::refresh
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
            onOpenRepoScreen = {
                showAddSourceModal = false
                onOpenRepoScreen()
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

@Composable
private fun InstalledTabContent(
    sources: List<Source>,
    onOpenSource: (Source) -> Unit,
    onRequestRemove: (Source) -> Unit,
    onSourceUrlCopied: (String) -> Unit,
    onGoToAvailable: () -> Unit,
    onAddSourceClick: () -> Unit
) {
    if (sources.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(WallBaseSpacing.lg),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(WallBaseSpacing.md)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Source,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxSize()
                    )
                }
                Text(
                    text = "No sources installed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Browse available community sources or add custom feeds like subreddits.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Row(horizontalArrangement = Arrangement.spacedBy(WallBaseSpacing.sm)) {
                    Button(
                        onClick = onGoToAvailable,
                        shape = WallBaseShapes.pill
                    ) {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Browse Available")
                    }
                    OutlinedButton(
                        onClick = onAddSourceClick,
                        shape = WallBaseShapes.pill
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Custom")
                    }
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = WallBaseSpacing.md,
            top = WallBaseSpacing.md,
            end = WallBaseSpacing.md,
            bottom = bottomBarInsetPadding(WallBaseSpacing.md, hasBottomNav = true)
        ),
        verticalArrangement = Arrangement.spacedBy(WallBaseSpacing.sm)
    ) {
        items(sources, key = Source::id) { source ->
            SourceCard(
                source = source,
                onOpenSource = onOpenSource,
                onRequestRemove = onRequestRemove,
                onSourceUrlCopied = onSourceUrlCopied
            )
        }
    }
}

@Composable
private fun AvailableTabContent(
    catalog: List<ExtensionRepoItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    installedIds: Set<String>,
    isLoading: Boolean,
    onInstall: (ExtensionRepoItem) -> Unit,
    onOpenRepoScreen: () -> Unit,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = WallBaseSpacing.md,
            top = WallBaseSpacing.md,
            end = WallBaseSpacing.md,
            bottom = bottomBarInsetPadding(WallBaseSpacing.md, hasBottomNav = true)
        ),
        verticalArrangement = Arrangement.spacedBy(WallBaseSpacing.sm)
    ) {
        item("search_bar") {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search available sources...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh catalog")
                        }
                    }
                },
                singleLine = true,
                shape = WallBaseShapes.pill,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            )
        }

        if (catalog.isEmpty() && !isLoading) {
            item("empty_catalog") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(WallBaseSpacing.lg),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(WallBaseSpacing.sm)
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "No community sources found in repositories." else "No sources match \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        FilledTonalButton(
                            onClick = onOpenRepoScreen,
                            shape = WallBaseShapes.pill
                        ) {
                            Icon(Icons.Outlined.Storage, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Manage Repositories")
                        }
                    }
                }
            }
        } else {
            items(catalog, key = { it.id }) { item ->
                val isInstalled = item.id in installedIds ||
                        installedIds.any { it.equals(item.id, ignoreCase = true) }

                AvailableSourceCard(
                    item = item,
                    isInstalled = isInstalled,
                    onInstall = { onInstall(item) }
                )
            }
        }
    }
}

@Composable
private fun AvailableSourceCard(
    item: ExtensionRepoItem,
    isInstalled: Boolean,
    onInstall: () -> Unit
) {
    Card(
        shape = WallBaseShapes.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WallBaseSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WallBaseSpacing.md)
        ) {
            if (!item.iconUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.iconUrl,
                    contentDescription = item.name,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(WallBaseShapes.control)
                )
            } else {
                Surface(
                    shape = WallBaseShapes.control,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .padding(10.dp)
                            .fillMaxSize()
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(WallBaseSpacing.xs)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = WallBaseShapes.pill,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "v${item.version}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                item.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "by ${item.author ?: "WallBase Community"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (isInstalled) {
                OutlinedButton(
                    onClick = onInstall,
                    shape = WallBaseShapes.pill,
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Installed", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                Button(
                    onClick = onInstall,
                    shape = WallBaseShapes.pill,
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Install", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
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
    val isRemovable = source.providerKey !in setOf(SourceKeys.LOCAL)

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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(WallBaseSpacing.md)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WallBaseSpacing.sm)
            ) {
                val iconUrl = source.iconUrl?.takeUnless { it.isBlank() }
                val fallbackPainter = safePainterResource(source.iconRes)
                val defaultPainter = rememberVectorPainter(image = Icons.Outlined.Public)

                when {
                    iconUrl != null -> {
                        AsyncImage(
                            model = iconUrl,
                            contentDescription = source.title,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(WallBaseShapes.control),
                            placeholder = fallbackPainter ?: defaultPainter,
                            error = fallbackPainter ?: defaultPainter
                        )
                    }

                    fallbackPainter != null -> {
                        Image(
                            painter = fallbackPainter,
                            contentDescription = source.title,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(WallBaseShapes.control)
                        )
                    }

                    else -> {
                        Surface(
                            shape = WallBaseShapes.control,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Public,
                                contentDescription = source.title,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .fillMaxSize()
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = source.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = source.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isRemovable) {
                    IconButton(onClick = { onRequestRemove(source) }) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Remove ${source.title}",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            shareUrl?.let { url ->
                Spacer(Modifier.size(4.dp))
                Text(
                    text = "Long-press to copy link",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
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
    onOpenRepoScreen: () -> Unit,
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
        null -> "Add custom source"
    }
    val sheetSubtitle = when (detectedType) {
        SourceRepository.RemoteSourceType.REDDIT -> "Enter a subreddit name (e.g. r/wallpapers) or Reddit link."
        SourceRepository.RemoteSourceType.WALLHAVEN -> "Paste a public Wallhaven search or collection link."
        SourceRepository.RemoteSourceType.PINTEREST -> "Enter a Pinterest board, profile (@username), or URL."
        SourceRepository.RemoteSourceType.EXTENSION -> "Install or enable this community declarative extension."
        SourceRepository.RemoteSourceType.WEBSITE -> "Paste a wallpaper website link."
        null -> "Enter a subreddit name, wallpaper URL, or browse extension repositories."
    }
    val inputLabel = when (detectedType) {
        SourceRepository.RemoteSourceType.REDDIT -> "Subreddit name or URL"
        SourceRepository.RemoteSourceType.WALLHAVEN -> "Wallhaven URL"
        SourceRepository.RemoteSourceType.PINTEREST -> "Pinterest board, @username, or URL"
        SourceRepository.RemoteSourceType.EXTENSION -> "Extension name or ID"
        SourceRepository.RemoteSourceType.WEBSITE -> "Website URL"
        null -> "Subreddit, URL, or provider name"
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item("title") {
                Text(text = sheetTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
                    shape = WallBaseShapes.pill,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Source")
                }
            }

            item("supported_sources") {
                SupportedSourcesList(
                    onSelectSourceInput = onInputChange,
                    onQuickAdd = onQuickAdd,
                    onOpenRepoScreen = onOpenRepoScreen
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

@Composable
private fun SupportedSourcesList(
    onSelectSourceInput: (String) -> Unit,
    onQuickAdd: (String) -> Unit,
    onOpenRepoScreen: () -> Unit
) {
    val sources = listOf(
        SupportedSourceInfo(
            label = "AlphaCoders",
            faviconDomain = "wall.alphacoders.com",
            quickAddInput = "alphacoders",
            requirement = "Anime & Gaming wallpapers"
        ),
        SupportedSourceInfo(
            label = "Unsplash",
            faviconDomain = "unsplash.com",
            quickAddInput = "unsplash",
            requirement = "High-res curated photography"
        ),
        SupportedSourceInfo(
            label = "Pexels",
            faviconDomain = "pexels.com",
            quickAddInput = "pexels",
            requirement = "Free 4K stock wallpapers"
        ),
        SupportedSourceInfo(
            label = "Pixiv",
            faviconDomain = "pixiv.net",
            quickAddInput = "pixiv",
            requirement = "Japanese anime illustration catalog"
        ),
        SupportedSourceInfo(
            label = "Safebooru",
            faviconDomain = "safebooru.org",
            quickAddInput = "safebooru",
            requirement = "Tagged anime art & illustrations"
        ),
        SupportedSourceInfo(
            label = "Wallhaven",
            faviconDomain = "wallhaven.cc",
            quickAddInput = "https://wallhaven.cc/toplist",
            requirement = "Toplist & tag searches"
        ),
        SupportedSourceInfo(
            label = "Reddit",
            faviconDomain = "reddit.com",
            quickAddInput = "r/wallpapers",
            requirement = "Subreddits like r/wallpapers, r/wallpaper"
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
            text = "Quick add presets",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        sources.forEach { source ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = WallBaseShapes.control,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
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
                            modifier = Modifier
                                .size(24.dp)
                                .clip(WallBaseShapes.control)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Public,
                            contentDescription = source.label,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = source.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
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
            shape = WallBaseShapes.control,
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            onClick = onOpenRepoScreen
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Storage,
                    contentDescription = "Manage Repositories",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Extension Repositories",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Manage subscribed repo.json URLs & import custom JSON scrapers",
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
        shape = WallBaseShapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(community.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                text = community.title,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp)
            )
            community.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
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
    val context = LocalContext.current
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

    AlertDialog(
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
