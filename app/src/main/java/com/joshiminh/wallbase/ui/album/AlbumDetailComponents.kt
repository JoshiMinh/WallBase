package com.joshiminh.wallbase.ui.album

import com.joshiminh.wallbase.util.RotationIntervalUnit
import com.joshiminh.wallbase.util.formatIntervalText
import com.joshiminh.wallbase.util.RotationTargetOption
import com.joshiminh.wallbase.util.*

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.surfaceColorAtElevation
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joshiminh.wallbase.navigation.TopBarHandle
import com.joshiminh.wallbase.navigation.TopBarState
import com.joshiminh.wallbase.data.entity.WallpaperItem
import com.joshiminh.wallbase.data.repository.WallpaperLayout
import com.joshiminh.wallbase.ui.components.SortBottomSheet
import com.joshiminh.wallbase.ui.components.TopBarSearchField
import com.joshiminh.wallbase.ui.components.WallpaperGrid
import com.joshiminh.wallbase.util.SortField
import com.joshiminh.wallbase.util.toSelection
import com.joshiminh.wallbase.util.toWallpaperSortOption
import com.joshiminh.wallbase.ui.viewmodel.AlbumDetailViewModel
import com.joshiminh.wallbase.util.wallpapers.WallpaperTarget
import com.joshiminh.wallbase.util.wallpapers.WallpaperRotationDefaults
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotationScheduleBottomSheet(
    rotationState: AlbumDetailViewModel.RotationUiState,
    canConfigure: Boolean,
    onDismiss: () -> Unit,
    onToggleRotation: (Boolean) -> Unit,
    onSelectRotationInterval: (Long) -> Unit,
    onSelectRotationTarget: (WallpaperTarget) -> Unit,
    onStartRotationNow: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val intervalUnits = remember { RotationIntervalUnit.entries }
    var selectedIntervalUnit by rememberSaveable { mutableStateOf(RotationIntervalUnit.fromMinutes(rotationState.intervalMinutes)) }
    var intervalValue by rememberSaveable { mutableStateOf(selectedIntervalUnit.displayValue(rotationState.intervalMinutes)) }
    var intervalError by rememberSaveable { mutableStateOf<String?>(null) }
    var intervalFieldFocused by remember { mutableStateOf(false) }

    LaunchedEffect(rotationState.intervalMinutes) {
        val unit = RotationIntervalUnit.fromMinutes(rotationState.intervalMinutes)
        selectedIntervalUnit = unit
        intervalValue = unit.displayValue(rotationState.intervalMinutes)
        intervalError = null
    }

    val quickIntervals = remember { listOf(30L, 60L, 180L, 360L, 720L, 1440L, 2880L, 10080L) }

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
                if (minutes != rotationState.intervalMinutes && canConfigure && !rotationState.isUpdating) {
                    onSelectRotationInterval(minutes)
                }
                intervalValue = selectedIntervalUnit.displayValue(minutes)
                true
            }
        }
    }

    val lastApplied = rotationState.lastAppliedAt?.let { timestamp ->
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(timestamp))
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Scheduled rotation",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Outlined.Close, contentDescription = "Close")
                }
            }

            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                RotationToggleCard(
                    summary = rotationSummary(rotationState, canConfigure),
                    lastApplied = lastApplied,
                    enabled = rotationState.isEnabled,
                    canConfigure = canConfigure,
                    isBusy = rotationState.isUpdating,
                    onToggle = {
                        if (!it || commitInterval()) onToggleRotation(it)
                    },
                    onRotateNow = onStartRotationNow
                )
                RotationIntervalCard(
                    value = intervalValue,
                    unit = selectedIntervalUnit,
                    units = intervalUnits,
                    enabled = canConfigure && !rotationState.isUpdating,
                    errorText = intervalError,
                    quickIntervals = quickIntervals,
                    onValueChange = { newValue ->
                        intervalValue = newValue.filter { it.isDigit() }
                        if (intervalError != null) intervalError = null
                    },
                    onCommit = {
                        if (commitInterval()) {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }
                    },
                    onUnitSelected = { unit ->
                        if (selectedIntervalUnit != unit) {
                            val baseMinutes = parseIntervalMinutes(intervalValue, selectedIntervalUnit)
                                ?: rotationState.intervalMinutes
                            val newValue = unit.valueFromMinutes(baseMinutes)
                            val newMinutes = unit.toMinutes(newValue)
                            if (newMinutes != null) {
                                selectedIntervalUnit = unit
                                intervalValue = newValue.toString()
                                intervalError = null
                                if (newMinutes != rotationState.intervalMinutes && canConfigure && !rotationState.isUpdating) {
                                    onSelectRotationInterval(newMinutes)
                                }
                            }
                        }
                    },
                    onIntervalSelected = { minutes ->
                        val sanitized = minutes.coerceAtLeast(WallpaperRotationDefaults.MIN_INTERVAL_MINUTES)
                        val unit = RotationIntervalUnit.fromMinutes(sanitized)
                        selectedIntervalUnit = unit
                        intervalValue = unit.displayValue(sanitized)
                        intervalError = null
                        onSelectRotationInterval(sanitized)
                    },
                    onFocusChanged = { focused ->
                        if (!focused && intervalFieldFocused) {
                            if (commitInterval()) keyboardController?.hide()
                            intervalFieldFocused = false
                        } else if (focused) {
                            intervalFieldFocused = true
                        }
                    }
                )
                RotationTargetCard(
                    selectedTarget = rotationState.target,
                    enabled = canConfigure && !rotationState.isUpdating,
                    onSelect = onSelectRotationTarget
                )
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(text = "Close")
            }
        }
    }
}

