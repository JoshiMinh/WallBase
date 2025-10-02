package com.joshiminh.wallbase.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joshiminh.wallbase.ui.sort.SortDirection
import com.joshiminh.wallbase.ui.sort.SortField
import com.joshiminh.wallbase.ui.sort.SortSelection
import com.joshiminh.wallbase.ui.sort.defaultDirection
import com.joshiminh.wallbase.ui.sort.displayName
import com.joshiminh.wallbase.ui.sort.toggle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBottomSheet(
    visible: Boolean,
    title: String,
    selection: SortSelection,
    availableFields: List<SortField>,
    onSelectionChanged: (SortSelection) -> Unit,
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
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Choose how items are ordered",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            availableFields.forEach { field ->
                val isSelected = selection.field == field
                val direction = selection.direction.takeIf { isSelected }
                SortOptionRow(
                    field = field,
                    selected = isSelected,
                    direction = direction,
                    onClick = {
                        val newDirection = if (isSelected) {
                            selection.direction.toggle()
                        } else {
                            field.defaultDirection()
                        }
                        val updated = SortSelection(field, newDirection)
                        if (selection != updated) {
                            onSelectionChanged(updated)
                        }
                    }
                )
            }
            additionalContent?.let { content ->
                Divider()
                content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortOptionRow(
    field: SortField,
    selected: Boolean,
    direction: SortDirection?,
    onClick: () -> Unit
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        tonalElevation = if (selected) 6.dp else 0.dp,
        onClick = onClick
    ) {
        ListItem(
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            ),
            leadingContent = {
                Icon(imageVector = field.icon(), contentDescription = null)
            },
            headlineContent = {
                Text(field.displayName, style = MaterialTheme.typography.titleSmall)
            },
            supportingContent = {
                if (direction != null) {
                    Text(
                        text = direction.description(field),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            trailingContent = {
                if (direction != null) {
                    Icon(
                        imageVector = direction.icon(),
                        contentDescription = direction.description(field)
                    )
                }
            }
        )
    }
}

private fun SortField.icon() = when (this) {
    SortField.Alphabet -> Icons.Outlined.SortByAlpha
    SortField.DateAdded -> Icons.Outlined.Schedule
}

private fun SortDirection.description(field: SortField): String = when (field) {
    SortField.Alphabet -> if (this == SortDirection.Ascending) "A → Z" else "Z → A"
    SortField.DateAdded -> if (this == SortDirection.Descending) "Newest first" else "Oldest first"
}

private fun SortDirection.icon() = if (this == SortDirection.Ascending) {
    Icons.Outlined.ArrowUpward
} else {
    Icons.Outlined.ArrowDownward
}
