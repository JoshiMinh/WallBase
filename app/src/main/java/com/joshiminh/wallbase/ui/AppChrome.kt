package com.joshiminh.wallbase.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import com.joshiminh.wallbase.navigation.RootRoute
import com.joshiminh.wallbase.navigation.TopBarState
import com.joshiminh.wallbase.navigation.isTopDestination

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun BoxScope.AppTopBar(
    showTopBar: Boolean,
    barsProgress: Float,
    topBarState: TopBarState?,
    fallbackTitle: String,
    canNavigateBack: Boolean,
    onNavigateBack: () -> Unit,
) {
if (showTopBar) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = -size.height * (1f - barsProgress)
                    },
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    TopAppBar(
                        modifier = Modifier.fillMaxWidth(),
                        windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                        title = {
                            val overrideState = topBarState
                            val customTitle = overrideState?.titleContent
                            when {
                                customTitle != null -> customTitle()
                                else -> Text(
                                    text = overrideState?.title ?: fallbackTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        },
                        navigationIcon = {
                            val overrideState = topBarState
                            val overrideNav = overrideState?.navigationIcon
                            when {
                                overrideNav != null -> IconButton(onClick = overrideNav.onClick) {
                                    Icon(
                                        imageVector = overrideNav.icon,
                                        contentDescription = overrideNav.contentDescription,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                overrideState != null -> Unit // no nav icon when state provided
                                canNavigateBack -> IconButton(onClick = { onNavigateBack() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                else -> Unit
                            }
                        },
                        actions = { topBarState?.actions?.invoke(this) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            scrolledContainerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                            actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                    topBarState?.bottomContent?.invoke()
                }
            }
        }
}

@Composable
internal fun BoxScope.AppBottomBar(
    currentDestination: NavDestination?,
    topLevelRoutes: List<String>,
    hasAvailableUpdate: Boolean,
    animationsEnabled: Boolean,
    barsProgress: Float,
    onNavigateRoute: (String) -> Unit,
) {
if (currentDestination?.route in topLevelRoutes) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = size.height * (1f - barsProgress)
                    },
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp
                    )
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                    ) {
                        RootRoute.entries.forEach { item ->
                            val isSelected = currentDestination.isTopDestination(item)
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    if (!isSelected) onNavigateRoute(item.route)
                                },
                                icon = {
                                    if (item == RootRoute.Settings && hasAvailableUpdate) {
                                        BadgedBox(
                                            badge = {
                                                Badge(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        ) {
                                            AnimatedBottomBarIcon(
                                                item = item,
                                                isSelected = isSelected,
                                                animationsEnabled = animationsEnabled,
                                            )
                                        }
                                    } else {
                                        AnimatedBottomBarIcon(
                                            item = item,
                                            isSelected = isSelected,
                                            animationsEnabled = animationsEnabled,
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
}