@Composable
fun RotationToggleCard(
    summary: String,
    lastApplied: String?,
    enabled: Boolean,
    canConfigure: Boolean,
    isBusy: Boolean,
    onToggle: (Boolean) -> Unit,
    onRotateNow: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (enabled) "Rotation enabled" else "Rotation disabled",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (lastApplied != null) {
                        Text(
                            text = "Last rotated $lastApplied",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    enabled = canConfigure && !isBusy,
                    colors = SwitchDefaults.colors()
                )
            }
            if (isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            FilledTonalButton(
                onClick = onRotateNow,
                enabled = enabled && canConfigure && !isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Rotate now")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RotationIntervalCard(
    value: String,
    unit: RotationIntervalUnit,
    units: List<RotationIntervalUnit>,
    enabled: Boolean,
    errorText: String?,
    quickIntervals: List<Long>,
    onValueChange: (String) -> Unit,
    onCommit: () -> Unit,
    onUnitSelected: (RotationIntervalUnit) -> Unit,
    onIntervalSelected: (Long) -> Unit,
    onFocusChanged: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "Rotation interval", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Choose how often WallBase applies a wallpaper from this album.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(text = "Duration") },
                singleLine = true,
                enabled = enabled,
                isError = errorText != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onCommit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { onFocusChanged(it.isFocused) }
            )
            if (errorText != null) {
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (quickIntervals.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Quick picks",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickIntervals.distinct().sorted().forEach { minutes ->
                            val displayUnit = RotationIntervalUnit.fromMinutes(minutes)
                            val displayValue = displayUnit.valueFromMinutes(minutes)
                            AssistChip(
                                onClick = { onIntervalSelected(minutes) },
                                enabled = enabled,
                                label = { Text(text = formatIntervalText(displayValue, displayUnit)) }
                            )
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Time unit",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    units.forEach { item ->
                        FilterChip(
                            selected = unit == item,
                            onClick = { onUnitSelected(item) },
                            enabled = enabled,
                            label = { Text(text = item.displayName) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RotationTargetCard(
    selectedTarget: WallpaperTarget,
    enabled: Boolean,
    onSelect: (WallpaperTarget) -> Unit
) {
    val targets = remember {
        listOf(
            RotationTargetOption(WallpaperTarget.HOME, "Home", Icons.Outlined.Home),
            RotationTargetOption(WallpaperTarget.LOCK, "Lock", Icons.Outlined.Lock),
            RotationTargetOption(WallpaperTarget.BOTH, "Both", Icons.Outlined.Wallpaper)
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "Apply to", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Select which screens receive new wallpapers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                targets.forEach { option ->
                    FilterChip(
                        selected = selectedTarget == option.target,
                        onClick = { onSelect(option.target) },
                        enabled = enabled,
                        label = { Text(text = option.label) },
                        leadingIcon = {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

fun parseIntervalMinutes(value: String, unit: RotationIntervalUnit): Long? {
    if (value.isBlank()) return null
    val number = value.toLongOrNull() ?: return null
    return unit.toMinutes(number)
}

fun rotationSummary(
    rotation: AlbumDetailViewModel.RotationUiState,
    canConfigure: Boolean
): String {
    if (!canConfigure) return "Add wallpapers to schedule rotation."
    if (!rotation.isConfigured) return "Rotation isn't configured yet."
    if (!rotation.isEnabled) return "Rotation is turned off."
    val unit = RotationIntervalUnit.fromMinutes(rotation.intervalMinutes)
    val value = unit.valueFromMinutes(rotation.intervalMinutes)
    val intervalText = formatIntervalText(value, unit)
    return "Rotates every $intervalText on the ${rotation.target.label}."
}



