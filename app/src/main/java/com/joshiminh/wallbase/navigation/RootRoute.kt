package com.joshiminh.wallbase.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.*

enum class RootRoute(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Library("library", "Library", Icons.Outlined.Collections, Icons.Filled.Collections),
    Browse("browse", "Browse", Icons.Outlined.Explore, Icons.Filled.Explore),
    Settings("settings", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
}

