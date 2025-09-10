package com.joshiminh.wallbase

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.joshiminh.wallbase.ui.SourcesScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WallBaseTheme {
                WallBaseApp()
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
fun WallBaseApp() {
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = RootRoute.Explore.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(RootRoute.Explore.route) { ExploreScreen() }
            composable(RootRoute.Library.route) { LibraryScreen() }
            composable(RootRoute.Sources.route) { SourcesScreen() }
            composable(RootRoute.Settings.route) { SettingsScreen() }
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