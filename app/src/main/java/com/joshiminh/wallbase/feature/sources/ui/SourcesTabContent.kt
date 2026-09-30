package com.joshiminh.wallbase.feature.sources.ui

import android.annotation.SuppressLint
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.joshiminh.wallbase.data.entity.Source
import com.joshiminh.wallbase.data.entity.SourceKeys
import com.joshiminh.wallbase.data.repository.SourceRepository
import com.joshiminh.wallbase.navigation.TopBarHandle
import com.joshiminh.wallbase.navigation.TopBarState
import com.joshiminh.wallbase.scraper.model.ExtensionRepoItem
import com.joshiminh.wallbase.sources.RedditCommunity
import com.joshiminh.wallbase.ui.components.TopBarSearchField
import com.joshiminh.wallbase.ui.components.bottomBarInsetPadding
import com.joshiminh.wallbase.ui.components.longPressReorderHandle
import com.joshiminh.wallbase.ui.components.topBarInsetPadding
import com.joshiminh.wallbase.ui.theme.WallBaseShapes
import androidx.hilt.navigation.compose.hiltViewModel
import com.joshiminh.wallbase.ui.theme.WallBaseSpacing
import com.joshiminh.wallbase.ui.viewmodel.ExtensionsViewModel
import com.joshiminh.wallbase.feature.sources.viewmodel.SourcesViewModel
import java.util.Locale
@Composable
internal fun InstalledTabContent(
    sources: List<Source>,
    isSearching: Boolean,
    searchQuery: String,
    isMultiSelectMode: Boolean,
    selectedSourceKeys: Set<String>,
    onToggleSelection: (Source) -> Unit,
    onOpenSource: (Source) -> Unit,
    onRequestRemove: (Source) -> Unit,
    onReorderSources: ((List<Source>) -> Unit)?,
    onSourceUrlCopied: (String) -> Unit,
    onGoToAvailable: () -> Unit,
    onAddSourceClick: () -> Unit
) {
    if (sources.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topBarInsetPadding(8.dp, hasTabBar = true), bottom = bottomBarInsetPadding(16.dp, hasBottomNav = true))
                .padding(horizontal = WallBaseSpacing.lg),
            contentAlignment = Alignment.Center
        ) {
            if (isSearching) {
                Text(
                    text = "No installed sources match \"$searchQuery\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(WallBaseSpacing.md)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Source,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxSize()
                        )
                    }
                    Text(
                        text = "No sources installed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Browse available community sources or add custom feeds like subreddits.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(WallBaseSpacing.sm)) {
                        Button(
                            onClick = onGoToAvailable,
                            shape = WallBaseShapes.pill
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Browse Available")
                        }
                        OutlinedButton(
                            onClick = onAddSourceClick,
                            shape = WallBaseShapes.pill
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Custom")
                        }
                    }
                }
            }
        }
        return
    }

    val lazyListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var localSources by remember { mutableStateOf(sources) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var pendingOrderKeys by remember { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(sources) {
        val incomingKeys = sources.map(Source::key)
        val pending = pendingOrderKeys
        if (draggingKey == null && (pending == null || incomingKeys == pending || incomingKeys.toSet() != pending.toSet())) {
            localSources = sources
            pendingOrderKeys = null
        }
    }

    val onDragStart: (Int, String) -> Unit = { index, key ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        draggingKey = key
        draggingIndex = index
        dragOffsetY = 0f
        pendingOrderKeys = null
    }

    val onDrag: (Float) -> Unit = { deltaY ->
        dragOffsetY += deltaY
        val currentIndex = draggingIndex
        if (currentIndex != null && currentIndex in localSources.indices) {
            val itemInfo = lazyListState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == draggingKey }
            val itemHeight = (itemInfo?.size?.toFloat() ?: 180f) + with(density) { WallBaseSpacing.sm.toPx() }
            val threshold = itemHeight * 0.55f

            if (dragOffsetY > threshold && currentIndex + 1 < localSources.size) {
                val updated = localSources.toMutableList()
                val moved = updated.removeAt(currentIndex)
                updated.add(currentIndex + 1, moved)
                localSources = updated
                dragOffsetY -= itemHeight
                draggingIndex = currentIndex + 1
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            } else if (dragOffsetY < -threshold && currentIndex > 0) {
                val updated = localSources.toMutableList()
                val moved = updated.removeAt(currentIndex)
                updated.add(currentIndex - 1, moved)
                localSources = updated
                dragOffsetY += itemHeight
                draggingIndex = currentIndex - 1
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    val onDragEnd: () -> Unit = {
        val finalSources = localSources
        val wasDragging = draggingKey != null
        draggingKey = null
        draggingIndex = null
        dragOffsetY = 0f
        if (wasDragging && finalSources.map(Source::key) != sources.map(Source::key)) {
            pendingOrderKeys = finalSources.map(Source::key)
            onReorderSources?.invoke(finalSources)
        }
    }

    val onDragCancel: () -> Unit = {
        localSources = sources
        draggingKey = null
        draggingIndex = null
        dragOffsetY = 0f
        pendingOrderKeys = null
    }

    // Auto-scroll when dragging near viewport boundaries
    LaunchedEffect(draggingKey) {
        if (draggingKey == null) return@LaunchedEffect
        while (isActive && draggingKey != null) {
            val itemInfo = lazyListState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == draggingKey }
            if (itemInfo != null) {
                val currentCenter = itemInfo.offset + itemInfo.size / 2 + dragOffsetY
                val viewportHeight = lazyListState.layoutInfo.viewportSize.height
                val topThreshold = 100f
                val bottomThreshold = viewportHeight - 100f
                if (currentCenter < topThreshold) {
                    val scroll = -((topThreshold - currentCenter) / topThreshold * 14f).coerceAtLeast(3f)
                    lazyListState.scrollBy(scroll)
                } else if (currentCenter > bottomThreshold) {
                    val scroll = ((currentCenter - bottomThreshold) / 100f * 14f).coerceAtLeast(3f)
                    lazyListState.scrollBy(scroll)
                }
            }
            delay(16)
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = WallBaseSpacing.md,
            top = topBarInsetPadding(8.dp, hasTabBar = true),
            end = WallBaseSpacing.md,
            bottom = bottomBarInsetPadding(WallBaseSpacing.md, hasBottomNav = true)
        ),
        verticalArrangement = Arrangement.spacedBy(WallBaseSpacing.sm)
    ) {
        itemsIndexed(localSources, key = { _, source -> source.key }) { index, source ->
            val isDragging = source.key == draggingKey
            val isReorderEnabled = !isSearching && !isMultiSelectMode && localSources.size > 1
            Box(
                modifier = Modifier
                    .zIndex(if (isDragging) 10f else 1f)
                    .animateItem(placementSpec = if (isDragging) null else spring())
                    .graphicsLayer {
                        if (isDragging) {
                            translationY = dragOffsetY
                            scaleX = 1.03f
                            scaleY = 1.03f
                            shadowElevation = 16f
                        }
                    }
            ) {
                SourceCard(
                    source = source,
                    isDragging = isDragging,
                    isReorderEnabled = isReorderEnabled,
                    isMultiSelectMode = isMultiSelectMode,
                    isSelected = source.key in selectedSourceKeys,
                    onToggleSelection = { onToggleSelection(source) },
                    onDragStart = { onDragStart(index, source.key) },
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel,
                    onOpenSource = onOpenSource,
                    onRequestRemove = onRequestRemove,
                    onSourceUrlCopied = onSourceUrlCopied
                )
            }
        }
    }
}

@Composable
internal fun AvailableTabContent(
    catalog: List<ExtensionRepoItem>,
    isSearching: Boolean,
    searchQuery: String,
    installedIds: Set<String>,
    installingIds: Set<String>,
    isLoading: Boolean,
    onInstall: (ExtensionRepoItem) -> Unit,
    onUninstall: (ExtensionRepoItem) -> Unit,
    onOpenRepoScreen: () -> Unit
) {
    if (catalog.isEmpty() && !isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topBarInsetPadding(8.dp, hasTabBar = true), bottom = bottomBarInsetPadding(16.dp, hasBottomNav = true))
                .padding(horizontal = WallBaseSpacing.lg),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(WallBaseSpacing.sm)
            ) {
                Text(
                    text = if (isSearching) "No sources match \"$searchQuery\"" else "No community sources found in repositories.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                FilledTonalButton(
                    onClick = onOpenRepoScreen,
                    shape = WallBaseShapes.pill
                ) {
                    Icon(Icons.Outlined.Extension, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Manage Repositories")
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = WallBaseSpacing.md,
            top = topBarInsetPadding(8.dp, hasTabBar = true),
            end = WallBaseSpacing.md,
            bottom = bottomBarInsetPadding(WallBaseSpacing.md, hasBottomNav = true)
        ),
        verticalArrangement = Arrangement.spacedBy(WallBaseSpacing.sm)
    ) {
        if (isLoading) {
            item("catalog_loading") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(WallBaseSpacing.md),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }

        items(catalog, key = { it.id }) { item ->
            val isInstalled = item.id in installedIds ||
                    installedIds.any { it.equals(item.id, ignoreCase = true) }
            val isInstalling = item.id in installingIds

            AvailableSourceCard(
                item = item,
                isInstalled = isInstalled,
                isInstalling = isInstalling,
                onInstall = { onInstall(item) },
                onUninstall = { onUninstall(item) }
            )
        }
    }
}

@Composable
private fun AvailableSourceCard(
    item: ExtensionRepoItem,
    isInstalled: Boolean,
    isInstalling: Boolean = false,
    onInstall: () -> Unit,
    onUninstall: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(WallBaseShapes.card)
            .clickable(enabled = !isInstalling) {
                if (isInstalled) onUninstall() else onInstall()
            },
        shape = WallBaseShapes.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WallBaseSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WallBaseSpacing.sm)
        ) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!item.iconUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.iconUrl,
                        contentDescription = item.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val authorText = item.author?.takeIf { it.isNotBlank() }?.let { "by $it" }
                val metaText = listOfNotNull(authorText, "v${item.version}").joinToString(" Â· ")
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val displayUrl = extensionDisplayUrl(item)
                Text(
                    text = displayUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            IconButton(
                onClick = if (isInstalled) onUninstall else onInstall,
                enabled = !isInstalling
            ) {
                if (isInstalling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Icon(
                        imageVector = if (isInstalled) Icons.Outlined.Check else Icons.Outlined.Download,
                        contentDescription = if (isInstalled) "Uninstall extension" else "Install extension",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun resolveSourceIconUrl(source: Source): String? {
    if (!source.iconUrl.isNullOrBlank() && !source.iconUrl.contains("images.alphacoders.com")) {
        return source.iconUrl
    }
    val extId = source.config ?: source.key.removePrefix("${SourceKeys.EXTENSION}:")
    val domain = when (source.providerKey) {
        SourceKeys.REDDIT -> "reddit.com"
        SourceKeys.PINTEREST -> "pinterest.com"
        SourceKeys.WALLHAVEN -> "wallhaven.cc"
        SourceKeys.EXTENSION -> when (extId.lowercase(Locale.ROOT)) {
            "alphacoders" -> "alphacoders.com"
            "pexels" -> "pexels.com"
            "pixiv" -> "pixiv.net"
            "safebooru" -> "safebooru.org"
            "unsplash" -> "unsplash.com"
            "wallhaven" -> "wallhaven.cc"
            "reddit" -> "reddit.com"
            "pinterest" -> "pinterest.com"
            else -> null
        }
        SourceKeys.WEBSITES -> source.config?.let { runCatching { java.net.URL(it).host }.getOrNull() }
        else -> null
    }
    return domain?.let { "https://www.google.com/s2/favicons?sz=128&domain=${it.removePrefix("www.")}" }
}

@Composable
private fun SourceCard(
    source: Source,
    isDragging: Boolean = false,
    isReorderEnabled: Boolean = false,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onOpenSource: (Source) -> Unit,
    onRequestRemove: (Source) -> Unit,
    onSourceUrlCopied: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val shareUrl = sourceShareUrl(source)
    val resolvedIcon = resolveSourceIconUrl(source)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(WallBaseShapes.card)
            .then(
                if (isReorderEnabled) {
                    Modifier.longPressReorderHandle(
                        key = source.key,
                        onDragStart = onDragStart,
                        onDrag = { onDrag(it.y) },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragCancel,
                    )
                } else Modifier
            )
            .combinedClickable(
                onClick = {
                    if (!isDragging) {
                        if (isMultiSelectMode) onToggleSelection() else onOpenSource(source)
                    }
                }
            ),
        shape = WallBaseShapes.card,
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        border = BorderStroke(
            if (isDragging) 1.5.dp else 1.dp,
            if (isDragging) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WallBaseSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WallBaseSpacing.sm)
        ) {
            if (isMultiSelectMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onToggleSelection() })
            }

            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!resolvedIcon.isNullOrBlank()) {
                    AsyncImage(
                        model = resolvedIcon,
                        contentDescription = source.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else if (source.iconRes != null && source.iconRes != 0) {
                    val painter = safePainterResource(source.iconRes)
                    if (painter != null) {
                        Image(
                            painter = painter,
                            contentDescription = source.title,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val displayUrl = sourceDisplayUrl(source)
                Text(
                    text = displayUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            var menuExpanded by remember(source.key) { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "More source actions")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (shareUrl != null) {
                        DropdownMenuItem(
                            text = { Text("Copy link") },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                            onClick = {
                                clipboardManager.setText(AnnotatedString(shareUrl))
                                onSourceUrlCopied(shareUrl)
                                menuExpanded = false
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Remove source") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = {
                            onRequestRemove(source)
                            menuExpanded = false
                        }
                    )
                }
            }
        }
    }
}

private fun sourceDisplayUrl(source: Source): String {
    val config = source.config?.trim()
    if (!config.isNullOrBlank()) {
        if (config.startsWith("http://") || config.startsWith("https://")) {
            return config
        }
        if (source.providerKey == SourceKeys.REDDIT) {
            val slug = config.trim('/')
            return if (slug.isNotBlank()) "https://reddit.com/r/$slug" else "https://reddit.com"
        }
        return when (config.lowercase(Locale.ROOT)) {
            "alphacoders" -> "https://alphacoders.com"
            "pexels" -> "https://pexels.com"
            "pinterest" -> "https://pinterest.com"
            "pixiv" -> "https://pixiv.net"
            "safebooru" -> "https://safebooru.org"
            "unsplash" -> "https://unsplash.com"
            "wallhaven" -> "https://wallhaven.cc"
            "reddit" -> "https://reddit.com"
            else -> if (config.contains(".")) "https://$config" else "https://$config.com"
        }
    }
    return when (source.providerKey) {
        SourceKeys.REDDIT -> "https://reddit.com"
        SourceKeys.WALLHAVEN -> "https://wallhaven.cc"
        SourceKeys.PINTEREST -> "https://pinterest.com"
        else -> "https://${source.providerKey}.com"
    }
}

private fun extensionDisplayUrl(item: ExtensionRepoItem): String {
    val key = item.id.lowercase(Locale.ROOT)
    return when {
        key.contains("alphacoders") -> "https://alphacoders.com"
        key.contains("pexels") -> "https://pexels.com"
        key.contains("pinterest") -> "https://pinterest.com"
        key.contains("pixiv") -> "https://pixiv.net"
        key.contains("safebooru") -> "https://safebooru.org"
        key.contains("unsplash") -> "https://unsplash.com"
        key.contains("wallhaven") -> "https://wallhaven.cc"
        key.contains("reddit") -> "https://reddit.com"
        else -> "https://$key.com"
    }
}

private fun providerLabel(providerKey: String): String = when (providerKey) {
    SourceKeys.REDDIT -> "Reddit"
    SourceKeys.PINTEREST -> "Pinterest"
    SourceKeys.WALLHAVEN -> "Wallhaven"
    SourceKeys.WEBSITES -> "Website"
    SourceKeys.EXTENSION -> "Extension"
    else -> providerKey.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
}
