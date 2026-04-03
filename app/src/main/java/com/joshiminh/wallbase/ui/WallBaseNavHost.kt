@file:Suppress("DEPRECATION", "unused")

package com.joshiminh.wallbase.ui

import com.joshiminh.wallbase.navigation.*
import com.joshiminh.wallbase.MainActivity
import com.joshiminh.wallbase.screens.*
import com.joshiminh.wallbase.R
import com.joshiminh.wallbase.*
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
import com.joshiminh.wallbase.data.entity.Source
import com.joshiminh.wallbase.data.entity.WallpaperItem
import com.joshiminh.wallbase.sources.RedditCommunity
import com.joshiminh.wallbase.ui.*
import com.joshiminh.wallbase.screens.LibraryScreen
import com.joshiminh.wallbase.screens.AlbumRoute
import com.joshiminh.wallbase.ui.theme.WallBaseTheme
import com.joshiminh.wallbase.ui.viewmodel.*
import com.joshiminh.wallbase.data.repository.AppTheme
import com.joshiminh.wallbase.data.repository.AppAccentColor
import com.joshiminh.wallbase.util.network.ServiceLocator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import okio.Path.Companion.toOkioPath

@SuppressLint("UnrememberedGetBackStackEntry")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun WallBaseApp(
    sourcesUiState: SourcesViewModel.SourcesUiState,
    settingsUiState: SettingsViewModel.SettingsUiState,
    onSetAppTheme: (AppTheme) -> Unit,
    onSetAppAccentColor: (AppAccentColor) -> Unit,
    onToggleAnimations: (Boolean) -> Unit,
    onUpdateSourceInput: (String) -> Unit,
    onSearchReddit: () -> Unit,
    onAddSourceFromInput: () -> Unit,
    onAddRedditCommunity: (RedditCommunity) -> Unit,
    onClearRedditSearch: () -> Unit,
    onRemoveSource: (Source, Boolean) -> Unit,
    onSourcesMessageShown: () -> Unit,
    onSourceUrlCopied: (String) -> Unit,
    onExportBackup: (Boolean) -> Unit,
    onImportBackup: () -> Unit,
    onSettingsMessageShown: () -> Unit,
    onSettingsRestartConsumed: () -> Unit,
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
    val sharedTransitionsEnabled by remember(
        settingsUiState.animationsEnabled,
        selectedWallpaperState,
    ) {
        derivedStateOf {
            settingsUiState.animationsEnabled &&
                selectedWallpaperState?.enableSharedTransition != false
        }
    }

    val topLevelRoutes = remember { RootRoute.entries.map(RootRoute::route) }

    val animationsEnabledState = rememberUpdatedState(settingsUiState.animationsEnabled)

    val navigateToWallpaperDetail = remember(
        navController,
        wallpaperSelectionViewModel,
    ) {
        { wallpaper: WallpaperItem, enableSharedTransition: Boolean ->
            val useSharedTransition =
                animationsEnabledState.value && enableSharedTransition
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
                            onAddRedditCommunity = onAddRedditCommunity,
                            onClearSearchResults = onClearRedditSearch,
                            onOpenSource = { source ->
                                navController.navigateSingleTop(
                                    "sourceBrowse/${Uri.encode(source.key)}",
                                )
                            },
                            onRemoveSource = onRemoveSource,
                            onMessageShown = onSourcesMessageShown,
                            onSourceUrlCopied = onSourceUrlCopied,
                            onConfigureTopBar = acquireTopBar,
                        )
                    }

                    composable("sourceBrowse/{sourceKey}") { backStackEntry ->
                        val key = backStackEntry.arguments?.getString("sourceKey")
                        if (key.isNullOrBlank()) {
                            LaunchedEffect(Unit) { navController.popBackStack() }
                        } else {
                            val animatedScope = this.takeIf { sharedScope != null }
                            SourceRoute(
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
                            AlbumRoute(
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

                            DetailRoute(
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
                            onSetAppTheme = onSetAppTheme,
                            onSetAppAccentColor = onSetAppAccentColor,
                            onToggleAnimations = onToggleAnimations,
                            onExportBackup = onExportBackup,
                            onImportBackup = onImportBackup,
                            onMessageShown = onSettingsMessageShown,
                            onRestartConsumed = onSettingsRestartConsumed,
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

            SharedTransitionHost(
                enabled = sharedTransitionsEnabled,
                modifier = navContainerModifier,
                content = renderNavHost,
            )
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionHost(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (SharedTransitionScope?) -> Unit,
) {
    if (enabled) {
        SharedTransitionLayout(modifier = modifier) {
            content(this)
        }
    } else {
        Box(modifier = modifier) {
            content(null)
        }
    }
}

