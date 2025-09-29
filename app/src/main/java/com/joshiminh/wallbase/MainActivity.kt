package com.joshiminh.wallbase

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.joshiminh.wallbase.data.entity.source.Source
import com.joshiminh.wallbase.sources.reddit.RedditCommunity
import com.joshiminh.wallbase.ui.AlbumDetailRoute
import com.joshiminh.wallbase.ui.BrowseScreen
import com.joshiminh.wallbase.ui.EditWallpaperRoute
import com.joshiminh.wallbase.ui.LibraryScreen
import com.joshiminh.wallbase.ui.SettingsScreen
import com.joshiminh.wallbase.ui.SourceBrowseRoute
import com.joshiminh.wallbase.ui.WallpaperDetailRoute
import com.joshiminh.wallbase.ui.theme.WallBaseTheme
import com.joshiminh.wallbase.ui.viewmodel.SettingsViewModel
import com.joshiminh.wallbase.ui.viewmodel.SourcesViewModel
import com.joshiminh.wallbase.ui.viewmodel.WallpaperDetailViewModel
import com.joshiminh.wallbase.ui.viewmodel.WallpaperSelectionViewModel
import com.joshiminh.wallbase.util.network.ServiceLocator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceLocator.initialize(applicationContext)
        enableEdgeToEdge()
        setContent {
            val sourcesViewModel: SourcesViewModel = viewModel(factory = SourcesViewModel.Factory)
            val sourcesUiState by sourcesViewModel.uiState.collectAsStateWithLifecycle()
            val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
            val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
            val darkTheme = settingsUiState.darkTheme

            var pendingIncludeSources by remember { mutableStateOf(true) }

            val backupExporter = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
                onResult = { uri ->
                    if (uri != null) settingsViewModel.exportBackup(uri, pendingIncludeSources)
                }
            )

            val backupImporter = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
                onResult = { uri ->
                    if (uri != null) settingsViewModel.importBackup(uri)
                }
            )

            val backupFileFormatter = remember {
                SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
            }

            WallBaseTheme(darkTheme = darkTheme) {
                WallBaseApp(
                    sourcesUiState = sourcesUiState,
                    settingsUiState = settingsUiState,
                    onToggleDarkTheme = settingsViewModel::setDarkTheme,
                    onUpdateSourceInput = sourcesViewModel::updateSourceInput,
                    onSearchReddit = sourcesViewModel::searchRedditCommunities,
                    onAddSourceFromInput = sourcesViewModel::addSourceFromInput,
                    onQuickAddSource = sourcesViewModel::quickAddSource,
                    onAddRedditCommunity = sourcesViewModel::addRedditCommunity,
                    onClearRedditSearch = sourcesViewModel::clearSearchResults,
                    onRemoveSource = sourcesViewModel::removeSource,
                    onSourcesMessageShown = sourcesViewModel::consumeMessage,
                    onSourceUrlCopied = sourcesViewModel::onSourceUrlCopied,
                    onExportBackup = { includeSources ->
                        pendingIncludeSources = includeSources
                        val timestamp = backupFileFormatter.format(Date())
                        backupExporter.launch("wallbase-backup-$timestamp.wbbackup")
                    },
                    onImportBackup = {
                        backupImporter.launch(
                            arrayOf(
                                "application/zip",
                                "application/octet-stream",
                                "application/x-sqlite3",
                                "application/vnd.sqlite3"
                            )
                        )
                    },
                    onSettingsMessageShown = settingsViewModel::consumeMessage,
                    onToggleAutoDownload = settingsViewModel::setAutoDownload,
                    onUpdateStorageLimit = settingsViewModel::setStorageLimit,
                    onClearPreviewCache = settingsViewModel::clearPreviewCache,
                    onClearOriginals = settingsViewModel::clearOriginalDownloads,
                    onToggleIncludeSourcesInBackup = settingsViewModel::setIncludeSourcesInBackup,
                    onCheckForUpdates = settingsViewModel::checkForUpdates,
                    onOpenUpdateUrl = settingsViewModel::onUpdateUrlOpened,
                    onDismissUpdate = settingsViewModel::dismissAvailableUpdate,
                    onClearUpdateStatus = settingsViewModel::clearUpdateStatus
                )
            }
        }
    }
}

