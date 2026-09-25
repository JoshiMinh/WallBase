package com.joshiminh.wallbase.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class RootRoute(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Library("library", "Library", Icons.Filled.Collections, Icons.Outlined.Collections),
    Search("search", "Browse", Icons.Filled.Explore, Icons.Outlined.Explore),
    Albums("albums", "Albums", Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary),
    Browse("browse", "Sources", Icons.Filled.Extension, Icons.Outlined.Extension),
    Settings("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings);

    val icon: ImageVector get() = unselectedIcon
}
