package com.joshiminh.wallbase.feature.sources.ui

import android.annotation.SuppressLint
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
import com.joshiminh.wallbase.ui.components.longPressReorderHandle
import com.joshiminh.wallbase.ui.components.topBarInsetPadding
import com.joshiminh.wallbase.ui.LocalBarsProgress
import com.joshiminh.wallbase.ui.theme.WallBaseShapes
import androidx.hilt.navigation.compose.hiltViewModel
import com.joshiminh.wallbase.ui.theme.WallBaseSpacing
import com.joshiminh.wallbase.ui.viewmodel.ExtensionsViewModel
import com.joshiminh.wallbase.feature.sources.viewmodel.SourcesViewModel
import java.util.Locale

@Composable
fun SourcesRoute(
    uiState: SourcesViewModel.SourcesUiState,
    extensionsViewModel: ExtensionsViewModel = hiltViewModel(),
    onUpdateSourceInput: (String) -> Unit,
    onSearchReddit: () -> Unit,
    onAddSourceFromInput: () -> Unit,
    onAddRedditCommunity: (RedditCommunity) -> Unit,
    onClearSearchResults: () -> Unit,
    onOpenSource: (Source) -> Unit,
    onRemoveSource: (Source, Boolean) -> Unit,
    onMoveSource: ((Int, Int) -> Unit)? = null,
    onReorderSources: ((List<Source>) -> Unit)? = null,
    onMessageShown: () -> Unit,
    onSourceUrlCopied: (String) -> Unit,
    onOpenRepoScreen: () -> Unit,
    onConfigureTopBar: (TopBarState) -> TopBarHandle
) {
    val extensionsState by extensionsViewModel.uiState.collectAsStateWithLifecycle()
    SourcesScreen(
        uiState = uiState,
        extensionsState = extensionsState,
        onInstallExtension = extensionsViewModel::installFromCatalog,
        onUninstallExtension = extensionsViewModel::uninstallExtension,
        onExtensionsMessageShown = extensionsViewModel::consumeMessage,
        onUpdateSourceInput = onUpdateSourceInput,
        onSearchReddit = onSearchReddit,
        onAddSourceFromInput = onAddSourceFromInput,
        onAddRedditCommunity = onAddRedditCommunity,
        onClearSearchResults = onClearSearchResults,
        onOpenSource = onOpenSource,
        onRemoveSource = onRemoveSource,
        onMoveSource = onMoveSource,
        onReorderSources = onReorderSources,
        onMessageShown = onMessageShown,
        onSourceUrlCopied = onSourceUrlCopied,
        onOpenRepoScreen = onOpenRepoScreen,
        onConfigureTopBar = onConfigureTopBar,
    )
}

