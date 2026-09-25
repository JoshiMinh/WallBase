package com.joshiminh.wallbase.screens

import android.annotation.SuppressLint
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.joshiminh.wallbase.ui.components.TopBarSearchField
import com.joshiminh.wallbase.ui.components.bottomBarInsetPadding
import com.joshiminh.wallbase.ui.components.topBarInsetPadding
import com.joshiminh.wallbase.ui.theme.WallBaseShapes
import androidx.hilt.navigation.compose.hiltViewModel
import com.joshiminh.wallbase.ui.theme.WallBaseSpacing
import com.joshiminh.wallbase.ui.viewmodel.ExtensionsViewModel
import com.joshiminh.wallbase.ui.viewmodel.SourcesViewModel
import java.util.Locale

@Composable
fun SourcesScreen(
    uiState: SourcesViewModel.SourcesUiState,
    extensionsViewModel: ExtensionsViewModel = hiltViewModel(),
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
    var pendingUninstallCatalogItem by remember { mutableStateOf<ExtensionRepoItem?>(null) }
    var showAddSourceModal by remember { mutableStateOf(false) }

    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }

    val visibleSources = remember(uiState.sources) {
        uiState.sources.filterNot(Source::isLocal)
    }

    val installedExtensionIds = remember(visibleSources, extensionsState.installedExtensions) {
        val fromSources = visibleSources.mapNotNull {
            if (it.providerKey == SourceKeys.EXTENSION) it.config ?: it.key.removePrefix("${SourceKeys.EXTENSION}:")
            else it.providerKey
        }
        val fromManifests = extensionsState.installedExtensions.map { it.id }
        (fromSources + fromManifests).toSet()
    }

    val trimmedQuery = remember(searchQuery) { searchQuery.trim().lowercase(Locale.ROOT) }

    val filteredInstalledSources = remember(visibleSources, trimmedQuery, isSearchActive) {
        if (!isSearchActive || trimmedQuery.isBlank()) {
            visibleSources
        } else {
            visibleSources.filter { source ->
                source.title.lowercase(Locale.ROOT).contains(trimmedQuery) ||
                        source.providerKey.lowercase(Locale.ROOT).contains(trimmedQuery) ||
                        (source.config?.lowercase(Locale.ROOT)?.contains(trimmedQuery) == true)
            }
        }
    }

    val filteredCatalog = remember(extensionsState.communityCatalog, trimmedQuery, isSearchActive) {
        if (!isSearchActive || trimmedQuery.isBlank()) {
            extensionsState.communityCatalog
        } else {
            extensionsState.communityCatalog.filter { item ->
                item.name.lowercase(Locale.ROOT).contains(trimmedQuery) ||
                        (item.description?.lowercase(Locale.ROOT)?.contains(trimmedQuery) == true) ||
                        (item.author?.lowercase(Locale.ROOT)?.contains(trimmedQuery) == true)
            }
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

    // Configure TopBar with Search, Add Source, and Repositories puzzle icon
    val topBarState = remember(
        selectedTab,
        isSearchActive,
        searchQuery,
        visibleSources.size,
        extensionsState.communityCatalog.size
    ) {
        val actions: @Composable RowScope.() -> Unit = {
            if (isSearchActive) {
                IconButton(onClick = {
                    isSearchActive = false
                    searchQuery = ""
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }) {
                    Icon(imageVector = Icons.Outlined.Close, contentDescription = "Close search")
                }
                IconButton(onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }) {
                    Icon(imageVector = Icons.Outlined.Search, contentDescription = "Search")
                }
            } else {
                IconButton(onClick = { isSearchActive = true }) {
                    Icon(imageVector = Icons.Outlined.Search, contentDescription = "Search sources")
                }
            }
            IconButton(onClick = { showAddSourceModal = true }) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Add custom source"
                )
            }
            IconButton(onClick = onOpenRepoScreen) {
                Icon(
                    imageVector = Icons.Outlined.Extension,
                    contentDescription = "Repositories"
                )
            }
        }

        val tabBottomContent: @Composable () -> Unit = {
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Installed · ${visibleSources.size}",
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
                            text = "Available · ${extensionsState.communityCatalog.size}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        TopBarState(
            title = if (isSearchActive) null else "Sources",
            actions = actions,
            titleContent = if (isSearchActive) {
                {
                    TopBarSearchField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        onClear = { searchQuery = "" },
                        placeholder = if (selectedTab == 0) "Search installed sources" else "Search available sources",
                        focusRequester = searchFocusRequester,
                        showClearButton = false
                    )
                }
            } else null,
            bottomContent = tabBottomContent,
            autoHideBars = false
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
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 80.dp)
            )
        },
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> InstalledTabContent(
                    sources = filteredInstalledSources,
                    isSearching = isSearchActive && searchQuery.isNotBlank(),
                    searchQuery = searchQuery,
                    onOpenSource = onOpenSource,
                    onRequestRemove = { pendingRemoval = it },
                    onSourceUrlCopied = onSourceUrlCopied,
                    onGoToAvailable = { selectedTab = 1 },
                    onAddSourceClick = { showAddSourceModal = true }
                )

                1 -> AvailableTabContent(
                    catalog = filteredCatalog,
                    isSearching = isSearchActive && searchQuery.isNotBlank(),
                    searchQuery = searchQuery,
                    installedIds = installedExtensionIds,
                    installingIds = extensionsState.installingItemIds,
                    isLoading = extensionsState.isLoading,
                    onInstall = extensionsViewModel::installFromCatalog,
                    onUninstall = { item -> pendingUninstallCatalogItem = item },
                    onOpenRepoScreen = onOpenRepoScreen
                )
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

    pendingUninstallCatalogItem?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingUninstallCatalogItem = null },
            title = { Text("Uninstall ${item.name}?") },
            text = { Text("This will remove ${item.name} from your installed sources.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        extensionsViewModel.uninstallExtension(item.id)
                        pendingUninstallCatalogItem = null
                    }
                ) {
                    Text("Uninstall", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUninstallCatalogItem = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun InstalledTabContent(
    sources: List<Source>,
    isSearching: Boolean,
    searchQuery: String,
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
                .padding(top = topBarInsetPadding(8.dp, hasTabBar = true), bottom = bottomBarInsetPadding(16.dp, hasBottomNav = true))
                .padding(horizontal = WallBaseSpacing.lg),
            contentAlignment = Alignment.Center
        ) {
            if (isSearching) {
                Text(
                    text = "No installed sources match \"$searchQuery\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            } else {
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
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
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
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = WallBaseSpacing.md,
            top = topBarInsetPadding(8.dp, hasTabBar = true),
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
    isSearching: Boolean,
    searchQuery: String,
    installedIds: Set<String>,
    installingIds: Set<String>,
    isLoading: Boolean,
    onInstall: (ExtensionRepoItem) -> Unit,
    onUninstall: (ExtensionRepoItem) -> Unit,
    onOpenRepoScreen: () -> Unit
) {
    if (catalog.isEmpty() && !isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topBarInsetPadding(8.dp, hasTabBar = true), bottom = bottomBarInsetPadding(16.dp, hasBottomNav = true))
                .padding(horizontal = WallBaseSpacing.lg),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(WallBaseSpacing.sm)
            ) {
                Text(
                    text = if (isSearching) "No sources match \"$searchQuery\"" else "No community sources found in repositories.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                FilledTonalButton(
                    onClick = onOpenRepoScreen,
                    shape = WallBaseShapes.pill
                ) {
                    Icon(Icons.Outlined.Extension, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Manage Repositories")
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = WallBaseSpacing.md,
            top = topBarInsetPadding(8.dp, hasTabBar = true),
            end = WallBaseSpacing.md,
            bottom = bottomBarInsetPadding(WallBaseSpacing.md, hasBottomNav = true)
        ),
        verticalArrangement = Arrangement.spacedBy(WallBaseSpacing.sm)
    ) {
        if (isLoading) {
            item("catalog_loading") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(WallBaseSpacing.md),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }

        items(catalog, key = { it.id }) { item ->
            val isInstalled = item.id in installedIds ||
                    installedIds.any { it.equals(item.id, ignoreCase = true) }
            val isInstalling = item.id in installingIds

            AvailableSourceCard(
                item = item,
                isInstalled = isInstalled,
                isInstalling = isInstalling,
                onInstall = { onInstall(item) },
                onUninstall = { onUninstall(item) }
            )
        }
    }
}

@Composable
private fun AvailableSourceCard(
    item: ExtensionRepoItem,
    isInstalled: Boolean,
    isInstalling: Boolean = false,
    onInstall: () -> Unit,
    onUninstall: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(WallBaseShapes.card)
            .clickable(enabled = !isInstalling) {
                if (isInstalled) onUninstall() else onInstall()
            },
        shape = WallBaseShapes.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WallBaseSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WallBaseSpacing.sm)
        ) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!item.iconUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.iconUrl,
                        contentDescription = item.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val authorText = item.author?.takeIf { it.isNotBlank() }?.let { "by $it" }
                val metaText = listOfNotNull(authorText, "v${item.version}").joinToString(" · ")
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val displayUrl = extensionDisplayUrl(item)
                Text(
                    text = displayUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            IconButton(
                onClick = if (isInstalled) onUninstall else onInstall,
                enabled = !isInstalling
            ) {
                if (isInstalling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Icon(
                        imageVector = if (isInstalled) Icons.Outlined.Check else Icons.Outlined.Download,
                        contentDescription = if (isInstalled) "Uninstall extension" else "Install extension",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
    val shareUrl = sourceShareUrl(source)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onOpenSource(source) },
                onLongClick = {
                    shareUrl?.let { url ->
                        clipboardManager.setText(AnnotatedString(url))
                        onSourceUrlCopied(url)
                    }
                }
            ),
        shape = WallBaseShapes.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WallBaseSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WallBaseSpacing.sm)
        ) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!source.iconUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = source.iconUrl,
                        contentDescription = source.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else if (source.iconRes != null && source.iconRes != 0) {
                    val painter = safePainterResource(source.iconRes)
                    if (painter != null) {
                        Image(
                            painter = painter,
                            contentDescription = source.title,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val displayUrl = sourceDisplayUrl(source)
                Text(
                    text = displayUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (shareUrl != null) {
                IconButton(onClick = {
                    clipboardManager.setText(AnnotatedString(shareUrl))
                    onSourceUrlCopied(shareUrl)
                }) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy source link",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = { onRequestRemove(source) }) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Remove source",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun sourceDisplayUrl(source: Source): String {
    val config = source.config?.trim()
    if (!config.isNullOrBlank()) {
        if (config.startsWith("http://") || config.startsWith("https://")) {
            return config
        }
        if (source.providerKey == SourceKeys.REDDIT) {
            val slug = config.trim('/')
            return if (slug.isNotBlank()) "https://reddit.com/r/$slug" else "https://reddit.com"
        }
        return when (config.lowercase(Locale.ROOT)) {
            "alphacoders" -> "https://wall.alphacoders.com"
            "pexels" -> "https://pexels.com"
            "pinterest" -> "https://pinterest.com"
            "pixiv" -> "https://pixiv.net"
            "safebooru" -> "https://safebooru.org"
            "unsplash" -> "https://unsplash.com"
            "wallhaven" -> "https://wallhaven.cc"
            "reddit" -> "https://reddit.com"
            else -> if (config.contains(".")) "https://$config" else "https://$config.com"
        }
    }
    return when (source.providerKey) {
        SourceKeys.REDDIT -> "https://reddit.com"
        SourceKeys.WALLHAVEN -> "https://wallhaven.cc"
        SourceKeys.PINTEREST -> "https://pinterest.com"
        else -> "https://${source.providerKey}.com"
    }
}

private fun extensionDisplayUrl(item: ExtensionRepoItem): String {
    val key = item.id.lowercase(Locale.ROOT)
    return when {
        key.contains("alphacoders") -> "https://wall.alphacoders.com"
        key.contains("pexels") -> "https://pexels.com"
        key.contains("pinterest") -> "https://pinterest.com"
        key.contains("pixiv") -> "https://pixiv.net"
        key.contains("safebooru") -> "https://safebooru.org"
        key.contains("unsplash") -> "https://unsplash.com"
        key.contains("wallhaven") -> "https://wallhaven.cc"
        key.contains("reddit") -> "https://reddit.com"
        else -> "https://$key.com"
    }
}

private fun providerLabel(providerKey: String): String = when (providerKey) {
    SourceKeys.REDDIT -> "Reddit"
    SourceKeys.PINTEREST -> "Pinterest"
    SourceKeys.WALLHAVEN -> "Wallhaven"
    SourceKeys.WEBSITES -> "Website"
    SourceKeys.EXTENSION -> "Extension"
    else -> providerKey.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Add Custom Source",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                label = { Text("URL, Subreddit, or Keywords") },
                placeholder = { Text("e.g. wallpapers, r/wallpapers, or URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                shape = WallBaseShapes.control,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAddSource,
                    enabled = input.isNotBlank(),
                    shape = WallBaseShapes.pill,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Directly")
                }

                FilledTonalButton(
                    onClick = onSearch,
                    enabled = input.isNotBlank() && !isSearching,
                    shape = WallBaseShapes.pill,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Search Reddit")
                    }
                }
            }

            if (results.isNotEmpty()) {
                Text(
                    text = "Reddit Communities Found",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(results, key = { it.name }) { community ->
                        val alreadyAdded = existingConfigs.contains(community.name.lowercase(Locale.ROOT))
                        RedditSearchResult(
                            community = community,
                            alreadyAdded = alreadyAdded,
                            onAdd = onAddResult
                        )
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
                        imageVector = Icons.Outlined.Extension,
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
                            text = "Manage subscribed repo.json URLs to discover more sources",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
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

private fun sourceShareUrl(source: Source): String? {
    val config = source.config?.takeIf { it.isNotBlank() }
    if (config != null) {
        if (config.startsWith("http://") || config.startsWith("https://")) {
            return config
        }
        if (source.providerKey == SourceKeys.REDDIT) {
            val slug = config.trim('/').ifBlank { return null }
            return "https://www.reddit.com/r/$slug/"
        }
        return when (config.lowercase(Locale.ROOT)) {
            "alphacoders" -> "https://wall.alphacoders.com"
            "pexels" -> "https://pexels.com"
            "pinterest" -> "https://pinterest.com"
            "pixiv" -> "https://pixiv.net"
            "safebooru" -> "https://safebooru.org"
            "unsplash" -> "https://unsplash.com"
            "wallhaven" -> "https://wallhaven.cc"
            "reddit" -> "https://reddit.com"
            else -> if (config.contains(".")) "https://$config" else "https://$config.com"
        }
    }
    return when (source.providerKey) {
        SourceKeys.REDDIT -> "https://www.reddit.com"
        SourceKeys.PINTEREST -> "https://www.pinterest.com"
        SourceKeys.WALLHAVEN -> "https://wallhaven.cc"
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
