package com.joshiminh.wallbase

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.annotation.DrawableRes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.joshiminh.wallbase.theme.WallBaseTheme
import com.joshiminh.wallbase.ui.ExploreScreen
import com.joshiminh.wallbase.ui.LibraryScreen
import com.joshiminh.wallbase.ui.SettingsScreen
import com.joshiminh.wallbase.ui.Source
import com.joshiminh.wallbase.ui.SourcesScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val systemDark = isSystemInDarkTheme()
            var followSystem by remember { mutableStateOf(true) }
            var darkTheme by remember { mutableStateOf(systemDark) }
            val sources = remember {
                mutableStateListOf(
                    Source("google_photos", R.drawable.google_photos, "Google Photos", "Login, pick albums"),
                    Source("google_drive", R.drawable.google_drive, "Google Drive", "Login, pick folder(s)"),
                    Source("reddit", R.drawable.reddit, "Reddit", "Add subs, sort/time, filters"),
                    Source("pinterest", R.drawable.pinterest, "Pinterest", "(planned)"),
                    Source("websites", android.R.drawable.ic_menu_search, "Websites", "Templates or custom rules"),
                    Source("local", android.R.drawable.ic_menu_gallery, "Local", "Device Photo Picker / SAF")
                )
            }
            val useDarkTheme = if (followSystem) systemDark else darkTheme
            WallBaseTheme(darkTheme = useDarkTheme) {
                WallBaseApp(
                    sources = sources,
                    darkTheme = darkTheme,
                    followSystem = followSystem,
                    onToggleDarkTheme = { darkTheme = it },
                    onToggleFollowSystem = { followSystem = it }
                )
            }
        }
    }
}

/** Top-level routes for bottom navigation */
private enum class RootRoute(
    val route: String,
    val label: String,
    @DrawableRes val icon: Int
) {
    Explore("explore", "Explore", android.R.drawable.ic_menu_search),
    Library("library", "Library", android.R.drawable.ic_menu_gallery),
    Sources("sources", "Sources", android.R.drawable.ic_menu_manage),
    Settings("settings", "Settings", android.R.drawable.ic_menu_preferences)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallBaseApp(
    sources: List<Source>,
    darkTheme: Boolean,
    followSystem: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    onToggleFollowSystem: (Boolean) -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

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
                colors = TopAppBarDefaults.topAppBarColors()
            )
        },
        bottomBar = {
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
                                painter = painterResource(id = item.icon),
                                contentDescription = item.label,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = RootRoute.Explore.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(RootRoute.Explore.route) { ExploreScreen(sources) }
            composable(RootRoute.Library.route) { LibraryScreen() }
            composable(RootRoute.Sources.route) { SourcesScreen(sources) }
            composable(RootRoute.Settings.route) {
                SettingsScreen(
                    darkTheme = darkTheme,
                    followSystem = followSystem,
                    onToggleDarkTheme = onToggleDarkTheme,
                    onToggleFollowSystem = onToggleFollowSystem
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
    else -> "WallBase"
}

private fun NavDestination?.isTopDestination(item: RootRoute): Boolean {
    return this?.hierarchy?.any { it.route == item.route } == true
}