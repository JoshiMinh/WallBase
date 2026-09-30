package com.joshiminh.wallbase.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars

@Composable
fun topBarInsetPadding(defaultTop: Dp = 8.dp, hasTabBar: Boolean = false): Dp {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val extraTab = if (hasTabBar) 48.dp else 0.dp
    return statusBarTop + 64.dp + extraTab + defaultTop
}

@Composable
fun bottomBarInsetPadding(extraBottom: Dp = 4.dp, hasBottomNav: Boolean = false): Dp {
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val extraNav = if (hasBottomNav) 80.dp else 0.dp
    return navBarBottom + extraNav + extraBottom
}
