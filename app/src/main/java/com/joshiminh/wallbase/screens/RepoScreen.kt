package com.joshiminh.wallbase.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joshiminh.wallbase.navigation.TopBarHandle
import com.joshiminh.wallbase.navigation.TopBarState
import com.joshiminh.wallbase.scraper.repository.ExtensionRepositoryManager
import com.joshiminh.wallbase.ui.components.bottomBarInsetPadding
import com.joshiminh.wallbase.ui.components.topBarInsetPadding
import com.joshiminh.wallbase.ui.theme.WallBaseShapes
import com.joshiminh.wallbase.ui.theme.WallBaseSpacing
import com.joshiminh.wallbase.ui.viewmodel.ExtensionsViewModel
import kotlinx.coroutines.launch

@Composable
fun RepoScreen(
    viewModel: ExtensionsViewModel,
    onNavigateBack: () -> Unit,
    onConfigureTopBar: (TopBarState) -> TopBarHandle,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    var showAddRepoDialog by remember { mutableStateOf(false) }
    var repoToEdit by remember { mutableStateOf<String?>(null) }
    var repoToDelete by remember { mutableStateOf<String?>(null) }

    val topBarState = remember {
        TopBarState(
            title = "Repositories",
            navigationIcon = TopBarState.NavigationIcon(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onNavigateBack
            ),
            actions = {
                IconButton(onClick = { showAddRepoDialog = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Add repository"
                    )
                }
            }
        )
    }

    val topBarHandleState = remember { mutableStateOf<TopBarHandle?>(null) }
    SideEffect {
        val handle = topBarHandleState.value
        if (handle == null) {
            topBarHandleState.value = onConfigureTopBar(topBarState)
        } else {
            handle.update(topBarState)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            topBarHandleState.value?.clear()
            topBarHandleState.value = null
        }
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = WallBaseSpacing.md,
                top = topBarInsetPadding(8.dp, hasTabBar = false),
                end = WallBaseSpacing.md,
                bottom = bottomBarInsetPadding(16.dp, hasBottomNav = false)
            ),
            verticalArrangement = Arrangement.spacedBy(WallBaseSpacing.md)
        ) {
            if (state.subscribedRepos.isEmpty() && !state.isLoading) {
                item {
                    Card(
                        shape = WallBaseShapes.card,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(WallBaseSpacing.xl),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(WallBaseSpacing.sm)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Extension,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "No repositories added.",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Tap the + button in the top bar to add a repo.json repository URL.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            } else {
                items(state.subscribedRepos.toList(), key = { it }) { repoUrl ->
                    RepoCard(
                        repoUrl = repoUrl,
                        onEdit = { repoToEdit = repoUrl },
                        onOpen = {
                            runCatching { uriHandler.openUri(repoUrl) }
                                .onFailure {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Unable to open URL in browser")
                                    }
                                }
                        },
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(repoUrl))
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Repository URL copied to clipboard")
                            }
                        },
                        onDelete = { repoToDelete = repoUrl }
                    )
                }
            }

            if (state.isLoading && state.subscribedRepos.isEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(WallBaseSpacing.md),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        )
    }

    if (showAddRepoDialog) {
        AddRepoDialog(
            onDismiss = { showAddRepoDialog = false },
            onAdd = { url ->
                viewModel.addRepository(url)
                showAddRepoDialog = false
            }
        )
    }

    repoToEdit?.let { oldUrl ->
        EditRepoDialog(
            currentUrl = oldUrl,
            onDismiss = { repoToEdit = null },
            onSave = { newUrl ->
                viewModel.editRepository(oldUrl, newUrl)
                repoToEdit = null
            }
        )
    }

    repoToDelete?.let { url ->
        AlertDialog(
            onDismissRequest = { repoToDelete = null },
            title = { Text("Delete Repository?") },
            text = {
                Text("Are you sure you want to remove this repository? Its available sources will no longer appear.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeRepository(url)
                        repoToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { repoToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun RepoCard(
    repoUrl: String,
    onEdit: () -> Unit,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val isDefault = repoUrl == ExtensionRepositoryManager.DEFAULT_COMMUNITY_REPO
    val repoName = if (isDefault) "Official Community Repository" else "Custom Repository"

    Card(
        shape = WallBaseShapes.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WallBaseSpacing.md),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WallBaseSpacing.xs)
            ) {
                Text(
                    text = repoName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isDefault) {
                    Surface(
                        shape = WallBaseShapes.pill,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Default",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Text(
                text = repoUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Quick actions: Delete, Edit, Open, Copy URL — all neutral
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete repository",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit repository URL",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = onOpen) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = "Open repository URL in browser",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = onCopy) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy repository URL",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddRepoDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var urlText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Repository") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enter a direct URL to a repo.json manifest repository:",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("Repository URL") },
                    placeholder = { Text("https://raw.githubusercontent.com/.../repo.json") },
                    singleLine = true,
                    shape = WallBaseShapes.control,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(urlText.trim()) },
                enabled = urlText.isNotBlank(),
                shape = WallBaseShapes.pill
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EditRepoDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var urlText by remember(currentUrl) { mutableStateOf(currentUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Repository") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Update the repository manifest URL:",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("Repository URL") },
                    singleLine = true,
                    shape = WallBaseShapes.control,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(urlText.trim()) },
                enabled = urlText.isNotBlank(),
                shape = WallBaseShapes.pill
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
