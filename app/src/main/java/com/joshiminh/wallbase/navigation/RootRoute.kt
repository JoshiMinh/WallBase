package com.joshiminh.wallbase.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class RootRoute(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Library("library", "Library", Icons.Filled.Collections, Icons.Outlined.Collections),
    Browse("browse", "Browse", Icons.Filled.Explore, Icons.Outlined.Explore),
    Settings("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings);

    val icon: ImageVector get() = unselectedIcon
}
