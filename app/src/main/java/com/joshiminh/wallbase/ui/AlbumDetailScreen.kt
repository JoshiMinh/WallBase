package com.joshiminh.wallbase.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joshiminh.wallbase.TopBarState
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem
import com.joshiminh.wallbase.ui.components.SortMenu
import com.joshiminh.wallbase.ui.components.WallpaperGrid
import com.joshiminh.wallbase.ui.viewmodel.AlbumDetailViewModel
import com.joshiminh.wallbase.ui.sort.WallpaperSortOption

@Composable
fun AlbumDetailRoute(
    albumId: Long,
    onWallpaperSelected: (WallpaperItem) -> Unit,
    onConfigureTopBar: (TopBarState?) -> Unit,
    viewModel: AlbumDetailViewModel = viewModel(factory = AlbumDetailViewModel.provideFactory(albumId))
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val title = uiState.albumTitle ?: "Album"

    LaunchedEffect(title) {
        onConfigureTopBar(TopBarState(title = title))
    }
    DisposableEffect(Unit) {
        onDispose { onConfigureTopBar(null) }
    }

    AlbumDetailScreen(
        state = uiState,
        onWallpaperSelected = onWallpaperSelected,
        onSortChange = viewModel::updateSort
    )
}

@Composable
private fun AlbumDetailScreen(
    state: AlbumDetailViewModel.AlbumDetailUiState,
    onWallpaperSelected: (WallpaperItem) -> Unit,
    onSortChange: (WallpaperSortOption) -> Unit
) {
    when {
        state.isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        state.notFound -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Album not found.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        else -> {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    SortMenu(
                        selectedOption = state.wallpaperSortOption,
                        options = WallpaperSortOption.entries.toList(),
                        optionLabel = { it.label },
                        onOptionSelected = onSortChange,
                        label = "Sort wallpapers"
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (state.wallpapers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "This album doesn't have any wallpapers yet.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    WallpaperGrid(
                        wallpapers = state.wallpapers,
                        onWallpaperSelected = onWallpaperSelected,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }
}
