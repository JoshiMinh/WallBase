package com.joshiminh.wallbase.ui.explore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Explore screen displaying wallpapers grouped by source.
 * Tapping a wallpaper opens a preview dialog with common actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen() {
    val sources = listOf("Photos", "Drive")
    var selectedTab by remember { mutableStateOf(0) }
    var previewUrl by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Explore") },
                    actions = {
                        IconButton(onClick = { /* TODO search */ }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                    }
                )
                ScrollableTabRow(selectedTabIndex = selectedTab) {
                    sources.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        val items = remember(selectedTab) {
            // Placeholder images for each source
            List(20) { "https://picsum.photos/300/600?random=${'$'}selectedTab${'$'}it" }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 128.dp),
            modifier = Modifier.padding(padding)
        ) {
            items(items) { url ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(4.dp)
                        .aspectRatio(9f / 16f)
                        .clickable { previewUrl = url }
                )
            }
        }
    }

    if (previewUrl != null) {
        Dialog(onDismissRequest = { previewUrl = null }) {
            Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 6.dp) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = previewUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(9f / 16f)
                    )
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { /* TODO save */ }) { Text("Save to Library") }
                    TextButton(onClick = { /* TODO like */ }) { Text("Like / Unlike") }
                    TextButton(onClick = { /* TODO album */ }) { Text("Add to Album") }
                    TextButton(onClick = { /* TODO samsung */ }) { Text("Open in Samsung wallpaper preview") }
                    TextButton(onClick = { previewUrl = null }) { Text("Close") }
                }
            }
        }
    }
}
