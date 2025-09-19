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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joshiminh.wallbase.data.wallpapers.WallpaperItem
import com.joshiminh.wallbase.ui.components.WallpaperGrid

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SourceBrowseRoute(
    sourceKey: String,
    onWallpaperSelected: (WallpaperItem) -> Unit,
    onTitleChange: (String?) -> Unit,
    viewModel: SourceBrowseViewModel = viewModel(factory = SourceBrowseViewModel.provideFactory(sourceKey))
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.source?.title) {
        onTitleChange(uiState.source?.title)
    }
    DisposableEffect(Unit) {
        onDispose { onTitleChange(null) }
    }

    SourceBrowseScreen(
        state = uiState,
        onQueryChange = viewModel::updateQuery,
        onClearQuery = viewModel::clearQuery,
        onSearch = viewModel::search,
        onRefresh = viewModel::refresh,
        onWallpaperSelected = onWallpaperSelected
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun SourceBrowseScreen(
    state: SourceBrowseViewModel.SourceBrowseUiState,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onWallpaperSelected: (WallpaperItem) -> Unit
) {
    val source = state.source
    if (source == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = state.errorMessage ?: "Source not available",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshing,
        onRefresh = onRefresh
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (source.description.isNotBlank()) {
            Text(source.description, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(12.dp))
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = { Text("Search this source") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() })
        )
        Spacer(modifier = Modifier.height(8.dp))
        RowActions(
            query = state.query,
            onSearch = onSearch,
            onClearQuery = onClearQuery
        )
        state.errorMessage?.takeIf { state.wallpapers.isEmpty() }?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .pullRefresh(pullRefreshState)
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                state.wallpapers.isEmpty() -> {
                    if (state.errorMessage != null) {
                        ErrorMessage(message = state.errorMessage, onRetry = onRefresh)
                    } else {
                        Text(
                            text = "No wallpapers found.",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.align(Alignment.Center)
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

            PullRefreshIndicator(
                refreshing = state.isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
        state.errorMessage?.takeIf { state.wallpapers.isNotEmpty() }?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun RowActions(
    query: String,
    onSearch: () -> Unit,
    onClearQuery: () -> Unit
) {
    val canClear = query.isNotBlank()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = onSearch) {
            Text("Search")
        }
        if (canClear) {
            TextButton(onClick = onClearQuery) {
                Text("Clear search")
            }
        }
    }
}

@Composable
private fun ErrorMessage(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}
