package com.joshiminh.wallbase.screens

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.joshiminh.wallbase.scraper.model.ExtensionRepoItem
import com.joshiminh.wallbase.scraper.model.SourceManifest
import com.joshiminh.wallbase.ui.components.ScraperDebuggerBottomSheet
import com.joshiminh.wallbase.ui.theme.WallBaseShapes
import com.joshiminh.wallbase.ui.theme.WallBaseSpacing
import com.joshiminh.wallbase.ui.viewmodel.ExtensionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(
    viewModel: ExtensionsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showDebuggerSheet by remember { mutableStateOf(false) }
    var showAddRepoDialog by remember { mutableStateOf(false) }
    var showPasteJsonDialog by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importFromFileUri(uri, context)
        }
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(WallBaseSpacing.xs)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Extension,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text("Extensions & Sources", style = MaterialTheme.typography.titleLarge)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDebuggerSheet = true }) {
                        Icon(
                            imageVector = Icons.Outlined.BugReport,
                            contentDescription = "Scraper Rule Debugger"
                        )
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PrimaryTabRow(
                selectedTabIndex = state.selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = state.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("Installed (${state.installedExtensions.size})") }
                )
                Tab(
                    selected = state.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text("Community Catalog") }
                )
                Tab(
                    selected = state.selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    text = { Text("Repositories & Import") }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (state.selectedTab) {
                    0 -> InstalledExtensionsTab(
                        installed = state.installedExtensions,
                        onUninstall = viewModel::uninstallExtension
                    )
                    1 -> CommunityCatalogTab(
                        catalog = state.communityCatalog,
                        installedIds = state.installedExtensions.map { it.id }.toSet(),
                        isLoading = state.isLoading,
                        onInstall = viewModel::installFromCatalog
                    )
                    2 -> RepositoriesAndImportTab(
                        repos = state.subscribedRepos,
                        onAddRepo = { showAddRepoDialog = true },
                        onRemoveRepo = viewModel::removeRepository,
                        onImportFile = { filePicker.launch(arrayOf("application/json", "text/*")) },
                        onPasteJson = { showPasteJsonDialog = true }
                    )
                }

                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.Center)
                    )
                }
            }
        }
    }

    if (showDebuggerSheet) {
        ScraperDebuggerBottomSheet(
            onDismiss = { showDebuggerSheet = false },
            onTestScraper = viewModel::testScraper,
            wallpapers = state.testWallpapers,
            errorMessage = state.testError,
            isLoading = state.isTestingScraper,
            onClearResults = viewModel::clearTestResults
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

    if (showPasteJsonDialog) {
        PasteJsonDialog(
            onDismiss = { showPasteJsonDialog = false },
            onImport = { json ->
                viewModel.importFromRawJson(json)
                showPasteJsonDialog = false
            }
        )
    }
}

@Composable
private fun InstalledExtensionsTab(
    installed: List<SourceManifest>,
    onUninstall: (String) -> Unit
) {
    if (installed.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No extensions installed.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(WallBaseSpacing.md),
        verticalArrangement = Arrangement.spacedBy(WallBaseSpacing.sm),
        modifier = Modifier.fillMaxSize()
    ) {
        items(installed, key = { it.id }) { manifest ->
            Card(
                shape = WallBaseShapes.card,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(WallBaseSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(WallBaseSpacing.md)
                ) {
                    if (!manifest.iconUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = manifest.iconUrl,
                            contentDescription = manifest.name,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(WallBaseShapes.control)
                        )
                    } else {
                        Surface(
                            shape = WallBaseShapes.control,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Source,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .fillMaxSize()
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(WallBaseSpacing.xs)
                        ) {
                            Text(
                                text = manifest.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = WallBaseShapes.pill,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "v${manifest.version}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        manifest.description?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "by ${manifest.author ?: "Community"} • ${manifest.feeds.size} feed(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    IconButton(onClick = { onUninstall(manifest.id) }) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Uninstall extension",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityCatalogTab(
    catalog: List<ExtensionRepoItem>,
    installedIds: Set<String>,
    isLoading: Boolean,
    onInstall: (ExtensionRepoItem) -> Unit
) {
    if (catalog.isEmpty() && !isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No community extensions found in subscribed repositories.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(WallBaseSpacing.md),
        verticalArrangement = Arrangement.spacedBy(WallBaseSpacing.sm),
        modifier = Modifier.fillMaxSize()
    ) {
        items(catalog, key = { it.id }) { item ->
            val isInstalled = item.id in installedIds

            Card(
                shape = WallBaseShapes.card,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(WallBaseSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(WallBaseSpacing.md)
                ) {
                    if (!item.iconUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = item.iconUrl,
                            contentDescription = item.name,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(WallBaseShapes.control)
                        )
                    } else {
                        Surface(
                            shape = WallBaseShapes.control,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .fillMaxSize()
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(WallBaseSpacing.xs)
                        ) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = WallBaseShapes.pill,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "v${item.version}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        item.description?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "by ${item.author ?: "Community"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    if (isInstalled) {
                        OutlinedButton(
                            onClick = { onInstall(item) },
                            shape = WallBaseShapes.pill
                        ) {
                            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Installed")
                        }
                    } else {
                        Button(
                            onClick = { onInstall(item) },
                            shape = WallBaseShapes.pill
                        ) {
                            Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Install")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepositoriesAndImportTab(
    repos: Set<String>,
    onAddRepo: () -> Unit,
    onRemoveRepo: (String) -> Unit,
    onImportFile: () -> Unit,
    onPasteJson: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(WallBaseSpacing.md),
        verticalArrangement = Arrangement.spacedBy(WallBaseSpacing.md),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "Extension Repositories",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(repos.toList(), key = { it }) { repoUrl ->
            Card(
                shape = WallBaseShapes.card,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(WallBaseSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(WallBaseSpacing.sm)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = repoUrl,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = { onRemoveRepo(repoUrl) }) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Remove repository",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        item {
            FilledTonalButton(
                onClick = onAddRepo,
                shape = WallBaseShapes.pill,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add Repository URL")
            }
        }

        item {
            Spacer(Modifier.height(WallBaseSpacing.sm))
            Text(
                text = "Manual Scraper Manifest Import",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(WallBaseSpacing.sm)
            ) {
                OutlinedButton(
                    onClick = onImportFile,
                    shape = WallBaseShapes.control,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.FileUpload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Import .json File")
                }

                OutlinedButton(
                    onClick = onPasteJson,
                    shape = WallBaseShapes.control,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Extension, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Paste JSON")
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
        title = { Text("Add Repository URL") },
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
                    placeholder = { Text("https://example.com/repo.json") },
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
private fun PasteJsonDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var jsonText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste Manifest JSON") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Paste the complete source JSON manifest schema:",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = jsonText,
                    onValueChange = { jsonText = it },
                    label = { Text("Manifest JSON") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = WallBaseShapes.control
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(jsonText.trim()) },
                enabled = jsonText.isNotBlank(),
                shape = WallBaseShapes.pill
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

