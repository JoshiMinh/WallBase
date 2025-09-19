package com.joshiminh.wallbase.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.joshiminh.wallbase.data.Source

@Composable
fun SourcesScreen(
    sources: SnapshotStateList<Source>,
    onGoogleDriveClick: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        itemsIndexed(sources) { index, source ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable(enabled = source.title == "Google Drive") {
                        if (source.title == "Google Drive") {
                            onGoogleDriveClick()
                        }
                    },
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Image(
                        painter = painterResource(id = source.icon),
                        contentDescription = source.title,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.size(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(source.title, style = MaterialTheme.typography.titleMedium)
                        Text(source.description, style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = source.enabled,
                        onCheckedChange = { checked ->
                            sources[index] = source.copy(enabled = checked)
                        }
                    )
                }
            }
        }
    }
}

