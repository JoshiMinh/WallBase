@file:Suppress("unused", "UnusedVariable")

package com.joshiminh.wallbase.screens

import com.joshiminh.wallbase.navigation.*
import android.Manifest
import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import kotlin.math.absoluteValue
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.rounded.Home
import com.joshiminh.wallbase.ui.components.RenameWallpaperDialog
import com.joshiminh.wallbase.ui.AlbumPickerDialog
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joshiminh.wallbase.ui.theme.WallBaseShapes
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joshiminh.wallbase.data.entity.AlbumItem
import com.joshiminh.wallbase.data.entity.SourceKeys
import com.joshiminh.wallbase.data.entity.WallpaperItem
import com.joshiminh.wallbase.ui.components.WallpaperPreviewImage
import com.joshiminh.wallbase.ui.components.sharedWallpaperTransitionModifier
import com.joshiminh.wallbase.ui.viewmodel.WallpaperDetailViewModel
import com.joshiminh.wallbase.util.wallpapers.WallpaperTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun WallpaperRoute(
    wallpaper: WallpaperItem,
    wallpapers: List<WallpaperItem> = listOf(wallpaper),
    initialIndex: Int = 0,
    onNavigateBack: () -> Unit,
    viewModel: WallpaperDetailViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val safeWallpapers = remember(wallpapers, wallpaper) {
        if (wallpapers.isNotEmpty()) wallpapers else listOf(wallpaper)
    }
    val safeInitialIndex = remember(safeWallpapers, initialIndex, wallpaper) {
        val index = if (initialIndex in safeWallpapers.indices) initialIndex
        else safeWallpapers.indexOfFirst { it.id == wallpaper.id }
        if (index >= 0) index else 0
    }
    val pagerState = rememberPagerState(
        initialPage = safeInitialIndex,
        pageCount = { safeWallpapers.size }
    )

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

    LaunchedEffect(pagerState.currentPage, safeWallpapers) {
        val currentWallpaper = safeWallpapers.getOrNull(pagerState.currentPage) ?: wallpaper
        viewModel.setWallpaper(currentWallpaper)
        viewModel.preloadPalettes(safeWallpapers, pagerState.currentPage)
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

    WallpaperScreen(
        uiState = uiState,
        wallpapers = safeWallpapers,
        pagerState = pagerState,
        onApplyTarget = viewModel::applyWallpaper,
        onConfirmApplyWithoutPreview = viewModel::confirmApplyWithoutPreview,
        onDismissPreviewFallback = viewModel::dismissPreviewFallback,
        onAddToLibrary = viewModel::addToLibrary,
        onSetAlbums = viewModel::setWallpaperAlbums,
        onCreateAlbumAndAdd = viewModel::createAlbumAndAddWallpaper,
        onRemoveFromLibrary = viewModel::removeFromLibrary,
        onDownload = viewModel::downloadWallpaper,
        onRequestRemoveDownload = viewModel::promptRemoveDownload,
        onConfirmRemoveDownload = viewModel::removeDownload,
        onDismissRemoveDownload = viewModel::dismissRemoveDownloadPrompt,
        onRequestPermission = { permissionLauncher.launch(Manifest.permission.SET_WALLPAPER) },
        onRenameWallpaper = viewModel::renameWallpaper,
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope
    )
}

@Deprecated("Use WallpaperRoute instead", ReplaceWith("WallpaperRoute(wallpaper, wallpapers, initialIndex, onNavigateBack, viewModel, sharedTransitionScope, animatedVisibilityScope)"))
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DetailRoute(
    wallpaper: WallpaperItem,
    wallpapers: List<WallpaperItem> = listOf(wallpaper),
    initialIndex: Int = 0,
    onNavigateBack: () -> Unit,
    viewModel: WallpaperDetailViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    WallpaperRoute(
        wallpaper = wallpaper,
        wallpapers = wallpapers,
        initialIndex = initialIndex,
        onNavigateBack = onNavigateBack,
        viewModel = viewModel,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope
    )
}

@Composable
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
fun WallpaperScreen(
    uiState: WallpaperDetailViewModel.WallpaperDetailUiState,
    wallpapers: List<WallpaperItem>,
    pagerState: PagerState,
    onApplyTarget: (WallpaperTarget) -> Unit,
    onConfirmApplyWithoutPreview: () -> Unit,
    onDismissPreviewFallback: () -> Unit,
    onAddToLibrary: () -> Unit,
    onSetAlbums: (Set<Long>) -> Unit = {},
    onCreateAlbumAndAdd: (String) -> Unit = {},
    onRemoveFromLibrary: () -> Unit,
    onDownload: () -> Unit,
    onRequestRemoveDownload: () -> Unit,
    onConfirmRemoveDownload: () -> Unit,
    onDismissRemoveDownload: () -> Unit,
    onRequestPermission: () -> Unit,
    onRenameWallpaper: (String?) -> Unit = {},
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?
) {
    val wallpaper = uiState.wallpaper ?: return
    val context = LocalContext.current
    var showAlbumPicker by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val sharedModifier = Modifier.sharedWallpaperTransitionModifier(
        wallpaper = wallpaper,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope
    )
    val vibrantColor = uiState.palette?.vibrantColor?.let { Color(it) }
    val dominantColor = uiState.palette?.dominantColor?.let { Color(it) }
    var localExtractedColor by remember(wallpaper.id) { mutableStateOf<Color?>(null) }
    val previewBitmap = uiState.editedPreview
    LaunchedEffect(previewBitmap, wallpaper.id) {
        val bitmap = previewBitmap ?: return@LaunchedEffect
        withContext(Dispatchers.Default) {
            val maxDim = 128
            val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
                val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
                val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
                runCatching { android.graphics.Bitmap.createScaledBitmap(bitmap, w, h, false) }.getOrNull()
            } else null
            val target = scaled ?: bitmap
            val p = runCatching { androidx.palette.graphics.Palette.from(target).generate() }.getOrNull()
            if (scaled != null && !scaled.isRecycled) scaled.recycle()
            val rgb = p?.vibrantSwatch?.rgb ?: p?.dominantSwatch?.rgb
            if (rgb != null) {
                localExtractedColor = Color(rgb)
            }
        }
    }
    val targetAccentColor = vibrantColor ?: dominantColor ?: localExtractedColor ?: MaterialTheme.colorScheme.primary
    val dynamicAccentColor by animateColorAsState(
        targetValue = targetAccentColor,
        animationSpec = tween(durationMillis = 350),
        label = "dynamicAccentColor"
    )
    val targetGlowColor = dominantColor ?: vibrantColor ?: localExtractedColor ?: MaterialTheme.colorScheme.primary
    val ambientGlowColor by animateColorAsState(
        targetValue = targetGlowColor,
        animationSpec = tween(durationMillis = 350),
        label = "ambientGlowColor"
    )

    val scrollState = rememberScrollState()
    var isViewModeOpen by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    if (isViewModeOpen) {
        WallpaperViewModeDialog(
            previewBitmap = previewBitmap,
            previewModel = wallpaper.previewModel(),
            title = wallpaper.displayTitle,
            onDismiss = { isViewModeOpen = false }
        )
    }

    if (showRenameDialog) {
        RenameWallpaperDialog(
            wallpaper = wallpaper,
            onConfirm = { newTitle ->
                onRenameWallpaper(newTitle)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 72.dp)
            )
        },
        contentWindowInsets = WindowInsets(left = 0.dp, top = 0.dp, right = 0.dp, bottom = 0.dp)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    pageSpacing = 12.dp,
                    key = { page -> wallpapers.getOrNull(page)?.id ?: page.toString() }
                ) { page ->
                    val item = wallpapers.getOrNull(page)
                    if (item != null) {
                        val isCurrent = page == pagerState.currentPage
                        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val scale = 1f - (pageOffset * 0.05f).coerceIn(0f, 0.1f)
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = 1f - (pageOffset * 0.25f).coerceIn(0f, 0.4f)
                                }
                                .clickable {
                                    if (isCurrent) {
                                        isViewModeOpen = true
                                    }
                                },
                            shape = WallBaseShapes.featured,
                            color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                val previewShape = WallBaseShapes.featured
                                val pageSharedModifier = if (isCurrent) {
                                    sharedModifier
                                } else {
                                    Modifier
                                }
                                Box(
                                    modifier = pageSharedModifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .clip(previewShape)
                                            .background(
                                                Brush.radialGradient(
                                                    colors = listOf(
                                                        (if (isCurrent) ambientGlowColor else MaterialTheme.colorScheme.primary).copy(alpha = 0.35f),
                                                        Color.Transparent
                                                    )
                                                )
                                            )
                                    )
                                    if (isCurrent && previewBitmap != null) {
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
                                            model = item.previewModel(),
                                            contentDescription = item.title,
                                            modifier = Modifier.matchParentSize(),
                                            contentScale = ContentScale.Crop,
                                            clipShape = previewShape
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .clip(previewShape)
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color.Transparent,
                                                        Color.Black.copy(alpha = 0.85f)
                                                    )
                                                )
                                            )
                                    )
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    if (isCurrent) {
                                                        showRenameDialog = true
                                                    }
                                                },
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = item.displayTitle.ifBlank { "Untitled wallpaper" },
                                                style = MaterialTheme.typography.titleLarge,
                                                color = Color.White,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = item.sourceName?.takeIf { it.isNotBlank() } ?: "Unknown source",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.White.copy(alpha = 0.8f)
                                            )
                                        }

                                        if (isCurrent) {
                                            IconButton(
                                                onClick = { showRenameDialog = true },
                                                modifier = Modifier.padding(start = 8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Edit,
                                                    contentDescription = "Rename wallpaper",
                                                    tint = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    shape = CircleShape,
                    tonalElevation = 4.dp,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Share
                IconButton(
                    onClick = {
                        val shareUrl = wallpaper.sourceUrl
                            .takeIf { it.isNotBlank() }
                            ?: wallpaper.imageUrl.takeIf { it.isNotBlank() }
                        if (shareUrl.isNullOrBlank()) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("No link available to share")
                            }
                        } else {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareUrl)
                            }
                            if (context !is Activity) {
                                shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            val chooser = Intent.createChooser(shareIntent, "Share wallpaper")
                            runCatching { context.startActivity(chooser) }
                                .onFailure { error ->
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            error.localizedMessage ?: "Unable to share wallpaper"
                                        )
                                    }
                                }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share wallpaper",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 2. Download (auto-adds to library if not already)
                val downloadEnabled = when {
                    uiState.isDownloaded -> !uiState.isRemovingDownload
                    else -> !uiState.isDownloading && (wallpaper.sourceKey != SourceKeys.LOCAL)
                }
                IconButton(
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
                        uiState.isRemovingDownload || uiState.isDownloading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        uiState.isDownloaded -> {
                            Icon(
                                imageVector = Icons.Outlined.DownloadDone,
                                contentDescription = "Remove download",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = "Download wallpaper",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // 3. Album
                IconButton(
                    onClick = { showAlbumPicker = true },
                    enabled = !uiState.isAddingToAlbum
                ) {
                    if (uiState.isAddingToAlbum) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.PhotoLibrary,
                            contentDescription = "Add to album",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 4. Save (Library Bookmark toggle)
                val saveEnabled = !uiState.isAddingToLibrary && !uiState.isRemovingFromLibrary
                IconButton(
                    onClick = {
                        if (uiState.isInLibrary) {
                            onRemoveFromLibrary()
                        } else {
                            onAddToLibrary()
                        }
                    },
                    enabled = saveEnabled
                ) {
                    when {
                        uiState.isAddingToLibrary || uiState.isRemovingFromLibrary -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        uiState.isInLibrary -> {
                            Icon(
                                imageVector = Icons.Filled.Bookmark,
                                contentDescription = "Remove from library",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Outlined.BookmarkBorder,
                                contentDescription = "Save to library",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = { onApplyTarget(WallpaperTarget.BOTH) },
                    enabled = uiState.hasWallpaperPermission && !uiState.isApplying,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = dynamicAccentColor,
                        contentColor = if (dynamicAccentColor.luminance() > 0.5f) Color.Black else Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    if (uiState.isApplying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = if (dynamicAccentColor.luminance() > 0.5f) Color.Black else Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Applying…")
                    } else {
                        Icon(imageVector = Icons.Outlined.Wallpaper, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Set",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
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

    if (showAlbumPicker) {
        AlbumPickerDialog(
            albums = uiState.albums,
            initialSelectedAlbumIds = uiState.assignedAlbumIds,
            isBusy = uiState.isAddingToAlbum,
            onConfirm = { albumIds ->
                onSetAlbums(albumIds)
                showAlbumPicker = false
            },
            onCreateNew = { title ->
                onCreateAlbumAndAdd(title)
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
private fun AssistiveLoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = WallBaseShapes.card
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
            .clip(WallBaseShapes.card)
            .clickable(enabled = enabled, role = Role.Button) { onClick() },
        shape = WallBaseShapes.card,
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
private fun WallpaperViewModeDialog(
    previewBitmap: android.graphics.Bitmap?,
    previewModel: Any,
    title: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        var zoomScale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }

        val transformState = rememberTransformableState { zoomChange, pan, _ ->
            zoomScale = (zoomScale * zoomChange).coerceIn(1f, 4f)
            if (zoomScale > 1f) {
                offsetX += pan.x
                offsetY += pan.y
                val maxOffset = 300f * (zoomScale - 1f)
                offsetX = offsetX.coerceIn(-maxOffset, maxOffset)
                offsetY = offsetY.coerceIn(-maxOffset, maxOffset)
            } else {
                offsetX = 0f
                offsetY = 0f
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (zoomScale > 1f) {
                                    zoomScale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    zoomScale = 2.5f
                                }
                            }
                        )
                    }
                    .transformable(state = transformState),
                contentAlignment = Alignment.Center
            ) {
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = title,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = zoomScale,
                                scaleY = zoomScale,
                                translationX = offsetX,
                                translationY = offsetY
                            ),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    WallpaperPreviewImage(
                        model = previewModel,
                        contentDescription = title,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = zoomScale,
                                scaleY = zoomScale,
                                translationX = offsetX,
                                translationY = offsetY
                            ),
                        contentScale = ContentScale.Fit,
                        clipShape = RoundedCornerShape(0.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close view mode",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

