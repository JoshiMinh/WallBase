package com.joshiminh.wallbase.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.joshiminh.wallbase.data.wallpapers.WallpaperItem
import com.joshiminh.wallbase.ui.components.WallpaperGrid

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
        onWallpaperSelected = onWallpaperSelected
    )
}

@Composable
private fun AlbumDetailScreen(
    state: AlbumDetailViewModel.AlbumDetailUiState,
    onWallpaperSelected: (WallpaperItem) -> Unit
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

        state.wallpapers.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "This album doesn't have any wallpapers yet.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        else -> {
            WallpaperGrid(
                wallpapers = state.wallpapers,
                onWallpaperSelected = onWallpaperSelected,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
