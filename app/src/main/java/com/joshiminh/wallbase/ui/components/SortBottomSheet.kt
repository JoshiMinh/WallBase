package com.joshiminh.wallbase.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            availableFields.forEach { field ->
                SortFieldRow(
                    field = field,
                    selected = selection.field == field,
                    direction = selection.direction.takeIf { selection.field == field },
                    onClick = {
                        val isCurrent = selection.field == field
                        val direction = if (isCurrent) {
                            selection.direction.toggle()
                        } else {
                            field.defaultDirection()
                        }
                        val updated = SortSelection(field, direction)
                        if (selection != updated) {
                            onSelectionChanged(updated)
                        }
                    }
                )
            }
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = field.displayName,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
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
        }
    }
}
