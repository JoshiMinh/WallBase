package com.joshiminh.wallbase

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Cloud
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joshiminh.wallbase.data.source.RedditCommunity
import com.joshiminh.wallbase.data.source.Source
import com.joshiminh.wallbase.data.wallpapers.WallpaperItem
import com.joshiminh.wallbase.R
import com.joshiminh.wallbase.di.ServiceLocator
import com.joshiminh.wallbase.theme.WallBaseTheme
import com.joshiminh.wallbase.ui.DriveFolderPickerScreen
import com.joshiminh.wallbase.ui.ExploreScreen
import com.joshiminh.wallbase.ui.LibraryScreen
import com.joshiminh.wallbase.ui.SettingsScreen
import com.joshiminh.wallbase.ui.SourcesScreen
import com.joshiminh.wallbase.ui.SourcesViewModel
import com.joshiminh.wallbase.ui.WallpaperDetailRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceLocator.initialize(applicationContext)
        enableEdgeToEdge()
        setContent {
            var darkTheme by remember { mutableStateOf(false) }
            val sourcesViewModel: SourcesViewModel = viewModel(factory = SourcesViewModel.Factory)
            val sourcesUiState by sourcesViewModel.uiState.collectAsStateWithLifecycle()

            val localImagesPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenMultipleDocuments(),
                onResult = { uris -> sourcesViewModel.importLocalWallpapers(uris) }
            )

            WallBaseTheme(darkTheme = darkTheme) {
                WallBaseApp(
                    sourcesUiState = sourcesUiState,
                    darkTheme = darkTheme,
                    onToggleDarkTheme = { darkTheme = it },
                    onToggleSource = sourcesViewModel::toggleSource,
                    onImportLocalImages = { localImagesPicker.launch(arrayOf("image/*")) },
                    onUpdateRedditQuery = sourcesViewModel::updateRedditQuery,
                    onSearchReddit = sourcesViewModel::searchRedditCommunities,
                    onAddRedditFromQuery = sourcesViewModel::addRedditFromQuery,
                    onAddRedditCommunity = sourcesViewModel::addRedditCommunity,
                    onClearRedditSearch = sourcesViewModel::clearSearchResults,
                    onRemoveSource = sourcesViewModel::removeSource,
                    onSourcesMessageShown = sourcesViewModel::consumeMessage
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
    Explore("explore", "Explore", Icons.Outlined.Explore),
    Library("library", "Library", Icons.Outlined.Collections),
    Sources("sources", "Sources", Icons.Outlined.Cloud),
    Settings("settings", "Settings", Icons.Outlined.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallBaseApp(
    sourcesUiState: SourcesViewModel.SourcesUiState,
    darkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    onToggleSource: (Source, Boolean) -> Unit,
    onImportLocalImages: () -> Unit,
    onUpdateRedditQuery: (String) -> Unit,
    onSearchReddit: () -> Unit,
    onAddRedditFromQuery: () -> Unit,
    onAddRedditCommunity: (RedditCommunity) -> Unit,
    onClearRedditSearch: () -> Unit,
    onRemoveSource: (Source) -> Unit,
    onSourcesMessageShown: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val driveToken = remember { "" }
    val topLevelRoutes = remember { RootRoute.entries.map(RootRoute::route) }
    val canNavigateBack = navController.previousBackStackEntry != null &&
        currentDestination?.route !in topLevelRoutes

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentTitle(currentDestination),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    if (canNavigateBack) {
                        IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back),
                            modifier = Modifier.size(24.dp)
                        )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors()
            )
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
        NavHost(
            navController = navController,
            startDestination = RootRoute.Explore.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(RootRoute.Explore.route) {
                ExploreScreen(
                    sources = sourcesUiState.sources,
                    onWallpaperSelected = { wallpaper ->
                        navController.currentBackStackEntry?.savedStateHandle?.set("wallpaper_detail", wallpaper)
                        navController.navigate("wallpaperDetail") {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(RootRoute.Library.route) { LibraryScreen() }
            composable(RootRoute.Sources.route) {
                SourcesScreen(
                    uiState = sourcesUiState,
                    onToggleSource = onToggleSource,
                    onGoogleDriveClick = { navController.navigate("driveFolders") },
                    onAddLocalWallpapers = onImportLocalImages,
                    onUpdateRedditQuery = onUpdateRedditQuery,
                    onSearchReddit = onSearchReddit,
                    onAddRedditFromQuery = onAddRedditFromQuery,
                    onAddRedditCommunity = onAddRedditCommunity,
                    onClearSearchResults = onClearRedditSearch,
                    onRemoveSource = onRemoveSource,
                    onMessageShown = onSourcesMessageShown
                )
            }
            composable("driveFolders") {
                DriveFolderPickerScreen(
                    token = driveToken,
                    onFolderPicked = { navController.popBackStack() }
                )
            }
            composable("wallpaperDetail") {
                val wallpaper = navController.previousBackStackEntry?.savedStateHandle
                    ?.get<WallpaperItem>("wallpaper_detail")
                if (wallpaper == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    WallpaperDetailRoute(wallpaper = wallpaper)
                    DisposableEffect(Unit) {
                        onDispose {
                            navController.previousBackStackEntry?.savedStateHandle?.remove<WallpaperItem>("wallpaper_detail")
                        }
                    }
                }
            }
            composable(RootRoute.Settings.route) {
                SettingsScreen(
                    darkTheme = darkTheme,
                    onToggleDarkTheme = onToggleDarkTheme
                )
            }
        }
    }
}

private fun currentTitle(dest: NavDestination?): String = when {
    dest.isTopDestination(RootRoute.Explore) -> "Explore"
    dest.isTopDestination(RootRoute.Library) -> "Library"
    dest.isTopDestination(RootRoute.Sources) -> "Sources"
    dest.isTopDestination(RootRoute.Settings) -> "Settings"
    dest?.route == "wallpaperDetail" -> "Wallpaper"
    dest?.route == "driveFolders" -> "Drive folders"
    else -> "WallBase"
}

private fun NavDestination?.isTopDestination(item: RootRoute): Boolean {
    return this?.hierarchy?.any { it.route == item.route } == true
}
