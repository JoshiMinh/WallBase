package com.joshiminh.wallbase.navigation

import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavOptionsBuilder

fun NavDestination?.isTopDestination(item: RootRoute): Boolean =
    this?.hierarchy?.any { it.route == item.route } == true

fun NavController.navigateSingleTop(
    route: String,
    builder: NavOptionsBuilder.() -> Unit = {},
) = navigate(route) {
    launchSingleTop = true
    builder()
}

fun currentTitle(dest: NavDestination?): String = when {
    dest.isTopDestination(RootRoute.Library) -> "Library"
    dest.isTopDestination(RootRoute.Browse) -> "Browse"
    dest.isTopDestination(RootRoute.Settings) -> "Settings"
    dest?.route == "wallpaperDetail" -> "Wallpaper"
    dest?.route == "photosAlbums"   -> "Photos albums"
    dest?.route == "album/{albumId}" -> "Album"
    else -> "WallBase"
}

