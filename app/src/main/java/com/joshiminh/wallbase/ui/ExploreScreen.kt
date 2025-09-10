package com.joshiminh.wallbase.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon

@Composable
fun ExploreScreen(sources: List<Source>) {
    var selectedTab by remember { mutableStateOf(0) }
    val enabledSources = sources.filter { it.enabled }
    if (selectedTab >= enabledSources.size) selectedTab = 0

    Column(Modifier.fillMaxSize()) {
        if (enabledSources.isNotEmpty()) {
            ScrollableTabRow(selectedTabIndex = selectedTab) {
                enabledSources.forEachIndexed { index, source ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(source.title) },
                        icon = {
                            Icon(
                                painter = painterResource(id = source.icon),
                                contentDescription = source.title,
                                tint = Color.Unspecified
                            )
                        }
                    )
                }
            }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Content for ${enabledSources[selectedTab].title}",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Enable a source to explore wallpapers",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
