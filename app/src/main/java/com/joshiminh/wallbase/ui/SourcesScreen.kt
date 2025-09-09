package com.joshiminh.wallbase.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SourcesScreen() {
    Text(
        text = """
            Sources:
            • Google Photos — Login, pick albums
            • Google Drive — Login, pick folder(s)
            • Reddit — Add subs, sort/time, filters
            • Pinterest — (planned)
            • Websites — Templates or custom rules
            • Local — Device Photo Picker / SAF
        """.trimIndent(),
        modifier = Modifier.padding(PaddingValues(16.dp)),
        style = MaterialTheme.typography.bodyLarge
    )
}