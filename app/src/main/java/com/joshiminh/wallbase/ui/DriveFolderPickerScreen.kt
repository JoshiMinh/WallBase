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
import com.joshiminh.wallbase.sources.google_drive.DriveFolder
import com.joshiminh.wallbase.sources.google_drive.DriveImage
import com.joshiminh.wallbase.sources.google_drive.fetchDriveFolders
import com.joshiminh.wallbase.sources.google_drive.fetchDriveImages
import com.joshiminh.wallbase.util.userFacingMessage
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun DriveFolderPickerScreen(
    token: String,
    onFolderPicked: (DriveFolder) -> Unit
) {
    var folders by remember { mutableStateOf<List<DriveFolder>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectionInProgress by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(token) {
        loading = true
        errorMessage = null
        selectionInProgress = false
        folders = emptyList()
        if (token.isBlank()) {
            loading = false
            errorMessage = "Missing google drive/photos authentication token"
            return@LaunchedEffect
        }

        try {
            folders = fetchDriveFolders(token).sortedBy { it.name.lowercase(Locale.ROOT) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            errorMessage = error.userFacingMessage("Failed to load Drive folders.")
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

            folders.isEmpty() -> {
                val message = errorMessage ?: "No folders found in Google Drive."
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
                    items(folders) { folder ->
                        Text(
                            text = folder.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !selectionInProgress) {
                                    scope.launch {
                                        selectionInProgress = true
                                        errorMessage = null
                                        try {
                                            val images: List<DriveImage> = fetchDriveImages(token, folder.id)
                                            if (images.isEmpty()) {
                                                errorMessage = "No images were found in \"${folder.name}\"."
                                            } else {
                                                onFolderPicked(folder)
                                            }
                                        } catch (cancellation: CancellationException) {
                                            selectionInProgress = false
                                            throw cancellation
                                        } catch (error: Exception) {
                                            errorMessage = error.userFacingMessage("Failed to load folder contents.")
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

        if (folders.isNotEmpty() && errorMessage != null && !loading) {
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
