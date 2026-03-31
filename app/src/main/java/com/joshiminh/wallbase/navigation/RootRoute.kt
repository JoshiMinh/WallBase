package com.joshiminh.wallbase.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*

enum class RootRoute(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Library("library", "Library", Icons.Outlined.Collections),
    Browse("browse", "Browse", Icons.Outlined.Explore),
    Settings("settings", "Settings", Icons.Outlined.Settings),
}

