package com.joshiminh.wallbase.navigation

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.graphics.vector.ImageVector

data class TopBarState(
    val title: String? = null,
    val navigationIcon: NavigationIcon? = null,
    val actions: (@Composable RowScope.() -> Unit)? = null,
    val titleContent: (@Composable () -> Unit)? = null,
) {
    data class NavigationIcon(
        val icon: ImageVector,
        val contentDescription: String?,
        val onClick: () -> Unit,
    )
}

class TopBarHandle internal constructor(
    private val ownerId: Long,
    private val setState: (Long, TopBarState) -> Unit,
    private val clearState: (Long) -> Unit,
) {
    fun update(state: TopBarState) = setState(ownerId, state)
    fun clear() = clearState(ownerId)
}

