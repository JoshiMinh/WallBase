package com.joshiminh.wallbase

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.joshiminh.wallbase.ui.theme.WallBaseTheme

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Explore : Screen("explore", "Explore", Icons.Filled.Explore)
    data object Library : Screen("library", "Library", Icons.Filled.Collections)
    data object Sources : Screen("sources", "Sources", Icons.Filled.Dns)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WallBaseTheme {
                val navController = rememberNavController()
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = { BottomBar(navController) }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Explore.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Explore.route) { ExploreScreen() }
                        composable(Screen.Library.route) { LibraryScreen() }
                        composable(Screen.Sources.route) { SourcesScreen() }
                        composable(Screen.Settings.route) { SettingsScreen() }
                    }
                }
            }
        }
    }
}

@Composable
fun BottomBar(navController: NavHostController) {
    val items = listOf(Screen.Explore, Screen.Library, Screen.Sources, Screen.Settings)
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        items.forEach { screen ->
            NavigationBarItem(
                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(screen.icon, contentDescription = screen.title) },
                label = { Text(screen.title) }
            )
        }
    }
}

@Composable
fun ExploreScreen() {
    Text(text = "Explore")
}

@Composable
fun LibraryScreen() {
    Text(text = "Library")
}

@Composable
fun SourcesScreen() {
    val sources = listOf(
        SourceOption(
            name = "Google Photos",
            description = "Login and pick albums."
        ),
        SourceOption(
            name = "Google Drive",
            description = "Login and pick folders."
        ),
        SourceOption(
            name = "Reddit",
            description = "Add subs, set sort/time, filter resolution."
        ),
        SourceOption(
            name = "Websites",
            description = "Add template or custom scrape rule."
        )
    )

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(sources) { source ->
            SourceCard(source)
        }
    }
}

@Composable
fun SettingsScreen() {
    Text(text = "Settings")
}

data class SourceOption(val name: String, val description: String)

@Composable
fun SourceCard(source: SourceOption) {
    var enabled by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = source.name, style = MaterialTheme.typography.titleMedium)
                Text(text = source.description, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    WallBaseTheme {
        val navController = rememberNavController()
        Scaffold(bottomBar = { BottomBar(navController) }) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Explore.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Explore.route) { ExploreScreen() }
                composable(Screen.Library.route) { LibraryScreen() }
                composable(Screen.Sources.route) { SourcesScreen() }
                composable(Screen.Settings.route) { SettingsScreen() }
            }
        }
    }
}

