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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
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
import com.joshiminh.wallbase.data.entity.CategoryItem
import com.joshiminh.wallbase.data.entity.Source
import com.joshiminh.wallbase.data.entity.WallpaperItem
import com.joshiminh.wallbase.sources.RedditCommunity
import com.joshiminh.wallbase.ui.*
import com.joshiminh.wallbase.screens.LibraryScreen
import com.joshiminh.wallbase.screens.AlbumRoute
import com.joshiminh.wallbase.ui.theme.WallBaseTheme
import com.joshiminh.wallbase.ui.theme.WallBaseMotion
import com.joshiminh.wallbase.ui.viewmodel.*
import com.joshiminh.wallbase.data.repository.AppTheme
import com.joshiminh.wallbase.data.repository.AppAccentColor
import androidx.hilt.navigation.compose.hiltViewModel
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
    onToggleDynamicColor: (Boolean) -> Unit,
    onToggleAmoledDark: (Boolean) -> Unit,
    onToggleAnimations: (Boolean) -> Unit,
    onToggleCategories: (Boolean) -> Unit = {},
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
    onToggleShowHorizontalWallpapers: (Boolean) -> Unit,
    onToggleShowDownloadBadge: (Boolean) -> Unit,
    onSaveSourceCredentials: (String) -> Unit,
    onShowSettingsMessage: (String) -> Unit,
    onCompleteOnboarding: () -> Unit,
    onCreateCategory: (String) -> Unit = {},
    onRenameCategory: (CategoryItem, String) -> Unit = { _, _ -> },
    onDeleteCategory: (CategoryItem) -> Unit = {},
    onReorderCategories: (List<Long>) -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onDismissAvailableUpdate: () -> Unit = {},
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

    val navigateToWallpaperDetail: (WallpaperItem, Boolean, List<WallpaperItem>) -> Unit = remember(
        navController,
        wallpaperSelectionViewModel,
    ) {
        { wallpaper: WallpaperItem, enableSharedTransition: Boolean, wallpapers: List<WallpaperItem> ->
            val useSharedTransition =
                animationsEnabledState.value && enableSharedTransition
            val list = if (wallpapers.isNotEmpty()) wallpapers else listOf(wallpaper)
            val index = list.indexOfFirst { it.id == wallpaper.id }.coerceAtLeast(0)
            wallpaperSelectionViewModel.select(
                wallpaper = wallpaper,
                wallpapers = list,
                initialIndex = index,
                enableSharedTransition = useSharedTransition,
            )
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
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
            topBar = {
                if (showTopBar) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.background,
                        tonalElevation = 0.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .statusBarsPadding()
                        ) {
                            TopAppBar(
                                modifier = Modifier.fillMaxWidth(),
                                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                                title = {
                                    val overrideState = topBarState
                                    val customTitle = overrideState?.titleContent
                                    when {
                                        customTitle != null -> customTitle()
                                        else -> Text(
                                            text = overrideState?.title ?: currentTitle(currentDestination),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
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
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.background,
                                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                                ),
                            )
                            topBarState?.bottomContent?.invoke()
                        }
                    }
                }
            },
            bottomBar = {
                if (currentDestination?.route in topLevelRoutes) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                    ) {
                        RootRoute.entries.forEach { item ->
                            val isSelected = currentDestination.isTopDestination(item)
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    if (!isSelected) {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    AnimatedBottomBarIcon(
                                        item = item,
                                        isSelected = isSelected,
                                        animationsEnabled = settingsUiState.animationsEnabled,
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                            )
                        }
                    }
                }
            },
        ) { _ ->
            val navContainerModifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)

            val renderNavHost: @Composable (SharedTransitionScope?) -> Unit = { sharedScope ->
                val rootRouteOrder = remember {
                    listOf(
                        RootRoute.Library.route,
                        RootRoute.Search.route,
                        RootRoute.Albums.route,
                        RootRoute.Browse.route,
                        RootRoute.Settings.route,
                    )
                }

                NavHost(
                    navController = navController,
                    startDestination = RootRoute.Library.route,
                    enterTransition = {
                        if (settingsUiState.animationsEnabled) {
                            val initialRoute = initialState.destination.route
                            val targetRoute = targetState.destination.route
                            val initialIdx = rootRouteOrder.indexOf(initialRoute)
                            val targetIdx = rootRouteOrder.indexOf(targetRoute)
                            if (initialIdx >= 0 && targetIdx >= 0) {
                                if (targetIdx > initialIdx) {
                                    slideInHorizontally(
                                        initialOffsetX = { (it * 0.15f).toInt() },
                                        animationSpec = tween(WallBaseMotion.mediumMillis),
                                    ) + fadeIn(animationSpec = tween(WallBaseMotion.mediumMillis))
                                } else {
                                    slideInHorizontally(
                                        initialOffsetX = { -(it * 0.15f).toInt() },
                                        animationSpec = tween(WallBaseMotion.mediumMillis),
                                    ) + fadeIn(animationSpec = tween(WallBaseMotion.mediumMillis))
                                }
                            } else {
                                slideInHorizontally(
                                    initialOffsetX = { (it * 0.25f).toInt() },
                                    animationSpec = tween(WallBaseMotion.mediumMillis),
                                ) + fadeIn(animationSpec = tween(WallBaseMotion.mediumMillis))
                            }
                        } else {
                            fadeIn(animationSpec = snap())
                        }
                    },
                    exitTransition = {
                        if (settingsUiState.animationsEnabled) {
                            val initialRoute = initialState.destination.route
                            val targetRoute = targetState.destination.route
                            val initialIdx = rootRouteOrder.indexOf(initialRoute)
                            val targetIdx = rootRouteOrder.indexOf(targetRoute)
                            if (initialIdx >= 0 && targetIdx >= 0) {
                                if (targetIdx > initialIdx) {
                                    slideOutHorizontally(
                                        targetOffsetX = { -(it * 0.15f).toInt() },
                                        animationSpec = tween(WallBaseMotion.shortMillis),
                                    ) + fadeOut(animationSpec = tween(WallBaseMotion.shortMillis))
                                } else {
                                    slideOutHorizontally(
                                        targetOffsetX = { (it * 0.15f).toInt() },
                                        animationSpec = tween(WallBaseMotion.shortMillis),
                                    ) + fadeOut(animationSpec = tween(WallBaseMotion.shortMillis))
                                }
                            } else {
                                slideOutHorizontally(
                                    targetOffsetX = { -(it * 0.25f).toInt() },
                                    animationSpec = tween(WallBaseMotion.shortMillis),
                                ) + fadeOut(animationSpec = tween(WallBaseMotion.shortMillis))
                            }
                        } else {
                            fadeOut(animationSpec = snap())
                        }
                    },
                    popEnterTransition = {
                        if (settingsUiState.animationsEnabled) {
                            slideInHorizontally(
                                initialOffsetX = { -(it * 0.25f).toInt() },
                                animationSpec = tween(WallBaseMotion.mediumMillis),
                            ) + fadeIn(animationSpec = tween(WallBaseMotion.mediumMillis))
                        } else {
                            fadeIn(animationSpec = snap())
                        }
                    },
                    popExitTransition = {
                        if (settingsUiState.animationsEnabled) {
                            slideOutHorizontally(
                                targetOffsetX = { (it * 0.25f).toInt() },
                                animationSpec = tween(WallBaseMotion.shortMillis),
                            ) + fadeOut(animationSpec = tween(WallBaseMotion.shortMillis))
                        } else {
                            fadeOut(animationSpec = snap())
                        }
                    },
                ) {
                    composable(RootRoute.Library.route) {
                        val animatedScope = this.takeIf { sharedScope != null }
                        LibraryScreen(
                            onWallpaperSelected = navigateToWallpaperDetail,
                            onConfigureTopBar = acquireTopBar,
                            sharedTransitionScope = sharedScope,
                            animatedVisibilityScope = animatedScope,
                        )
                    }

                    composable(RootRoute.Search.route) {
                        val animatedScope = this.takeIf { sharedScope != null }
                        GlobalSearchScreen(
                            onWallpaperSelected = navigateToWallpaperDetail,
                            onConfigureTopBar = acquireTopBar,
                            sharedTransitionScope = sharedScope,
                            animatedVisibilityScope = animatedScope,
                        )
                    }

                    composable(RootRoute.Albums.route) {
                        AlbumsScreen(
                            onAlbumSelected = { album ->
                                navController.navigateSingleTop("album/${album.id}")
                            },
                            onConfigureTopBar = acquireTopBar,
                        )
                    }

                    composable(RootRoute.Browse.route) {
                        SourcesScreen(
                            uiState = sourcesUiState,
                            onUpdateSourceInput = onUpdateSourceInput,
                            onSearchReddit = onSearchReddit,
                            onAddSourceFromInput = onAddSourceFromInput,
                            onAddRedditCommunity = onAddRedditCommunity,
                            onClearSearchResults = onClearRedditSearch,
                            onOpenSource = { source: Source ->
                                navController.navigateSingleTop(
                                    "sourceBrowse/${Uri.encode(source.key)}",
                                )
                            },
                            onRemoveSource = onRemoveSource,
                            onMessageShown = onSourcesMessageShown,
                            onSourceUrlCopied = onSourceUrlCopied,
                            onOpenRepoScreen = { navController.navigateSingleTop("repositories") },
                            onConfigureTopBar = acquireTopBar,
                        )
                    }

                    composable("repositories") {
                        val extensionsViewModel: ExtensionsViewModel = hiltViewModel()
                        RepoScreen(
                            viewModel = extensionsViewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onConfigureTopBar = acquireTopBar
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
                        val wallpapers = selectedWallpaperState?.wallpapers ?: listOfNotNull(wallpaper)
                        val initialIndex = selectedWallpaperState?.initialIndex ?: 0
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

                            val viewModel: WallpaperDetailViewModel = hiltViewModel()

                            val detailSharedScope =
                                sharedScope.takeIf { enableSharedTransition }
                            val detailVisibilityScope =
                                if (detailSharedScope != null) this else null

                            WallpaperRoute(
                                wallpaper = wallpaper,
                                wallpapers = wallpapers,
                                initialIndex = initialIndex,
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
                            onRequestAppLockChange = handleAppLockToggle,
                            onToggleCategories = onToggleCategories,
                            onToggleAutoDownload = onToggleAutoDownload,
                            onOpenCategories = { navController.navigateSingleTop("settings/categories") },
                            onOpenAppearance = { navController.navigateSingleTop("settings/appearance") },
                            onOpenDataStorage = { navController.navigateSingleTop("settings/data_storage") },
                            onOpenExtensions = { navController.navigateSingleTop("repositories") },
                            onCheckForUpdates = onCheckForUpdates,
                            onDismissAvailableUpdate = onDismissAvailableUpdate,
                            onMessageShown = onSettingsMessageShown,
                            onRestartConsumed = onSettingsRestartConsumed,
                        )
                    }

                    composable("settings/categories") {
                        ManageCategoriesScreen(
                            categories = settingsUiState.categories,
                            onCreateCategory = onCreateCategory,
                            onRenameCategory = onRenameCategory,
                            onDeleteCategory = onDeleteCategory,
                            onReorderCategories = onReorderCategories,
                            onNavigateBack = { navController.popBackStack() },
                            onConfigureTopBar = acquireTopBar,
                        )
                    }

                    composable("settings/appearance") {
                        AppearanceSettingsScreen(
                            uiState = settingsUiState,
                            onSetAppTheme = onSetAppTheme,
                            onSetAppAccentColor = onSetAppAccentColor,
                            onToggleDynamicColor = onToggleDynamicColor,
                            onToggleAmoledDark = onToggleAmoledDark,
                            onToggleAnimations = onToggleAnimations,
                            onToggleShowHorizontalWallpapers = onToggleShowHorizontalWallpapers,
                            onToggleShowDownloadBadge = onToggleShowDownloadBadge,
                            onNavigateBack = { navController.popBackStack() },
                            onConfigureTopBar = acquireTopBar,
                        )
                    }

                    composable("settings/data_storage") {
                        DataAndStorageSettingsScreen(
                            uiState = settingsUiState,
                            onUpdateStorageLimit = onUpdateStorageLimit,
                            onClearPreviewCache = onClearPreviewCache,
                            onClearOriginals = onClearOriginals,
                            onExportBackup = onExportBackup,
                            onImportBackup = onImportBackup,
                            onToggleIncludeSourcesInBackup = onToggleIncludeSourcesInBackup,
                            onNavigateBack = { navController.popBackStack() },
                            onConfigureTopBar = acquireTopBar,
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

@Composable
private fun AnimatedBottomBarIcon(
    item: RootRoute,
    isSelected: Boolean,
    animationsEnabled: Boolean,
) {
    val scale = remember { Animatable(1f) }
    val translationY = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(isSelected) {
        if (isSelected && animationsEnabled) {
            when (item) {
                RootRoute.Library -> {
                    scale.snapTo(0.72f)
                    translationY.snapTo(-5f)
                    rotation.snapTo(-14f)
                    launch {
                        scale.animateTo(
                            1f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                        )
                    }
                    launch {
                        translationY.animateTo(
                            0f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                        )
                    }
                    launch {
                        rotation.animateTo(
                            0f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                        )
                    }
                }
                RootRoute.Search -> {
                    scale.snapTo(0.75f)
                    translationY.snapTo(-4f)
                    rotation.snapTo(-180f)
                    launch {
                        scale.animateTo(
                            1f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                        )
                    }
                    launch {
                        translationY.animateTo(
                            0f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                        )
                    }
                    launch {
                        rotation.animateTo(
                            0f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                        )
                    }
                }
                RootRoute.Albums -> {
                    scale.snapTo(0.72f)
                    translationY.snapTo(-5f)
                    rotation.snapTo(12f)
                    launch {
                        scale.animateTo(
                            1f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                        )
                    }
                    launch {
                        translationY.animateTo(
                            0f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                        )
                    }
                    launch {
                        rotation.animateTo(
                            0f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                        )
                    }
                }
                RootRoute.Browse -> {
                    scale.snapTo(0.72f)
                    translationY.snapTo(-5f)
                    rotation.snapTo(18f)
                    launch {
                        scale.animateTo(
                            1f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                        )
                    }
                    launch {
                        translationY.animateTo(
                            0f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                        )
                    }
                    launch {
                        rotation.animateTo(
                            0f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                        )
                    }
                }
                RootRoute.Settings -> {
                    scale.snapTo(0.75f)
                    translationY.snapTo(-4f)
                    rotation.snapTo(-120f)
                    launch {
                        scale.animateTo(
                            1f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                        )
                    }
                    launch {
                        translationY.animateTo(
                            0f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                        )
                    }
                    launch {
                        rotation.animateTo(
                            0f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                        )
                    }
                }
            }
        } else {
            scale.snapTo(1f)
            translationY.snapTo(0f)
            rotation.snapTo(0f)
        }
    }

    AnimatedContent(
        targetState = isSelected,
        transitionSpec = {
            if (animationsEnabled) {
                (fadeIn(animationSpec = tween(180, delayMillis = 30)) +
                    scaleIn(initialScale = 0.82f, animationSpec = tween(180)))
                    .togetherWith(
                        fadeOut(animationSpec = tween(120)) +
                            scaleOut(targetScale = 0.82f, animationSpec = tween(120))
                    )
            } else {
                fadeIn(animationSpec = snap()).togetherWith(fadeOut(animationSpec = snap()))
            }
        },
        label = "BottomBarIconContent_${item.name}",
        modifier = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            this.translationY = translationY.value
            rotationZ = rotation.value
        },
    ) { selected ->
        Icon(
            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
            contentDescription = item.label,
            modifier = Modifier.size(24.dp),
        )
    }
}



