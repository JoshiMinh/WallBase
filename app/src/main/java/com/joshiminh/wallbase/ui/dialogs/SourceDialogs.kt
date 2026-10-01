package com.joshiminh.wallbase.ui.dialogs

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
import com.joshiminh.wallbase.domain.model.Source
import com.joshiminh.wallbase.domain.model.SourceKeys
import com.joshiminh.wallbase.data.repository.SourceRepository
import com.joshiminh.wallbase.ui.navigation.TopBarHandle
import com.joshiminh.wallbase.ui.navigation.TopBarState
import com.joshiminh.wallbase.scraper.model.ExtensionRepoItem
import com.joshiminh.wallbase.data.sources.RedditCommunity
import com.joshiminh.wallbase.ui.components.TopBarSearchField
import com.joshiminh.wallbase.ui.components.bottomBarInsetPadding
import com.joshiminh.wallbase.ui.components.longPressReorderHandle
import com.joshiminh.wallbase.ui.components.topBarInsetPadding
import com.joshiminh.wallbase.ui.theme.WallBaseShapes
import androidx.hilt.navigation.compose.hiltViewModel
import com.joshiminh.wallbase.ui.theme.WallBaseSpacing
import com.joshiminh.wallbase.ui.viewmodels.ExtensionsViewModel
import com.joshiminh.wallbase.ui.viewmodels.SourcesViewModel
import java.util.Locale
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun AddSourceBottomSheet(
    input: String,
    detectedType: SourceRepository.RemoteSourceType?,
    isSearching: Boolean,
    results: List<RedditCommunity>,
    searchError: String?,
    existingConfigs: Set<String>,
    onInputChange: (String) -> Unit,
    onSearch: () -> Unit,
    onAddSource: () -> Unit,
    onAddResult: (RedditCommunity) -> Unit,
    onOpenRepoScreen: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Add Custom Source",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onOpenRepoScreen) {
                    Text("Extension repos")
                }
            }

            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                label = { Text("URL, Subreddit, or Keywords") },
                placeholder = { Text("e.g. wallpapers, r/wallpapers, or URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                shape = WallBaseShapes.control,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAddSource,
                    enabled = input.isNotBlank() && detectedType != null,
                    shape = WallBaseShapes.pill,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Directly")
                }

                FilledTonalButton(
                    onClick = onSearch,
                    enabled = input.isNotBlank() && !isSearching,
                    shape = WallBaseShapes.pill,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Search Reddit")
                    }
                }
            }

            searchError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (results.isNotEmpty()) {
                Text(
                    text = "Reddit Communities Found",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(results, key = { it.name }) { community ->
                        val alreadyAdded = existingConfigs.contains(community.name.lowercase(Locale.ROOT))
                        RedditSearchResult(
                            community = community,
                            alreadyAdded = alreadyAdded,
                            onAdd = onAddResult
                        )
                    }
                }
            }

        }
    }
}

@Composable
private fun RedditSearchResult(
    community: RedditCommunity,
    alreadyAdded: Boolean,
    onAdd: (RedditCommunity) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = WallBaseShapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = community.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = community.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            community.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (alreadyAdded) "Already added" else "Tap add to create a new source",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                TextButton(onClick = { onAdd(community) }, enabled = !alreadyAdded) {
                    Text(if (alreadyAdded) "Added" else "Add")
                }
            }
        }
    }
}

fun sourceShareUrl(source: Source): String? {
    val config = source.config?.takeIf { it.isNotBlank() }
    if (config != null) {
        if (config.startsWith("http://") || config.startsWith("https://")) {
            return config
        }
        if (source.providerKey == SourceKeys.REDDIT) {
            val slug = config.trim('/').ifBlank { return null }
            return "https://www.reddit.com/r/$slug/"
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
        SourceKeys.REDDIT -> "https://www.reddit.com"
        SourceKeys.PINTEREST -> "https://www.pinterest.com"
        SourceKeys.WALLHAVEN -> "https://wallhaven.cc"
        else -> null
    }
}

@SuppressLint("LocalContextResourcesRead")
@Composable
fun safePainterResource(@DrawableRes resId: Int?): Painter? {
    if (resId == null || resId == 0) return null
    val context = LocalContext.current
    val isValid = remember(resId, context) {
        runCatching { context.resources.getResourceName(resId) }.isSuccess
    }
    return if (isValid) painterResource(resId) else null
}

@Composable
internal fun RemoveSourceDialog(
    source: Source,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit
) {
    var removeWallpapers by remember(source.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove ${source.title}?") },
        text = {
            Column {
                Text("Do you also want to remove wallpapers saved from this source?")
                Spacer(modifier = Modifier.size(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = removeWallpapers,
                        onCheckedChange = { removeWallpapers = it }
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Also remove wallpapers")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(removeWallpapers) }) {
                Text("Remove")
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
internal fun RemoveSelectedSourcesDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit
) {
    var removeWallpapers by remember(count) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove $count sources?") },
        text = {
            Column {
                Text("Do you also want to remove wallpapers saved from these sources?")
                Spacer(modifier = Modifier.size(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = removeWallpapers,
                        onCheckedChange = { removeWallpapers = it }
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Also remove wallpapers")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(removeWallpapers) }, enabled = count > 0) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
