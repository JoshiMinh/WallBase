package com.joshiminh.wallbase.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.joshiminh.wallbase.R

private data class SourceEntry(@DrawableRes val icon: Int, val title: String, val description: String)

@Composable
fun SourcesScreen() {
    val sources = listOf(
        SourceEntry(R.drawable.google_photos, "Google Photos", "Login, pick albums"),
        SourceEntry(R.drawable.google_drive, "Google Drive", "Login, pick folder(s)"),
        SourceEntry(R.drawable.reddit, "Reddit", "Add subs, sort/time, filters"),
        SourceEntry(R.drawable.pinterest, "Pinterest", "(planned)"),
        SourceEntry(android.R.drawable.ic_menu_search, "Websites", "Templates or custom rules"),
        SourceEntry(android.R.drawable.ic_menu_gallery, "Local", "Device Photo Picker / SAF")
    )

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(sources) { source ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Icon(
                    painter = painterResource(id = source.icon),
                    contentDescription = source.title,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.size(16.dp))
                Column {
                    Text(source.title, style = MaterialTheme.typography.titleMedium)
                    Text(source.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
