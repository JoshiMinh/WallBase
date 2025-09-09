package com.joshiminh.wallbase.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ExploreScreen() {
    // Roadmap: Top App Bar Tabs → one per enabled source
    // Placeholder until you wire real data + TabRow/pager
    Text(
        text = "Explore → Sources: Google Photos, Google Drive, Reddit, Local, Websites, Pinterest",
        modifier = Modifier.padding(PaddingValues(16.dp)),
        style = MaterialTheme.typography.bodyLarge
    )
}