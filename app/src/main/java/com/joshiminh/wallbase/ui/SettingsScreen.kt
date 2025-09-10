package com.joshiminh.wallbase.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen() {
    val items = listOf(
        "Theme: Follow System (auto-sync dark mode)",
        "Library storage: SQLite only (saved/liked, albums, schedules)",
        "Wallpapers: Apply via Samsung System Preview (planned: set home/lock, cropping)",
        "Search wallpapers",
        "Slide show & scheduled wallpapers",
        "Backup/Restore",
        "About"
    )
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(items) { item ->
            ListItem(
                headlineContent = { Text(item) }
            )
            Divider()
        }
    }
}
