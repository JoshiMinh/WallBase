package com.joshiminh.wallbase.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.widthIn
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joshiminh.wallbase.TopBarHandle
import com.joshiminh.wallbase.TopBarState
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem
import com.joshiminh.wallbase.ui.components.SortBottomSheet
import com.joshiminh.wallbase.ui.components.TopBarSearchField
import com.joshiminh.wallbase.ui.components.WallpaperGrid
import com.joshiminh.wallbase.ui.sort.SortField
import com.joshiminh.wallbase.ui.sort.SortSelection
import com.joshiminh.wallbase.ui.sort.toSelection
import com.joshiminh.wallbase.ui.sort.toWallpaperSortOption
import com.joshiminh.wallbase.ui.viewmodel.AlbumDetailViewModel
import com.joshiminh.wallbase.util.wallpapers.WallpaperTarget
import com.joshiminh.wallbase.util.wallpapers.rotation.WallpaperRotationDefaults
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AlbumDetailRoute(
    albumId: Long,
    onWallpaperSelected: (WallpaperItem) -> Unit,
    onConfigureTopBar: (TopBarState) -> TopBarHandle,
    viewModel: AlbumDetailViewModel = viewModel(factory = AlbumDetailViewModel.provideFactory(albumId)),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val title = uiState.albumTitle ?: "Album"

    val snackbarHostState = remember { SnackbarHostState() }
    val canSort = uiState.wallpapers.isNotEmpty()
    val actionEnabled = canSort &&
        !uiState.isDownloading &&
        !uiState.isRemovingDownloads &&
        !uiState.notFound
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    val availableSortFields = remember { listOf(SortField.Alphabet, SortField.DateAdded) }
    val sortSelection = uiState.wallpaperSortOption.toSelection()

    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }
    val trimmedQuery = remember(searchQuery) { searchQuery.trim() }
    val displayedWallpapers = remember(uiState.wallpapers, trimmedQuery, isSearchActive) {
        if (!isSearchActive || trimmedQuery.isEmpty()) {
            uiState.wallpapers
        } else {
            uiState.wallpapers.filter { wallpaper ->
                wallpaper.title.contains(trimmedQuery, ignoreCase = true) ||
                    (wallpaper.sourceName?.contains(trimmedQuery, ignoreCase = true) == true)
            }
        }
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        } else {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    LaunchedEffect(canSort) {
        if (!canSort && isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        }
    }

    val topBarActions: @Composable RowScope.() -> Unit = {
        if (isSearchActive) {
            IconButton(onClick = {
                isSearchActive = false
                searchQuery = ""
                focusManager.clearFocus()
                keyboardController?.hide()
            }) {
                Icon(imageVector = Icons.Outlined.Close, contentDescription = "Close search")
            }
            IconButton(
                onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
                enabled = canSort
            ) {
                Icon(imageVector = Icons.Outlined.Search, contentDescription = "Search")
            }
        } else {
            IconButton(
                onClick = { isSearchActive = true },
                enabled = canSort
            ) {
                Icon(imageVector = Icons.Outlined.Search, contentDescription = "Search")
            }
        }
        IconButton(
            onClick = { showSortSheet = true },
            enabled = canSort
        ) {
            Icon(imageVector = Icons.AutoMirrored.Outlined.Sort, contentDescription = "Sort")
        }
        if (uiState.isAlbumDownloaded) {
            IconButton(
                onClick = viewModel::promptRemoveDownloads,
                enabled = actionEnabled
            ) {
                Icon(
                    imageVector = Icons.Outlined.TaskAlt,
                    contentDescription = "Remove downloaded files"
                )
            }
        } else {
            IconButton(
                onClick = viewModel::downloadAlbum,
                enabled = actionEnabled
            ) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = "Download album"
                )
            }
        }
    }
    val titleContent: (@Composable () -> Unit)? = if (isSearchActive) {
        {
            TopBarSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                onClear = { searchQuery = "" },
                placeholder = "Search album",
                focusRequester = searchFocusRequester,
                showClearButton = false
            )
        }
    } else {
        null
    }
    val topBarState = TopBarState(
        title = if (isSearchActive) null else title,
        navigationIcon = null,
        actions = topBarActions,
        titleContent = titleContent
    )
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

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    AlbumDetailScreen(
        state = uiState,
        wallpapers = displayedWallpapers,
        isSearching = isSearchActive,
        searchQuery = trimmedQuery,
        onWallpaperSelected = onWallpaperSelected,
        snackbarHostState = snackbarHostState,
        onConfirmRemoveDownloads = viewModel::removeAlbumDownloads,
        onDismissRemoveDownloads = viewModel::dismissRemoveDownloadsPrompt,
        onToggleRotation = viewModel::toggleRotation,
        onSelectRotationInterval = viewModel::updateRotationInterval,
        onSelectRotationTarget = viewModel::updateRotationTarget,
        onStartRotationNow = viewModel::triggerRotationNow,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope
    )

    SortBottomSheet(
        visible = showSortSheet,
        title = "Sort wallpapers",
        selection = sortSelection,
        availableFields = availableSortFields,
        onSelectionChanged = { selection ->
            viewModel.updateSort(selection.toWallpaperSortOption())
        },
        onDismissRequest = { showSortSheet = false }
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AlbumDetailScreen(
    state: AlbumDetailViewModel.AlbumDetailUiState,
    wallpapers: List<WallpaperItem>,
    isSearching: Boolean,
    searchQuery: String,
    onWallpaperSelected: (WallpaperItem) -> Unit,
    snackbarHostState: SnackbarHostState,
    onConfirmRemoveDownloads: () -> Unit,
    onDismissRemoveDownloads: () -> Unit,
    onToggleRotation: (Boolean) -> Unit,
    onSelectRotationInterval: (Long) -> Unit,
    onSelectRotationTarget: (WallpaperTarget) -> Unit,
    onStartRotationNow: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?
) {
    val hasQuery = isSearching && searchQuery.isNotBlank()
    var rotationExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.rotation.isConfigured) {
        if (!state.rotation.isConfigured) {
            rotationExpanded = true
        }
    }

    LaunchedEffect(state.rotation.isEnabled) {
        if (state.rotation.isEnabled) {
            rotationExpanded = true
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets(left = 0.dp, top = 0.dp, right = 0.dp, bottom = 0.dp)
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.notFound -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Album not found.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (state.isDownloading) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("Downloading…") }
                        )
                    }
                    if (state.isRemovingDownloads) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("Removing downloads…") }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Scheduled rotation",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = if (state.rotation.isEnabled) {
                                            "Wallpapers rotate automatically from this album."
                                        } else {
                                            "Choose how often WallBase should rotate wallpapers."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { rotationExpanded = !rotationExpanded }) {
                                    val (icon, description) = if (rotationExpanded) {
                                        Icons.Outlined.ExpandLess to "Collapse rotation settings"
                                    } else {
                                        Icons.Outlined.ExpandMore to "Expand rotation settings"
                                    }
                                    Icon(imageVector = icon, contentDescription = description)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Switch(
                                    checked = state.rotation.isEnabled,
                                    onCheckedChange = onToggleRotation,
                                    enabled = state.wallpapers.isNotEmpty() && !state.rotation.isUpdating,
                                    colors = SwitchDefaults.colors()
                                )
                            }

                            val intervalUnits = remember { RotationIntervalUnit.values().toList() }
                            var selectedIntervalUnit by rememberSaveable {
                                mutableStateOf(RotationIntervalUnit.fromMinutes(state.rotation.intervalMinutes))
                            }
                            var intervalValue by rememberSaveable {
                                mutableStateOf(selectedIntervalUnit.displayValue(state.rotation.intervalMinutes))
                            }
                            var intervalError by rememberSaveable { mutableStateOf<String?>(null) }
                            var intervalFieldFocused by remember { mutableStateOf(false) }

                            LaunchedEffect(state.rotation.intervalMinutes) {
                                val unit = RotationIntervalUnit.fromMinutes(state.rotation.intervalMinutes)
                                selectedIntervalUnit = unit
                                intervalValue = unit.displayValue(state.rotation.intervalMinutes)
                                intervalError = null
                            }

                            val commitInterval: () -> Boolean = {
                                val minutes = parseIntervalMinutes(intervalValue, selectedIntervalUnit)
                                when {
                                    minutes == null -> {
                                        intervalError = "Enter a valid duration."
                                        false
                                    }

                                    minutes < WallpaperRotationDefaults.MIN_INTERVAL_MINUTES -> {
                                        intervalError = "Minimum rotation is ${WallpaperRotationDefaults.MIN_INTERVAL_MINUTES} minutes."
                                        false
                                    }

                                    else -> {
                                        intervalError = null
                                        if (minutes != state.rotation.intervalMinutes) {
                                            onSelectRotationInterval(minutes)
                                        }
                                        intervalValue = selectedIntervalUnit.displayValue(minutes)
                                        true
                                    }
                                }
                            }

                            AnimatedVisibility(visible = rotationExpanded) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "Interval",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = intervalValue,
                                                onValueChange = { newValue ->
                                                    val filtered = newValue.filter { it.isDigit() }
                                                    intervalValue = filtered
                                                    if (intervalError != null) {
                                                        intervalError = null
                                                    }
                                                },
                                                label = { Text("Duration") },
                                                singleLine = true,
                                                enabled = !state.rotation.isUpdating,
                                                isError = intervalError != null,
                                                keyboardOptions = KeyboardOptions(
                                                    keyboardType = KeyboardType.Number,
                                                    imeAction = ImeAction.Done
                                                ),
                                                keyboardActions = KeyboardActions(
                                                    onDone = {
                                                        if (commitInterval()) {
                                                            keyboardController?.hide()
                                                            focusManager.clearFocus()
                                                        }
                                                    }
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .widthIn(min = 96.dp)
                                                    .onFocusChanged { focusState ->
                                                        if (focusState.isFocused) {
                                                            intervalFieldFocused = true
                                                        } else if (intervalFieldFocused) {
                                                            if (commitInterval()) {
                                                                keyboardController?.hide()
                                                            }
                                                            intervalFieldFocused = false
                                                        }
                                                    }
                                            )

                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                intervalUnits.forEach { unit ->
                                                    FilterChip(
                                                        selected = selectedIntervalUnit == unit,
                                                        onClick = {
                                                            if (selectedIntervalUnit != unit) {
                                                                val baseMinutes =
                                                                    parseIntervalMinutes(intervalValue, selectedIntervalUnit)
                                                                        ?: state.rotation.intervalMinutes
                                                                val newValue = unit.valueFromMinutes(baseMinutes)
                                                                val newMinutes = unit.toMinutes(newValue)
                                                                if (newMinutes != null) {
                                                                    selectedIntervalUnit = unit
                                                                    intervalValue = newValue.toString()
                                                                    intervalError = null
                                                                    if (newMinutes != state.rotation.intervalMinutes) {
                                                                        onSelectRotationInterval(newMinutes)
                                                                    }
                                                                }
                                                            }
                                                        },
                                                        enabled = !state.rotation.isUpdating,
                                                        label = { Text(unit.displayName) }
                                                    )
                                                }
                                            }
                                        }

                                        intervalError?.let { error ->
                                            Text(
                                                text = error,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "Apply to",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        val targetScroll = rememberScrollState()
                                        Row(
                                            modifier = Modifier.horizontalScroll(targetScroll),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf(
                                                WallpaperTarget.HOME,
                                                WallpaperTarget.LOCK,
                                                WallpaperTarget.BOTH
                                            ).forEach { target ->
                                                FilterChip(
                                                    selected = state.rotation.target == target,
                                                    onClick = { onSelectRotationTarget(target) },
                                                    enabled = !state.rotation.isUpdating,
                                                    label = { Text(target.label) }
                                                )
                                            }
                                        }
                                    }

                                    val lastApplied = state.rotation.lastAppliedAt?.let { timestamp ->
                                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                            .format(Date(timestamp))
                                    }
                                    if (lastApplied != null) {
                                        Text(
                                            text = "Last applied: $lastApplied",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Button(
                                        onClick = onStartRotationNow,
                                        enabled = state.rotation.isEnabled && !state.rotation.isUpdating
                                    ) {
                                        Text(text = "Rotate now")
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    if (wallpapers.isEmpty()) {
                        val message = when {
                            state.wallpapers.isEmpty() -> "This album doesn't have any wallpapers yet."
                            hasQuery -> "No wallpapers match your search."
                            else -> "No wallpapers available."
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else {
                        WallpaperGrid(
                            wallpapers = wallpapers,
                            onWallpaperSelected = onWallpaperSelected,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            columns = state.wallpaperGridColumns,
                            layout = state.wallpaperLayout,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                }
            }
        }
    }

    if (state.showRemoveDownloadsConfirmation) {
        AlertDialog(
            onDismissRequest = {
                if (!state.isRemovingDownloads) {
                    onDismissRemoveDownloads()
                }
            },
            title = { Text(text = "Remove downloaded files?") },
            text = {
                Text(
                    text = "Delete the downloaded copies saved for this album?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirmRemoveDownloads,
                    enabled = !state.isRemovingDownloads
                ) {
                    Text(text = "Remove")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissRemoveDownloads,
                    enabled = !state.isRemovingDownloads
                ) {
                    Text(text = "Cancel")
                }
            }
        )
    }
}

private enum class RotationIntervalUnit(val displayName: String, val minutesPerUnit: Long) {
    MINUTES("Minutes", 1),
    HOURS("Hours", 60),
    DAYS("Days", 1440);

    fun toMinutes(value: Long): Long? {
        if (value <= 0) return null
        if (value > Long.MAX_VALUE / minutesPerUnit) return null
        return value * minutesPerUnit
    }

    fun valueFromMinutes(minutes: Long): Long {
        return when (this) {
            MINUTES -> minutes.coerceAtLeast(1)
            else -> ((minutes + minutesPerUnit - 1) / minutesPerUnit).coerceAtLeast(1)
        }
    }

    fun displayValue(minutes: Long): String = valueFromMinutes(minutes).toString()

    companion object {
        fun fromMinutes(minutes: Long): RotationIntervalUnit {
            return when {
                minutes % DAYS.minutesPerUnit == 0L -> DAYS
                minutes % HOURS.minutesPerUnit == 0L -> HOURS
                else -> MINUTES
            }
        }
    }
}

private fun parseIntervalMinutes(value: String, unit: RotationIntervalUnit): Long? {
    if (value.isBlank()) return null
    val number = value.toLongOrNull() ?: return null
    return unit.toMinutes(number)
}
