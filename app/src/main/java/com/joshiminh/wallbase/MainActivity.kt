@file:Suppress("DEPRECATION", "unused")

package com.joshiminh.wallbase

import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.*
import com.joshiminh.wallbase.data.entity.source.Source
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem
import com.joshiminh.wallbase.sources.reddit.RedditCommunity
import com.joshiminh.wallbase.ui.*
import com.joshiminh.wallbase.ui.theme.WallBaseTheme
import com.joshiminh.wallbase.ui.viewmodel.*
import com.joshiminh.wallbase.util.network.ServiceLocator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ────────────────────────────────────────────────────────────────────────────────
// App lock request types
// ────────────────────────────────────────────────────────────────────────────────
private sealed interface AppLockRequest {
    data object Enable : AppLockRequest
    data object Unlock : AppLockRequest
}

// ────────────────────────────────────────────────────────────────────────────────
/** Top-level routes for bottom navigation */
// ────────────────────────────────────────────────────────────────────────────────
private enum class RootRoute(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Library("library", "Library", Icons.Outlined.Collections),
    Browse("browse", "Browse", Icons.Outlined.Explore),
    Settings("settings", "Settings", Icons.Outlined.Settings),
}

// ────────────────────────────────────────────────────────────────────────────────
// Top bar state & handle
// ────────────────────────────────────────────────────────────────────────────────
data class TopBarState(
    val title: String? = null,
    val navigationIcon: NavigationIcon? = null,
    val actions: (@Composable RowScope.() -> Unit)? = null,
    val titleContent: (@Composable () -> Unit)? = null,
) {
    data class NavigationIcon(
        val icon: ImageVector,
        val contentDescription: String?,
        val onClick: () -> Unit,
    )
}

class TopBarHandle internal constructor(
    private val ownerId: Long,
    private val setState: (Long, TopBarState) -> Unit,
    private val clearState: (Long) -> Unit,
) {
    fun update(state: TopBarState) = setState(ownerId, state)
    fun clear() = clearState(ownerId)
}

// ────────────────────────────────────────────────────────────────────────────────
// Navigation helpers
// ────────────────────────────────────────────────────────────────────────────────
private fun NavDestination?.isTopDestination(item: RootRoute): Boolean =
    this?.hierarchy?.any { it.route == item.route } == true

private fun NavController.navigateSingleTop(
    route: String,
    builder: NavOptionsBuilder.() -> Unit = {},
) = navigate(route) {
    launchSingleTop = true
    builder()
}

fun currentTitle(dest: NavDestination?): String = when {
    dest.isTopDestination(RootRoute.Library) -> "Library"
    dest.isTopDestination(RootRoute.Browse) -> "Browse"
    dest.isTopDestination(RootRoute.Settings) -> "Settings"
    dest?.route == "wallpaperDetail" -> "Wallpaper"
    dest?.route == "photosAlbums"   -> "Photos albums"
    dest?.route == "album/{albumId}" -> "Album"
    else -> "WallBase"
}

