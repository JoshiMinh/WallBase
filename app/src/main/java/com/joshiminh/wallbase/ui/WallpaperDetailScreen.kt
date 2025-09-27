@file:Suppress("unused", "UnusedVariable")

package com.joshiminh.wallbase.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joshiminh.wallbase.data.entity.album.AlbumItem
import com.joshiminh.wallbase.data.entity.source.SourceKeys
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem
import com.joshiminh.wallbase.ui.components.WallpaperPreviewImage
import com.joshiminh.wallbase.ui.viewmodel.WallpaperDetailViewModel
import com.joshiminh.wallbase.util.wallpapers.WallpaperTarget

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun WallpaperDetailRoute(
    wallpaper: WallpaperItem,
    onNavigateBack: () -> Unit,
    onEditWallpaper: () -> Unit,
    viewModel: WallpaperDetailViewModel = viewModel(factory = WallpaperDetailViewModel.Factory),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = viewModel::onWallpaperPermissionResult
    )
    var launchedPreview by remember { mutableStateOf<WallpaperDetailViewModel.WallpaperPreviewLaunch?>(null) }
    val previewLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        launchedPreview?.let { preview ->
            viewModel.onPreviewResult(preview, result.resultCode)
            launchedPreview = null
        }
    }

    LaunchedEffect(wallpaper.id) {
        viewModel.setWallpaper(wallpaper)
    }

    LaunchedEffect(uiState.pendingPreview) {
        val preview = uiState.pendingPreview ?: return@LaunchedEffect
        launchedPreview = preview
        try {
            previewLauncher.launch(preview.preview.intent)
        } catch (throwable: Throwable) {
            launchedPreview = null
            viewModel.onPreviewLaunchFailed(preview, throwable)
        } finally {
            viewModel.onPreviewLaunched()
        }
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    WallpaperDetailScreen(
        uiState = uiState,
        onApplyTarget = viewModel::applyWallpaper,
        onConfirmApplyWithoutPreview = viewModel::confirmApplyWithoutPreview,
        onDismissPreviewFallback = viewModel::dismissPreviewFallback,
        onAddToLibrary = viewModel::addToLibrary,
        onAddToAlbum = viewModel::addToAlbum,
        onRemoveFromLibrary = viewModel::removeFromLibrary,
        onDownload = viewModel::downloadWallpaper,
        onRequestRemoveDownload = viewModel::promptRemoveDownload,
        onConfirmRemoveDownload = viewModel::removeDownload,
        onDismissRemoveDownload = viewModel::dismissRemoveDownloadPrompt,
        onRequestPermission = { permissionLauncher.launch(Manifest.permission.SET_WALLPAPER) },
        onNavigateBack = onNavigateBack,
        onEditWallpaper = onEditWallpaper,
        snackbarHostState = snackbarHostState,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope
    )
}

