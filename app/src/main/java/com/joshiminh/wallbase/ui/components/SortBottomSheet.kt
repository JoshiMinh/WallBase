package com.joshiminh.wallbase.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joshiminh.wallbase.ui.sort.SortDirection
import com.joshiminh.wallbase.ui.sort.SortField
import com.joshiminh.wallbase.ui.sort.SortSelection
import com.joshiminh.wallbase.ui.sort.displayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBottomSheet(
    visible: Boolean,
    title: String,
    selection: SortSelection,
    availableFields: List<SortField>,
    onFieldSelected: (SortField) -> Unit,
    onDirectionSelected: (SortDirection) -> Unit,
    onDismissRequest: () -> Unit,
    additionalContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            availableFields.forEach { field ->
                SortFieldRow(
                    field = field,
                    selected = selection.field == field,
                    direction = selection.direction.takeIf { selection.field == field },
                    onClick = { onFieldSelected(field) }
                )
            }
            Divider()
            SortDirectionRow(
                direction = selection.direction,
                onDirectionSelected = onDirectionSelected
            )
            additionalContent?.let { content ->
                Divider()
                Spacer(modifier = Modifier.height(4.dp))
                content()
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SortFieldRow(
    field: SortField,
    selected: Boolean,
    direction: SortDirection?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = if (selected) 4.dp else 0.dp
    ) {
        ListItem(
            headlineContent = { Text(text = field.displayName) },
            leadingContent = {
                RadioButton(selected = selected, onClick = null)
            },
            trailingContent = {
                direction?.let { dir ->
                    val icon = if (dir == SortDirection.Ascending) {
                        Icons.Outlined.ArrowUpward
                    } else {
                        Icons.Outlined.ArrowDownward
                    }
                    val description = if (dir == SortDirection.Ascending) {
                        "Ascending"
                    } else {
                        "Descending"
                    }
                    Icon(imageVector = icon, contentDescription = description)
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SortDirectionRow(
    direction: SortDirection,
    onDirectionSelected: (SortDirection) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Order", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DirectionButton(
                direction = SortDirection.Ascending,
                selected = direction == SortDirection.Ascending,
                onClick = { onDirectionSelected(SortDirection.Ascending) }
            )
            DirectionButton(
                direction = SortDirection.Descending,
                selected = direction == SortDirection.Descending,
                onClick = { onDirectionSelected(SortDirection.Descending) }
            )
        }
    }
}

@Composable
private fun DirectionButton(
    direction: SortDirection,
    selected: Boolean,
    onClick: () -> Unit
) {
    val icon = if (direction == SortDirection.Ascending) {
        Icons.Outlined.ArrowUpward
    } else {
        Icons.Outlined.ArrowDownward
    }
    val description = if (direction == SortDirection.Ascending) {
        "Sort ascending"
    } else {
        "Sort descending"
    }

    if (selected) {
        FilledIconButton(onClick = onClick) {
            Icon(imageVector = icon, contentDescription = description)
        }
    } else {
        OutlinedIconButton(onClick = onClick) {
            Icon(imageVector = icon, contentDescription = description)
        }
    }
}
