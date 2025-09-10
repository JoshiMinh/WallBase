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
import com.joshiminh.wallbase.data.Source

@Composable
fun ExploreScreen(sources: List<Source>) {
    val tabs = sources.filter { it.enabled && it.showInExplore }
    var selectedTab by remember { mutableStateOf(0) }

    if (tabs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No sources enabled",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(tab.title) }
                    )
                }
            }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Content for ${tabs[selectedTab].title}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