@Composable
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
private fun WallpaperDetailScreen(
    uiState: WallpaperDetailViewModel.WallpaperDetailUiState,
    onApplyTarget: (WallpaperTarget) -> Unit,
    onConfirmApplyWithoutPreview: () -> Unit,
    onDismissPreviewFallback: () -> Unit,
    onAddToLibrary: () -> Unit,
    onAddToAlbum: (Long) -> Unit,
    onRemoveFromLibrary: () -> Unit,
    onDownload: () -> Unit,
    onRequestRemoveDownload: () -> Unit,
    onConfirmRemoveDownload: () -> Unit,
    onDismissRemoveDownload: () -> Unit,
    onRequestPermission: () -> Unit,
    onNavigateBack: () -> Unit,
    onEditWallpaper: () -> Unit,
    snackbarHostState: SnackbarHostState,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?
) {
    val wallpaper = uiState.wallpaper ?: return
    val uriHandler = LocalUriHandler.current
    val hasSourceUrl = wallpaper.sourceUrl.isNotBlank()
    val canAddToLibrary = wallpaper.sourceKey != null && wallpaper.sourceKey != SourceKeys.LOCAL
    val canRemoveFromLibrary = uiState.isInLibrary && wallpaper.sourceKey != null
    val canDownload = wallpaper.sourceKey != null && wallpaper.sourceKey != SourceKeys.LOCAL
    var showTargetDialog by remember { mutableStateOf(false) }
    var showAlbumPicker by remember { mutableStateOf(false) }
    val aspectRatio = wallpaper.aspectRatio?.takeIf { it > 0f } ?: DEFAULT_DETAIL_ASPECT_RATIO
    val sharedModifier =
        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = wallpaper.transitionKey()),
                    animatedVisibilityScope = animatedVisibilityScope,
                    // pick one:
                    resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds(),
                    // or: resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                    boundsTransform = BoundsTransform { _, _ -> tween(durationMillis = 350) }
                )
            }
        } else {
            Modifier
        }
    val statusMessages = remember(
        uiState.isDownloading,
        uiState.isRemovingDownload,
        uiState.isAddingToLibrary,
        uiState.isRemovingFromLibrary,
        uiState.isAddingToAlbum,
        uiState.isApplying
    ) {
        buildList {
            if (uiState.isDownloading) add("Downloading wallpaper…")
            if (uiState.isRemovingDownload) add("Removing download…")
            if (uiState.isAddingToLibrary) add("Adding to library…")
            if (uiState.isRemovingFromLibrary) add("Removing from library…")
            if (uiState.isAddingToAlbum) add("Adding to album…")
            if (uiState.isApplying) add("Applying wallpaper…")
        }
    }
    val showLibraryAction = canAddToLibrary || canRemoveFromLibrary
    val libraryBusy = uiState.isAddingToLibrary || uiState.isRemovingFromLibrary || uiState.isAddingToAlbum
    val canToggleLibrary = if (uiState.isInLibrary) {
        canRemoveFromLibrary
    } else {
        canAddToLibrary
    }

    val scrollState = rememberScrollState()

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets(left = 0.dp, top = 0.dp, right = 0.dp, bottom = 0.dp)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        val previewBitmap = uiState.editedPreview
                        val previewShape = RoundedCornerShape(24.dp)
                        val previewModifier = sharedModifier.then(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(aspectRatio)
                        )
                        Box(
                            modifier = previewModifier,
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(previewShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                            if (previewBitmap != null) {
                                Image(
                                    bitmap = previewBitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clip(previewShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                WallpaperPreviewImage(
                                    model = wallpaper.previewModel(),
                                    contentDescription = wallpaper.title,
                                    modifier = Modifier.matchParentSize(),
                                    contentScale = ContentScale.Crop,
                                    clipShape = previewShape
                                )
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    shape = CircleShape,
                    tonalElevation = 4.dp,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    shape = CircleShape,
                    tonalElevation = 4.dp,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ) {
                    IconButton(onClick = onEditWallpaper) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit wallpaper"
                        )
                    }
                }

                // Library action now lives beside the title
            }

            if (statusMessages.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    statusMessages.forEach { status ->
                        AssistChip(onClick = {}, enabled = false, label = { Text(text = status) })
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = wallpaper.title.ifBlank { "Untitled wallpaper" },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = wallpaper.sourceName?.takeIf { it.isNotBlank() } ?: "Unknown source",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (showLibraryAction) {
                    LibraryActionButton(
                        inLibrary = uiState.isInLibrary,
                        enabled = !libraryBusy,
                        busy = libraryBusy,
                        onClick = {
                            if (!libraryBusy) {
                                if (uiState.isInLibrary) {
                                    onRemoveFromLibrary()
                                } else {
                                    onAddToLibrary()
                                }
                            }
                        },
                        onLongClick = {
                            if (!libraryBusy) {
                                showAlbumPicker = true
                            }
                        }
                    )
                }
            }

            if (!uiState.hasWallpaperPermission) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Allow WallBase to open the system wallpaper preview so you can confirm the image.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onRequestPermission) {
                        Text(text = "Grant wallpaper access")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val downloadEnabled = when {
                    uiState.isDownloaded -> !uiState.isRemovingDownload
                    else -> canDownload && !uiState.isDownloading
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (uiState.isDownloaded) {
                            onRequestRemoveDownload()
                        } else {
                            onDownload()
                        }
                    },
                    enabled = downloadEnabled
                ) {
                    when {
                        uiState.isRemovingDownload -> {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .width(18.dp)
                                    .height(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Removing…")
                        }

                        uiState.isDownloading -> {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .width(18.dp)
                                    .height(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Downloading…")
                        }

                        uiState.isDownloaded -> {
                            Icon(imageVector = Icons.Outlined.TaskAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Downloaded")
                        }

                        else -> {
                            Icon(imageVector = Icons.Outlined.CloudDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Download")
                        }
                    }
                }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { showTargetDialog = true },
                    enabled = uiState.hasWallpaperPermission && !uiState.isApplying
                ) {
                    if (uiState.isApplying) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .width(18.dp)
                                .height(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Applying…")
                    } else {
                        Icon(imageVector = Icons.Outlined.Wallpaper, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Set")
                    }
                }
            }

            if (hasSourceUrl) {
                TextButton(
                    onClick = { wallpaper.sourceUrl.let(uriHandler::openUri) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text(
                        text = "Open original",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
    if (uiState.showRemoveDownloadConfirmation) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isRemovingDownload) {
                    onDismissRemoveDownload()
                }
            },
            title = { Text(text = "Remove downloaded file?") },
            text = {
                Text(
                    text = "Delete the downloaded copy saved locally for this wallpaper?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirmRemoveDownload,
                    enabled = !uiState.isRemovingDownload
                ) {
                    Text(text = "Remove")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissRemoveDownload,
                    enabled = !uiState.isRemovingDownload
                ) {
                    Text(text = "Cancel")
                }
            }
        )
    }

    if (showTargetDialog) {
        SetWallpaperDialog(
            onDismiss = { showTargetDialog = false },
            onTargetSelected = { target ->
                showTargetDialog = false
                onApplyTarget(target)
            },
            isApplying = uiState.isApplying
        )
    }

    if (showAlbumPicker) {
        AlbumPickerDialog(
            albums = uiState.albums,
            onAlbumSelected = { album ->
                onAddToAlbum(album.id)
                showAlbumPicker = false
            },
            onDismiss = { showAlbumPicker = false }
        )
    }

    uiState.pendingFallback?.let { fallback ->
        PreviewFallbackDialog(
            fallback = fallback,
            isApplying = uiState.isApplying,
            onConfirm = onConfirmApplyWithoutPreview,
            onDismiss = onDismissPreviewFallback
        )
    }
}

private const val DEFAULT_DETAIL_ASPECT_RATIO = 9f / 16f

@Composable
private fun LibraryActionButton(
    inLibrary: Boolean,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(44.dp)
            .combinedClickable(
                enabled = enabled || busy,
                onClick = {
                    if (!busy && enabled) {
                        onClick()
                    }
                },
                onLongClick = {
                    if (!busy) {
                        onLongClick()
                    }
                }
            ),
        shape = CircleShape,
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                busy -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }

                inLibrary -> {
                    Icon(imageVector = Icons.Outlined.TaskAlt, contentDescription = "In library")
                }

                else -> {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "Add to library")
                }
            }
        }
    }
}

