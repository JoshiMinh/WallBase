package com.joshiminh.wallbase.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joshiminh.wallbase.sources.google_photos.GooglePhotosAlbum
import com.joshiminh.wallbase.sources.google_photos.GooglePhotosMediaItem
import com.joshiminh.wallbase.sources.google_photos.fetchGooglePhotosAlbums
import com.joshiminh.wallbase.sources.google_photos.fetchGooglePhotosMediaItems
import com.joshiminh.wallbase.util.userFacingMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun GooglePhotosAlbumPickerScreen(
    token: String,
    onAlbumPicked: (GooglePhotosAlbum) -> Unit
) {
    var albums by remember { mutableStateOf<List<GooglePhotosAlbum>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectionInProgress by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(token) {
        loading = true
        errorMessage = null
        selectionInProgress = false
        albums = emptyList()
        if (token.isBlank()) {
            loading = false
            errorMessage = "Missing Google Photos authentication token."
            return@LaunchedEffect
        }

        try {
            albums = fetchGooglePhotosAlbums(token)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            errorMessage = error.userFacingMessage("Failed to load Google Photos albums.")
        } finally {
            loading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            albums.isEmpty() -> {
                val message = errorMessage ?: "No albums found in Google Photos."
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (errorMessage != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }

            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(albums) { album ->
                        Text(
                            text = buildAlbumLabel(album),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !selectionInProgress) {
                                    scope.launch {
                                        selectionInProgress = true
                                        errorMessage = null
                                        try {
                                            val items: List<GooglePhotosMediaItem> =
                                                fetchGooglePhotosMediaItems(token, album.id, pageSize = 40)
                                            if (items.none { it.mimeType?.startsWith("image/") == true }) {
                                                errorMessage =
                                                    "No photos were found in \"${album.title}\"."
                                            } else {
                                                onAlbumPicked(album)
                                            }
                                        } catch (cancellation: CancellationException) {
                                            selectionInProgress = false
                                            throw cancellation
                                        } catch (error: Exception) {
                                            errorMessage =
                                                error.userFacingMessage("Failed to load album contents.")
                                        } finally {
                                            selectionInProgress = false
                                        }
                                    }
                                }
                                .padding(16.dp)
                        )
                    }
                }
            }
        }

        if (albums.isNotEmpty() && errorMessage != null && !loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        if (selectionInProgress && !loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

private fun buildAlbumLabel(album: GooglePhotosAlbum): String {
    val count = album.mediaItemsCount
    return if (count == null || count <= 0) {
        album.title
    } else {
        "${album.title} ($count)"
    }
}
