package com.joshiminh.wallbase.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen() {
    Text(
        text = """
            Settings:
            • Theme: Follow System (auto-sync dark mode already supported)
            • Wallpapers: Apply via Samsung System Preview (planned)
            • Backup/Restore
            • About
        """.trimIndent(),
        modifier = Modifier.padding(PaddingValues(16.dp)),
        style = MaterialTheme.typography.bodyLarge
    )
}