@Composable
private fun SetWallpaperDialog(
    onDismiss: () -> Unit,
    onTargetSelected: (WallpaperTarget) -> Unit,
    isApplying: Boolean
) {
    val applyOptions = remember {
        listOf(
            ApplyOption(
                target = WallpaperTarget.HOME,
                title = "Home screen",
                description = "Replace the wallpaper on your home screen.",
                icon = Icons.Rounded.Home
            ),
            ApplyOption(
                target = WallpaperTarget.LOCK,
                title = "Lock screen",
                description = "Show this wallpaper when your device is locked.",
                icon = Icons.Rounded.Lock
            ),
            ApplyOption(
                target = WallpaperTarget.BOTH,
                title = "Both screens",
                description = "Apply everywhere for a consistent look.",
                icon = Icons.Rounded.Wallpaper
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Set wallpaper") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Choose where to apply this wallpaper.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isApplying) {
                    AssistiveLoadingRow()
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    applyOptions.forEach { option ->
                        ApplyOptionCard(
                            option = option,
                            enabled = !isApplying,
                            onClick = { onTargetSelected(option.target) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isApplying) {
                Text(text = "Cancel")
            }
        }
    )
}

@Composable
private fun AssistiveLoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(
            text = "Applying wallpaper…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ApplyOptionCard(
    option: ApplyOption,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.Button) { onClick() },
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = option.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = option.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class ApplyOption(
    val target: WallpaperTarget,
    val title: String,
    val description: String,
    val icon: ImageVector
)

@Composable
private fun PreviewFallbackDialog(
    fallback: WallpaperDetailViewModel.WallpaperPreviewFallback,
    isApplying: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Preview unavailable") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val reason = fallback.reason
                if (reason.isNullOrBlank()) {
                    Text(text = "Your device couldn't open the wallpaper preview.")
                } else {
                    Text(
                        text = "Your device couldn't open the wallpaper preview ($reason)."
                    )
                }
                Text(
                    text = "Apply the wallpaper directly to the ${fallback.target.label}?"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isApplying) {
                Text(text = "Apply without preview")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isApplying) {
                Text(text = "Cancel")
            }
        }
    )
}

@Composable
private fun AlbumPickerDialog(
    albums: List<AlbumItem>,
    onAlbumSelected: (AlbumItem) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Add to album") },
        text = {
            if (albums.isEmpty()) {
                Text(text = "Create an album in your library to start organizing wallpapers.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    albums.forEach { album ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            tonalElevation = 1.dp,
                            onClick = { onAlbumSelected(album) }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = album.title,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${album.wallpaperCount} wallpapers",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}