/** Top-level routes for bottom navigation */
private enum class RootRoute(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    Library("library", "Library", Icons.Outlined.Collections),
    Browse("browse", "Browse", Icons.Outlined.Explore),
    Settings("settings", "Settings", Icons.Outlined.Settings)
}

data class TopBarState(
    val title: String? = null,
    val navigationIcon: NavigationIcon? = null,
    val actions: (@Composable RowScope.() -> Unit)? = null,
    val titleContent: (@Composable () -> Unit)? = null
) {
    data class NavigationIcon(
        val icon: ImageVector,
        val contentDescription: String?,
        val onClick: () -> Unit
    )
}

class TopBarHandle internal constructor(
    private val ownerId: Long,
    private val setState: (Long, TopBarState) -> Unit,
    private val clearState: (Long) -> Unit
) {
    fun update(state: TopBarState) {
        setState(ownerId, state)
    }

    fun clear() {
        clearState(ownerId)
    }
}

@SuppressLint("UnrememberedGetBackStackEntry")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun WallBaseApp(
    sourcesUiState: SourcesViewModel.SourcesUiState,
    settingsUiState: SettingsViewModel.SettingsUiState,
    onToggleDarkTheme: (Boolean) -> Unit,
    onUpdateSourceInput: (String) -> Unit,
    onSearchReddit: () -> Unit,
    onAddSourceFromInput: () -> Unit,
    onQuickAddSource: (String) -> Unit,
    onAddRedditCommunity: (RedditCommunity) -> Unit,
    onClearRedditSearch: () -> Unit,
    onRemoveSource: (Source, Boolean) -> Unit,
    onSourcesMessageShown: () -> Unit,
    onSourceUrlCopied: (String) -> Unit,
    onExportBackup: (Boolean) -> Unit,
    onImportBackup: () -> Unit,
    onSettingsMessageShown: () -> Unit,
    onToggleAutoDownload: (Boolean) -> Unit,
    onUpdateStorageLimit: (Long) -> Unit,
    onClearPreviewCache: () -> Unit,
    onClearOriginals: () -> Unit,
    onToggleIncludeSourcesInBackup: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onOpenUpdateUrl: (String) -> Unit,
    onDismissUpdate: () -> Unit,
    onClearUpdateStatus: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val wallpaperSelectionViewModel: WallpaperSelectionViewModel = viewModel()
    val topLevelRoutes = remember { RootRoute.entries.map(RootRoute::route) }
    var topBarState by remember { mutableStateOf<TopBarState?>(null) }
    var topBarOwnerId by remember { mutableStateOf<Long?>(null) }
    var nextTopBarOwnerId by remember { mutableLongStateOf(0L) }
    val acquireTopBar: (TopBarState) -> TopBarHandle = remember {
        { state ->
            val ownerId = nextTopBarOwnerId
            nextTopBarOwnerId += 1
            topBarOwnerId = ownerId
            topBarState = state
            TopBarHandle(
                ownerId = ownerId,
                setState = { id, updated ->
                    if (topBarOwnerId == id) {
                        topBarState = updated
                    }
                },
                clearState = { id ->
                    if (topBarOwnerId == id) {
                        topBarOwnerId = null
                        topBarState = null
                    }
                }
            )
        }
    }
    val canNavigateBack = navController.previousBackStackEntry != null &&
            currentDestination?.route !in topLevelRoutes
    val showTopBar = currentDestination?.route != "wallpaperDetail"

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = {
                        val overrideState = topBarState
                        val customTitle = overrideState?.titleContent
                        when {
                            customTitle != null -> customTitle()
                            else -> {
                                Text(
                                    text = overrideState?.title ?: currentTitle(currentDestination),
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        val overrideState = topBarState
                        val overrideNav = overrideState?.navigationIcon
                        when {
                            overrideNav != null -> {
                                IconButton(onClick = overrideNav.onClick) {
                                    Icon(
                                        imageVector = overrideNav.icon,
                                        contentDescription = overrideNav.contentDescription,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            overrideState != null -> {
                                // Explicitly no navigation icon when a top bar state is provided.
                            }
                            canNavigateBack -> {
                                IconButton(onClick = { navController.navigateUp() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            else -> { /* no nav icon on top-level */ }
                        }
                    },
                    actions = {
                        topBarState?.actions?.invoke(this)
                    },
                    colors = TopAppBarDefaults.topAppBarColors()
                )
            }
        },
        bottomBar = {
            if (currentDestination?.route in topLevelRoutes) {
                NavigationBar {
                    val items = RootRoute.entries.toList()
                    items.forEach { item ->
                        NavigationBarItem(
                            selected = currentDestination.isTopDestination(item),
                            onClick = {
                                if (!currentDestination.isTopDestination(item)) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        SharedTransitionLayout(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            val sharedScope = this
            NavHost(
                navController = navController,
                startDestination = RootRoute.Library.route
            ) {
                composable(RootRoute.Library.route) {
                    LibraryScreen(
                        onWallpaperSelected = { wallpaper ->
                            wallpaperSelectionViewModel.select(wallpaper)
                            navController.navigateSingleTop("wallpaperDetail") {
                                popUpTo("wallpaperDetail") { inclusive = true }
                            }
                        },
                        onAlbumSelected = { album ->
                            navController.navigateSingleTop("album/${album.id}")
                        },
                        onConfigureTopBar = acquireTopBar,
                        sharedTransitionScope = sharedScope,
                        animatedVisibilityScope = this
                    )
                }
                composable(RootRoute.Browse.route) {
                    BrowseScreen(
                        uiState = sourcesUiState,
                        onUpdateSourceInput = onUpdateSourceInput,
                        onSearchReddit = onSearchReddit,
                        onAddSourceFromInput = onAddSourceFromInput,
                        onQuickAddSource = onQuickAddSource,
                        onAddRedditCommunity = onAddRedditCommunity,
                        onClearSearchResults = onClearRedditSearch,
                        onOpenSource = { source ->
                            navController.navigateSingleTop("sourceBrowse/${Uri.encode(source.key)}")
                        },
                        onRemoveSource = onRemoveSource,
                        onMessageShown = onSourcesMessageShown,
                        onSourceUrlCopied = onSourceUrlCopied
                    )
                }
            composable("sourceBrowse/{sourceKey}") { backStackEntry ->
                val key = backStackEntry.arguments?.getString("sourceKey")
                if (key.isNullOrBlank()) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    SourceBrowseRoute(
                        sourceKey = key,
                        onWallpaperSelected = { wallpaper ->
                            wallpaperSelectionViewModel.select(wallpaper)
                            navController.navigateSingleTop("wallpaperDetail") {
                                popUpTo("wallpaperDetail") { inclusive = true }
                            }
                        },
                        onConfigureTopBar = acquireTopBar,
                        sharedTransitionScope = sharedScope,
                        animatedVisibilityScope = this
                    )
                }
            }
            composable("album/{albumId}") { backStackEntry ->
                val id = backStackEntry.arguments?.getString("albumId")?.toLongOrNull()
                if (id == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    AlbumDetailRoute(
                        albumId = id,
                        onWallpaperSelected = { wallpaper ->
                            wallpaperSelectionViewModel.select(wallpaper)
                            navController.navigateSingleTop("wallpaperDetail") {
                                popUpTo("wallpaperDetail") { inclusive = true }
                            }
                        },
                        onAlbumDeleted = {
                            navController.popBackStack()
                        },
                        onConfigureTopBar = acquireTopBar,
                        sharedTransitionScope = sharedScope,
                        animatedVisibilityScope = this
                    )
                }
            }
            composable("wallpaperDetail") {
                val wallpaper by wallpaperSelectionViewModel.selectedWallpaper.collectAsStateWithLifecycle()
                if (wallpaper == null) {
                    topBarState = null
                    val activity = LocalActivity.current
                    LaunchedEffect(Unit) {
                        wallpaperSelectionViewModel.clear()
                        val popped = navController.popBackStack()
                        if (!popped) {
                            activity?.finish()
                        }
                    }
                } else {
                    val activity = LocalActivity.current
                    val navigateBack: () -> Unit = {
                        wallpaperSelectionViewModel.clear()
                        val popped = navController.popBackStack()
                        if (!popped) {
                            activity?.finish()
                        }
                    }

                    BackHandler(onBack = navigateBack)

                    val viewModel: WallpaperDetailViewModel = viewModel(
                        factory = WallpaperDetailViewModel.Factory
                    )

                    WallpaperDetailRoute(
                        wallpaper = wallpaper!!,
                        onNavigateBack = navigateBack,
                        onEditWallpaper = {
                            viewModel.prepareEditor()
                            navController.navigate("wallpaperDetail/edit")
                        },
                        sharedTransitionScope = sharedScope,
                        animatedVisibilityScope = this,
                        viewModel = viewModel
                    )

                    LaunchedEffect(wallpaper!!.id) {
                        topBarState = null
                    }
                }
            }
            composable("wallpaperDetail/edit") {
                val parentEntry = remember(navController) { navController.getBackStackEntry("wallpaperDetail") }
                val viewModel: WallpaperDetailViewModel = viewModel(
                    parentEntry,
                    factory = WallpaperDetailViewModel.Factory
                )
                val navigateBack: () -> Unit = {
                    navController.popBackStack()
                }

                BackHandler(onBack = navigateBack)

                EditWallpaperRoute(
                    onNavigateBack = navigateBack,
                    viewModel = viewModel
                )

                LaunchedEffect(Unit) {
                    topBarState = null
                }
            }
            composable(RootRoute.Settings.route) {
                SettingsScreen(
                    uiState = settingsUiState,
                    onToggleDarkTheme = onToggleDarkTheme,
                    onExportBackup = onExportBackup,
                    onImportBackup = onImportBackup,
                    onMessageShown = onSettingsMessageShown,
                    onToggleAutoDownload = onToggleAutoDownload,
                    onUpdateStorageLimit = onUpdateStorageLimit,
                    onClearPreviewCache = onClearPreviewCache,
                    onClearOriginals = onClearOriginals,
                    onToggleIncludeSourcesInBackup = onToggleIncludeSourcesInBackup,
                    onCheckForUpdates = onCheckForUpdates,
                    onOpenUpdateUrl = onOpenUpdateUrl,
                    onDismissUpdate = onDismissUpdate,
                    onClearUpdateStatus = onClearUpdateStatus
                )
            }
        }
    }
}
}

private fun currentTitle(dest: NavDestination?): String = when {
    dest.isTopDestination(RootRoute.Library) -> "Library"
    dest.isTopDestination(RootRoute.Browse) -> "Browse"
    dest.isTopDestination(RootRoute.Settings) -> "Settings"
    dest?.route == "wallpaperDetail" -> "Wallpaper"
    dest?.route == "photosAlbums" -> "Photos albums"
    dest?.route == "album/{albumId}" -> "Album"
    else -> "WallBase"
}

private fun NavDestination?.isTopDestination(item: RootRoute): Boolean {
    return this?.hierarchy?.any { it.route == item.route } == true
}

private fun NavController.navigateSingleTop(
    route: String,
    builder: NavOptionsBuilder.() -> Unit = {}
) {
    this.navigate(route) {
        launchSingleTop = true
        builder()
    }
}