// ────────────────────────────────────────────────────────────────────────────────
// Lock overlay
// ────────────────────────────────────────────────────────────────────────────────
@Composable
fun AppLockOverlay(
    modifier: Modifier = Modifier,
    onUnlock: () -> Unit,
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Unlock WallBase",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Use your device credentials to continue.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            FilledTonalButton(onClick = onUnlock) {
                Text("Unlock")
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────────
// Activity
// ────────────────────────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceLocator.ensureInitialized(applicationContext)
        enableEdgeToEdge()

        setContent {
            val sourcesViewModel: SourcesViewModel = viewModel(factory = SourcesViewModel.Factory)
            val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)

            val sourcesUiState by sourcesViewModel.uiState.collectAsStateWithLifecycle()
            val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()

            var pendingIncludeSources by remember { mutableStateOf(true) }

            val backupExporter = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
            ) { uri ->
                if (uri != null) settingsViewModel.exportBackup(uri, pendingIncludeSources)
            }

            val backupImporter = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri != null) settingsViewModel.importBackup(uri)
            }

            val backupFileFormatter = remember { SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US) }

            WallBaseTheme(darkTheme = settingsUiState.darkTheme) {
                WallBaseApp(
                    sourcesUiState = sourcesUiState,
                    settingsUiState = settingsUiState,
                    onToggleDarkTheme = settingsViewModel::setDarkTheme,
                    onToggleAnimations = settingsViewModel::setAnimationsEnabled,
                    onUpdateSourceInput = sourcesViewModel::updateSourceInput,
                    onSearchReddit = sourcesViewModel::searchRedditCommunities,
                    onAddSourceFromInput = sourcesViewModel::addSourceFromInput,
                    onQuickAddSource = sourcesViewModel::quickAddSource,
                    onAddRedditCommunity = sourcesViewModel::addRedditCommunity,
                    onClearRedditSearch = sourcesViewModel::clearSearchResults,
                    onRemoveSource = sourcesViewModel::removeSource,
                    onEditSource = sourcesViewModel::editSource,
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
                                "application/vnd.sqlite3",
                            ),
                        )
                    },
                    onSettingsMessageShown = settingsViewModel::consumeMessage,
                    onToggleAutoDownload = settingsViewModel::setAutoDownload,
                    onUpdateStorageLimit = settingsViewModel::setStorageLimit,
                    onClearPreviewCache = settingsViewModel::clearPreviewCache,
                    onClearOriginals = settingsViewModel::clearOriginalDownloads,
                    onToggleIncludeSourcesInBackup = settingsViewModel::setIncludeSourcesInBackup,
                    onSetAppLockEnabled = settingsViewModel::setAppLockEnabled,
                    onShowSettingsMessage = settingsViewModel::showMessage,
                    onCompleteOnboarding = settingsViewModel::markOnboardingComplete,
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────────
// App root
// ────────────────────────────────────────────────────────────────────────────────
@SuppressLint("UnrememberedGetBackStackEntry")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun WallBaseApp(
    sourcesUiState: SourcesViewModel.SourcesUiState,
    settingsUiState: SettingsViewModel.SettingsUiState,
    onToggleDarkTheme: (Boolean) -> Unit,
    onToggleAnimations: (Boolean) -> Unit,
    onUpdateSourceInput: (String) -> Unit,
    onSearchReddit: () -> Unit,
    onAddSourceFromInput: () -> Unit,
    onQuickAddSource: (String) -> Unit,
    onAddRedditCommunity: (RedditCommunity) -> Unit,
    onClearRedditSearch: () -> Unit,
    onRemoveSource: (Source, Boolean) -> Unit,
    onEditSource: (Source, String) -> Unit,
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
    onSetAppLockEnabled: (Boolean) -> Unit,
    onShowSettingsMessage: (String) -> Unit,
    onCompleteOnboarding: () -> Unit,
) {
    val navController = rememberNavController()
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination
    val wallpaperSelectionViewModel: WallpaperSelectionViewModel = viewModel()
    val selectedWallpaperState by wallpaperSelectionViewModel
        .selectedWallpaper
        .collectAsStateWithLifecycle()
    val sharedTransitionsEnabled =
        settingsUiState.animationsEnabled &&
            selectedWallpaperState?.enableSharedTransition != false

    val topLevelRoutes = remember { RootRoute.entries.map(RootRoute::route) }

    val navigateToWallpaperDetail = remember(
        navController,
        wallpaperSelectionViewModel,
        settingsUiState.animationsEnabled,
    ) {
        { wallpaper: WallpaperItem, enableSharedTransition: Boolean ->
            val useSharedTransition =
                settingsUiState.animationsEnabled && enableSharedTransition
            wallpaperSelectionViewModel.select(wallpaper, useSharedTransition)
            navController.navigateSingleTop("wallpaperDetail") {
                popUpTo("wallpaperDetail") { inclusive = true }
            }
        }
    }

    var topBarState by remember { mutableStateOf<TopBarState?>(null) }
    var topBarOwnerId by remember { mutableStateOf<Long?>(null) }
    var nextTopBarOwnerId by remember { mutableLongStateOf(0L) }

    val activity = LocalActivity.current as? ComponentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val keyguardManager = remember(activity) { activity?.getSystemService(KeyguardManager::class.java) }

    var isAppUnlocked by rememberSaveable(settingsUiState.appLockEnabled) {
        mutableStateOf(!settingsUiState.appLockEnabled)
    }
    var pendingAppLockRequest by remember { mutableStateOf<AppLockRequest?>(null) }
    var activeAppLockRequest by remember { mutableStateOf<AppLockRequest?>(null) }
    var skipNextResume by remember { mutableStateOf(false) }

    val appLockLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        when (activeAppLockRequest) {
            AppLockRequest.Enable -> {
                if (result.resultCode == Activity.RESULT_OK) {
                    onSetAppLockEnabled(true)
                    isAppUnlocked = true
                    onShowSettingsMessage("App lock enabled")
                } else {
                    onShowSettingsMessage("App lock not enabled")
                }
            }
            AppLockRequest.Unlock -> {
                if (result.resultCode == Activity.RESULT_OK) {
                    isAppUnlocked = true
                } else {
                    isAppUnlocked = false
                    onShowSettingsMessage("Unlock required to continue")
                }
            }
            null -> Unit
        }
        activeAppLockRequest = null
        pendingAppLockRequest = null
    }

    LaunchedEffect(pendingAppLockRequest, keyguardManager, activity) {
        val request = pendingAppLockRequest ?: return@LaunchedEffect
        if (activeAppLockRequest != null) return@LaunchedEffect

        val currentActivity = activity ?: run {
            pendingAppLockRequest = null
            return@LaunchedEffect
        }

        val keyguard = keyguardManager
        if (keyguard == null || !keyguard.isDeviceSecure) {
            if (request == AppLockRequest.Enable) {
                onShowSettingsMessage("Set up a screen lock to use app lock")
            } else {
                onShowSettingsMessage("Device lock not available")
            }
            pendingAppLockRequest = null
            return@LaunchedEffect
        }

        val description = when (request) {
            AppLockRequest.Enable -> "Confirm your screen lock to enable app lock."
            AppLockRequest.Unlock -> "Unlock to continue using WallBase."
        }

        val title = currentActivity.getString(R.string.app_name)
        val intent = keyguard.createConfirmDeviceCredentialIntent(title, description)
        if (intent == null) {
            onShowSettingsMessage("Unable to open device lock screen")
            pendingAppLockRequest = null
            return@LaunchedEffect
        }

        activeAppLockRequest = request
        skipNextResume = true
        appLockLauncher.launch(intent)
    }

    DisposableEffect(lifecycleOwner, settingsUiState.appLockEnabled) {
        val observer = LifecycleEventObserver { _: LifecycleOwner, event: Lifecycle.Event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (skipNextResume) {
                        skipNextResume = false
                    } else if (
                        settingsUiState.appLockEnabled &&
                        !isAppUnlocked &&
                        pendingAppLockRequest == null &&
                        activeAppLockRequest == null
                    ) {
                        pendingAppLockRequest = AppLockRequest.Unlock
                    }
                }
                Lifecycle.Event.ON_PAUSE -> if (settingsUiState.appLockEnabled) {
                    isAppUnlocked = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(settingsUiState.appLockEnabled) {
        if (!settingsUiState.appLockEnabled) {
            isAppUnlocked = true
            pendingAppLockRequest = null
            activeAppLockRequest = null
        }
    }

    val handleAppLockToggle: (Boolean) -> Unit = { enable ->
        if (enable) {
            if (pendingAppLockRequest == null && activeAppLockRequest == null) {
                pendingAppLockRequest = AppLockRequest.Enable
            }
        } else if (settingsUiState.appLockEnabled) {
            onSetAppLockEnabled(false)
            isAppUnlocked = true
            onShowSettingsMessage("App lock disabled")
        }
    }

    val acquireTopBar: (TopBarState) -> TopBarHandle = remember {
        { state ->
            val ownerId = nextTopBarOwnerId
            nextTopBarOwnerId += 1
            topBarOwnerId = ownerId
            topBarState = state
            TopBarHandle(
                ownerId = ownerId,
                setState = { id, updated -> if (topBarOwnerId == id) topBarState = updated },
                clearState = { id ->
                    if (topBarOwnerId == id) {
                        topBarOwnerId = null
                        topBarState = null
                    }
                },
            )
        }
    }

    val canNavigateBack =
        navController.previousBackStackEntry != null && currentDestination?.route !in topLevelRoutes
    val showTopBar = currentDestination?.route != "wallpaperDetail"

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                if (showTopBar) {
                    TopAppBar(
                        modifier = Modifier.fillMaxWidth(),
                        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
                        title = {
                            val overrideState = topBarState
                            val customTitle = overrideState?.titleContent
                            when {
                                customTitle != null -> customTitle()
                                else -> Text(
                                    text = overrideState?.title ?: currentTitle(currentDestination),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            }
                        },
                        navigationIcon = {
                            val overrideState = topBarState
                            val overrideNav = overrideState?.navigationIcon
                            when {
                                overrideNav != null -> IconButton(onClick = overrideNav.onClick) {
                                    Icon(
                                        imageVector = overrideNav.icon,
                                        contentDescription = overrideNav.contentDescription,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                overrideState != null -> Unit // no nav icon when state provided
                                canNavigateBack -> IconButton(onClick = { navController.navigateUp() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                else -> Unit
                            }
                        },
                        actions = { topBarState?.actions?.invoke(this) },
                        colors = TopAppBarDefaults.topAppBarColors(),
                    )
                }
            },
            bottomBar = {
                if (currentDestination?.route in topLevelRoutes) {
                    NavigationBar {
                        RootRoute.entries.forEach { item ->
                            NavigationBarItem(
                                selected = currentDestination.isTopDestination(item),
                                onClick = {
                                    if (!currentDestination.isTopDestination(item)) {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label,
                                        modifier = Modifier.size(24.dp),
                                    )
                                },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            val navContainerModifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)

            val renderNavHost: @Composable (SharedTransitionScope?) -> Unit = { sharedScope ->
                NavHost(
                    navController = navController,
                    startDestination = RootRoute.Library.route,
                ) {
                    composable(RootRoute.Library.route) {
                        val animatedScope = this.takeIf { sharedScope != null }
                        LibraryScreen(
                            onWallpaperSelected = navigateToWallpaperDetail,
                            onAlbumSelected = { album ->
                                navController.navigateSingleTop("album/${album.id}")
                            },
                            onConfigureTopBar = acquireTopBar,
                            sharedTransitionScope = sharedScope,
                            animatedVisibilityScope = animatedScope,
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
                                navController.navigateSingleTop(
                                    "sourceBrowse/${Uri.encode(source.key)}",
                                )
                            },
                            onRemoveSource = onRemoveSource,
                            onEditSource = onEditSource,
                            onMessageShown = onSourcesMessageShown,
                            onSourceUrlCopied = onSourceUrlCopied,
                        )
                    }

                    composable("sourceBrowse/{sourceKey}") { backStackEntry ->
                        val key = backStackEntry.arguments?.getString("sourceKey")
                        if (key.isNullOrBlank()) {
                            LaunchedEffect(Unit) { navController.popBackStack() }
                        } else {
                            val animatedScope = this.takeIf { sharedScope != null }
                            SourceBrowseRoute(
                                sourceKey = key,
                                onWallpaperSelected = navigateToWallpaperDetail,
                                onConfigureTopBar = acquireTopBar,
                                sharedTransitionScope = sharedScope,
                                animatedVisibilityScope = animatedScope,
                            )
                        }
                    }

                    composable("album/{albumId}") { backStackEntry ->
                        val id = backStackEntry.arguments?.getString("albumId")?.toLongOrNull()
                        if (id == null) {
                            LaunchedEffect(Unit) { navController.popBackStack() }
                        } else {
                            val animatedScope = this.takeIf { sharedScope != null }
                            AlbumDetailRoute(
                                albumId = id,
                                onWallpaperSelected = navigateToWallpaperDetail,
                                onAlbumDeleted = { navController.popBackStack() },
                                onConfigureTopBar = acquireTopBar,
                                sharedTransitionScope = sharedScope,
                                animatedVisibilityScope = animatedScope,
                            )
                        }
                    }

                    composable("wallpaperDetail") {
                        val selectedWallpaperState by wallpaperSelectionViewModel
                            .selectedWallpaper.collectAsStateWithLifecycle()

                        val wallpaper = selectedWallpaperState?.wallpaper
                        val enableSharedTransition =
                            selectedWallpaperState?.enableSharedTransition == true

                        if (wallpaper == null) {
                            topBarState = null
                            val act = LocalActivity.current
                            LaunchedEffect(Unit) {
                                wallpaperSelectionViewModel.clear()
                                val popped = navController.popBackStack()
                                if (!popped) act?.finish()
                            }
                        } else {
                            val act = LocalActivity.current
                            val navigateBack: () -> Unit = {
                                wallpaperSelectionViewModel.clear()
                                val popped = navController.popBackStack()
                                if (!popped) act?.finish()
                            }

                            BackHandler(onBack = navigateBack)

                            val viewModel: WallpaperDetailViewModel =
                                viewModel(factory = WallpaperDetailViewModel.Factory)

                            val detailSharedScope =
                                sharedScope.takeIf { enableSharedTransition }
                            val detailVisibilityScope =
                                if (detailSharedScope != null) this else null

                            WallpaperDetailRoute(
                                wallpaper = wallpaper,
                                onNavigateBack = navigateBack,
                                sharedTransitionScope = detailSharedScope,
                                animatedVisibilityScope = detailVisibilityScope,
                                viewModel = viewModel,
                            )

                            LaunchedEffect(wallpaper.id) { topBarState = null }
                        }
                    }

                    composable(RootRoute.Settings.route) {
                        SettingsScreen(
                            uiState = settingsUiState,
                            onToggleDarkTheme = onToggleDarkTheme,
                            onToggleAnimations = onToggleAnimations,
                            onExportBackup = onExportBackup,
                            onImportBackup = onImportBackup,
                            onMessageShown = onSettingsMessageShown,
                            onToggleAutoDownload = onToggleAutoDownload,
                            onUpdateStorageLimit = onUpdateStorageLimit,
                            onClearPreviewCache = onClearPreviewCache,
                            onClearOriginals = onClearOriginals,
                            onToggleIncludeSourcesInBackup = onToggleIncludeSourcesInBackup,
                            onRequestAppLockChange = handleAppLockToggle,
                        )
                    }
                }
            }

            if (sharedTransitionsEnabled) {
                SharedTransitionLayout(modifier = navContainerModifier) {
                    renderNavHost(this)
                }
            } else {
                Box(modifier = navContainerModifier) {
                    renderNavHost(null)
                }
            }
        }

        when {
            !settingsUiState.hasCompletedOnboarding -> {
                LandingScreen(
                    onFinished = onCompleteOnboarding,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(1f),
                )
            }

            settingsUiState.appLockEnabled && !isAppUnlocked -> {
                AppLockOverlay(
                    modifier = Modifier.fillMaxSize(),
                    onUnlock = {
                        if (pendingAppLockRequest == null && activeAppLockRequest == null) {
                            pendingAppLockRequest = AppLockRequest.Unlock
                        }
                    },
                )
            }
        }
    }
}
