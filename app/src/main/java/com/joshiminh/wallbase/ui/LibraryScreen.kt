package com.joshiminh.wallbase.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joshiminh.wallbase.data.wallpapers.WallpaperItem
import com.joshiminh.wallbase.ui.components.WallpaperGrid

@Composable
fun LibraryScreen(
    onWallpaperSelected: (WallpaperItem) -> Unit,
    libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
) {
    val uiState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    LibraryContent(
        uiState = uiState,
        onWallpaperSelected = onWallpaperSelected
    )
}

@Composable
private fun LibraryContent(
    uiState: LibraryViewModel.LibraryUiState,
    onWallpaperSelected: (WallpaperItem) -> Unit
) {
    val tabs = listOf("All Wallpapers", "Albums")
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTab) {
            0 -> {
                if (uiState.wallpapers.isEmpty()) {
                    LibraryEmptyState(
                        message = "Your library is empty. Save wallpapers from Browse to see them here."
                    )
                } else {
                    WallpaperGrid(
                        wallpapers = uiState.wallpapers,
                        onWallpaperSelected = onWallpaperSelected,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            else -> {
                LibraryEmptyState(
                    message = "Album support is coming soon."
                )
            }
        }
    }
}

@Composable
private fun LibraryEmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
}
