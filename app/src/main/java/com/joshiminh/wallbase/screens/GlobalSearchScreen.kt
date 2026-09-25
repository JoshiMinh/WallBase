package com.joshiminh.wallbase.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.joshiminh.wallbase.data.entity.WallpaperItem
import com.joshiminh.wallbase.data.repository.WallpaperLayout
import com.joshiminh.wallbase.navigation.TopBarHandle
import com.joshiminh.wallbase.navigation.TopBarState
import com.joshiminh.wallbase.ui.components.SheetTab
import com.joshiminh.wallbase.ui.components.TopBarSearchField
import com.joshiminh.wallbase.ui.components.ViewFilterSortBottomSheet
import com.joshiminh.wallbase.ui.components.WallpaperGrid
import com.joshiminh.wallbase.ui.components.bottomBarInsetPadding
import com.joshiminh.wallbase.ui.components.topBarInsetPadding
import com.joshiminh.wallbase.ui.viewmodel.GlobalSearchViewModel

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    onWallpaperSelected: (WallpaperItem, Boolean, List<WallpaperItem>) -> Unit,
    onConfigureTopBar: (TopBarState) -> TopBarHandle,
    viewModel: GlobalSearchViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var showSortSheet by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        viewModel.updateSearchQuery("")
        focusManager.clearFocus()
        keyboardController?.hide()
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

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.consumeError()
        }
    }

    val currentSelectedSource = remember(uiState.sources, uiState.selectedSourceKey) {
        uiState.sources.find { it.key == uiState.selectedSourceKey }
    }

    val topBarHandleState = remember { mutableStateOf<TopBarHandle?>(null) }
    val topBarState = remember(
        isSearchActive,
        uiState.searchQuery,
        uiState.wallpaperGridColumns,
        uiState.wallpaperLayout,
        uiState.sources,
        uiState.selectedSourceKey,
        currentSelectedSource
    ) {
        val actions: @Composable RowScope.() -> Unit = {
            if (isSearchActive) {
                IconButton(onClick = {
                    isSearchActive = false
                    viewModel.updateSearchQuery("")
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close search"
                    )
                }
            } else {
                IconButton(onClick = { isSearchActive = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search wallpapers"
                    )
                }
            }
            IconButton(onClick = { showSortSheet = true }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Sort,
                    contentDescription = "View and layout options"
                )
            }
        }

        val searchPlaceholder = if (currentSelectedSource != null) {
            "Search ${currentSelectedSource.title}…"
        } else {
            "Search across all sources…"
        }

        val titleContent: (@Composable () -> Unit)? = if (isSearchActive) {
            {
                TopBarSearchField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    onClear = { viewModel.updateSearchQuery("") },
                    placeholder = searchPlaceholder,
                    focusRequester = searchFocusRequester,
                    showClearButton = uiState.searchQuery.isNotEmpty()
                )
            }
        } else null

        val navigationIcon = if (isSearchActive) {
            TopBarState.NavigationIcon(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Close search",
                onClick = {
                    isSearchActive = false
                    viewModel.updateSearchQuery("")
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
            )
        } else null

        val sourceTabsContent: (@Composable () -> Unit)? = if (uiState.sources.isNotEmpty()) {
            {
                val sources = uiState.sources
                val selectedIndex = if (uiState.selectedSourceKey == null) 0
                else (sources.indexOfFirst { it.key == uiState.selectedSourceKey } + 1).coerceAtLeast(0)

                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedIndex,
                    containerColor = Color.Transparent,
                    edgePadding = 12.dp
                ) {
                    Tab(
                        selected = uiState.selectedSourceKey == null,
                        onClick = { viewModel.selectSourceFilter(null) },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Public,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (uiState.selectedSourceKey == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text("All")
                            }
                        }
                    )
                    sources.forEach { source ->
                        val isSelected = uiState.selectedSourceKey == source.key
                        Tab(
                            selected = isSelected,
                            onClick = { viewModel.selectSourceFilter(source.key) },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (!source.iconUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = source.iconUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                        )
                                    } else if (source.iconRes != null && source.iconRes != 0) {
                                        val painter = safePainterResource(source.iconRes)
                                        if (painter != null) {
                                            Image(
                                                painter = painter,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(CircleShape)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Outlined.Public,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.Public,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(source.title)
                                }
                            }
                        )
                    }
                }
            }
        } else null

        TopBarState(
            title = if (isSearchActive) null else "Browse",
            navigationIcon = navigationIcon,
            actions = actions,
            titleContent = titleContent,
            bottomContent = sourceTabsContent,
            autoHideBars = false
        )
    }

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

    val supportsSharedTransitions = sharedTransitionScope != null && animatedVisibilityScope != null
    val pullRefreshState = rememberPullToRefreshState()

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            state = pullRefreshState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullRefreshState,
                    isRefreshing = uiState.isRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topBarInsetPadding(16.dp, hasTabBar = uiState.sources.isNotEmpty()))
                )
            },
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                uiState.isLoading && uiState.wallpapers.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.wallpapers.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(72.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (uiState.isSearching) Icons.Outlined.Search else Icons.Outlined.Explore,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = if (uiState.isSearching) "No wallpapers found for \"${uiState.searchQuery}\""
                                else "No wallpapers available in ${currentSelectedSource?.title ?: "explore feed"}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Try searching with different keywords or check enabled sources in Sources.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                else -> {
                    WallpaperGrid(
                        wallpapers = uiState.wallpapers,
                        onWallpaperSelected = { wallpaper ->
                            onWallpaperSelected(
                                wallpaper,
                                supportsSharedTransitions,
                                uiState.wallpapers
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                        columns = uiState.wallpaperGridColumns,
                        layout = uiState.wallpaperLayout,
                        showDownloadedBadge = uiState.showDownloadBadge,
                        onLoadMore = { viewModel.loadMore() },
                        isLoadingMore = uiState.isLoadingMore,
                        canLoadMore = !uiState.isLoading && !uiState.isLoadingMore && uiState.wallpapers.isNotEmpty(),
                        contentPadding = PaddingValues(
                            start = 4.dp,
                            top = topBarInsetPadding(12.dp, hasTabBar = uiState.sources.isNotEmpty()),
                            end = 4.dp,
                            bottom = bottomBarInsetPadding(4.dp, hasBottomNav = true)
                        ),
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 80.dp)
        )
    }

    ViewFilterSortBottomSheet(
        visible = showSortSheet,
        onDismissRequest = { showSortSheet = false },
        availableTabs = listOf(SheetTab.DISPLAY),
        initialTab = SheetTab.DISPLAY,
        sortSelection = com.joshiminh.wallbase.util.SortSelection(
            field = com.joshiminh.wallbase.util.SortField.DateAdded,
            direction = com.joshiminh.wallbase.util.SortDirection.Descending
        ),
        availableSortFields = emptyList(),
        onSortSelectionChanged = {},
        wallpaperLayout = uiState.wallpaperLayout,
        onWallpaperLayoutChanged = { viewModel.updateWallpaperLayout(it) },
        gridColumns = uiState.wallpaperGridColumns,
        onGridColumnsChanged = { viewModel.updateWallpaperGridColumns(it) }
    )
}
