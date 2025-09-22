package com.joshiminh.wallbase.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joshiminh.wallbase.data.repository.AlbumLayout

@Composable
fun GridColumnPicker(
    label: String,
    selectedColumns: Int,
    onColumnsSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 1..4
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = range.toList()
            options.forEachIndexed { index, count ->
                SegmentedButton(
                    selected = selectedColumns == count,
                    onClick = { onColumnsSelected(count) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    label = { Text(text = count.toString()) }
                )
            }
        }
    }
}

@Composable
fun AlbumLayoutPicker(
    label: String,
    selectedLayout: AlbumLayout,
    onLayoutSelected: (AlbumLayout) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = AlbumLayout.values()
            options.forEachIndexed { index, layout ->
                SegmentedButton(
                    selected = selectedLayout == layout,
                    onClick = { onLayoutSelected(layout) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    label = {
                        val text = when (layout) {
                            AlbumLayout.GRID -> "Grid"
                            AlbumLayout.CARD_LIST -> "Cards"
                            AlbumLayout.LIST -> "List"
                        }
                        Text(text = text)
                    }
                )
            }
        }
    }
}