@Composable
private fun SourcesScreen(
    uiState: SourcesViewModel.SourcesUiState,
    extensionsState: ExtensionsViewModel.ExtensionsUiState,
    onInstallExtension: (ExtensionRepoItem) -> Unit,
    onUninstallExtension: (String) -> Unit,
    onExtensionsMessageShown: () -> Unit,
    onUpdateSourceInput: (String) -> Unit,
    onSearchReddit: () -> Unit,
    onAddSourceFromInput: () -> Unit,
    onAddRedditCommunity: (RedditCommunity) -> Unit,
    onClearSearchResults: () -> Unit,
    onOpenSource: (Source) -> Unit,
    onRemoveSource: (Source, Boolean) -> Unit,
    onMoveSource: ((Int, Int) -> Unit)? = null,
    onReorderSources: ((List<Source>) -> Unit)? = null,
    onMessageShown: () -> Unit,
    onSourceUrlCopied: (String) -> Unit,
    onOpenRepoScreen: () -> Unit,
    onConfigureTopBar: (TopBarState) -> TopBarHandle
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var pendingRemoval by remember { mutableStateOf<Source?>(null) }
    var showRemoveSelectedDialog by remember { mutableStateOf(false) }
    var isMultiSelectMode by rememberSaveable { mutableStateOf(false) }
    var selectedSourceKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingUninstallCatalogItem by remember { mutableStateOf<ExtensionRepoItem?>(null) }
    var showAddSourceModal by remember { mutableStateOf(false) }
    var isAddingSource by remember { mutableStateOf(false) }

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
        isMultiSelectMode,
        selectedSourceKeys,
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
            } else if (isMultiSelectMode) {
                if (selectedSourceKeys.isNotEmpty()) {
                    IconButton(onClick = { showRemoveSelectedDialog = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Remove selected sources")
                    }
                }
                IconButton(onClick = {
                    isMultiSelectMode = false
                    selectedSourceKeys = emptySet()
                }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Cancel selection")
                }
            } else {
                IconButton(onClick = { isSearchActive = true }) {
                    Icon(imageVector = Icons.Outlined.Search, contentDescription = "Search sources")
                }
                if (selectedTab == 0) {
                    IconButton(onClick = { isMultiSelectMode = true }) {
                        Icon(Icons.Outlined.Checklist, contentDescription = "Select sources")
                    }
                }
            }
        }

        val tabBottomContent: @Composable () -> Unit = {
            Surface(color = MaterialTheme.colorScheme.background) {
                PrimaryTabRow(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background),
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    divider = {}
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
        }

        TopBarState(
            title = if (isSearchActive) null else if (isMultiSelectMode) {
                "${selectedSourceKeys.size} selected"
            } else "Sources",
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

    LaunchedEffect(uiState.urlInput, uiState.snackbarMessage, isAddingSource) {
        if (!isAddingSource) return@LaunchedEffect
        when {
            uiState.snackbarMessage?.startsWith("Added ") == true -> {
                isAddingSource = false
                showAddSourceModal = false
            }
            uiState.snackbarMessage != null -> isAddingSource = false
        }
    }

    LaunchedEffect(extensionsState.snackbarMessage) {
        val message = extensionsState.snackbarMessage ?: return@LaunchedEffect
        try {
            snackbarHostState.showSnackbar(message)
        } finally {
            onExtensionsMessageShown()
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
                    isMultiSelectMode = isMultiSelectMode,
                    selectedSourceKeys = selectedSourceKeys,
                    onToggleSelection = { source ->
                        selectedSourceKeys = if (source.key in selectedSourceKeys) {
                            selectedSourceKeys - source.key
                        } else selectedSourceKeys + source.key
                    },
                    onReorderSources = onReorderSources,
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
                    onInstall = onInstallExtension,
                    onUninstall = { item -> pendingUninstallCatalogItem = item },
                    onOpenRepoScreen = onOpenRepoScreen
                )
            }

            // FAB to add custom source
            if (selectedTab == 0 && !isMultiSelectMode) {
                val barsProgress = LocalBarsProgress.current
                val density = LocalDensity.current
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, bottom = 96.dp)
                        .graphicsLayer {
                            translationY = (1f - barsProgress) * with(density) { 80.dp.toPx() }
                        }
                ) {
                    FloatingActionButton(onClick = { showAddSourceModal = true }) {
                        Icon(imageVector = Icons.Outlined.Add, contentDescription = "Add custom source")
                    }
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
                isAddingSource = true
                onAddSourceFromInput()
            },
            onAddResult = { community ->
                isAddingSource = true
                onAddRedditCommunity(community)
            },
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

    if (showRemoveSelectedDialog) {
        val selectedSources = visibleSources.filter { it.key in selectedSourceKeys }
        RemoveSelectedSourcesDialog(
            count = selectedSources.size,
            onDismiss = { showRemoveSelectedDialog = false },
            onConfirm = { removeWallpapers ->
                selectedSources.forEach { onRemoveSource(it, removeWallpapers) }
                selectedSourceKeys = emptySet()
                isMultiSelectMode = false
                showRemoveSelectedDialog = false
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
                        onUninstallExtension(item.id)
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
