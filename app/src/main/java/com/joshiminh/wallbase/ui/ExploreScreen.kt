package com.joshiminh.wallbase.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.joshiminh.wallbase.R

private data class ExploreTab(@DrawableRes val icon: Int, val label: String)

@Composable
fun ExploreScreen() {
    val tabs = remember {
        listOf(
            ExploreTab(R.drawable.google_photos, "Google Photos"),
            ExploreTab(R.drawable.google_drive, "Google Drive"),
            ExploreTab(R.drawable.reddit, "Reddit"),
            ExploreTab(R.drawable.pinterest, "Pinterest")
        )
    }
    var selectedTab by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(tab.label) },
                    icon = {
                        Icon(
                            painter = painterResource(id = tab.icon),
                            contentDescription = tab.label
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
                text = "Content for ${tabs[selectedTab].label}",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
