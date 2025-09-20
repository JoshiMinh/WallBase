package com.joshiminh.wallbase.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joshiminh.wallbase.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    uiState: SettingsViewModel.SettingsUiState,
    onToggleDarkTheme: (Boolean) -> Unit,
    onSourceRepoUrlChanged: (String) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onMessageShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onMessageShown()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                SettingsSection(spacing = 12.dp) {
                    Text(
                        text = "Appearance",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SettingsCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Dark mode",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Use a dark theme throughout the app.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Switch(
                                checked = uiState.darkTheme,
                                onCheckedChange = onToggleDarkTheme
                            )
                        }
                    }
                }
            }

            item {
                SettingsSection(spacing = 12.dp) {
                    Text(
                        text = "Sources",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SettingsCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Source repository",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Paste a JSON feed that lists wallpaper sources to import.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            OutlinedTextField(
                                value = uiState.sourceRepoUrl,
                                onValueChange = onSourceRepoUrlChanged,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = {
                                    Text("https://example.com/wallbase-sources.json")
                                },
                                keyboardOptions = KeyboardOptions.Default.copy(
                                    imeAction = ImeAction.Done
                                )
                            )
                        }
                    }
                }
            }

            item {
                SettingsSection(spacing = 12.dp) {
                    Text(
                        text = "Data & backup",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SettingsCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Export library",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Save your sources, library, and albums as a backup file.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            SettingsActionButton(
                                text = if (uiState.isBackingUp) "Exporting…" else "Export",
                                enabled = !uiState.isBackingUp && !uiState.isRestoring,
                                showProgress = uiState.isBackingUp,
                                onClick = onExportBackup
                            )
                        }
                    }

                    SettingsCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Import backup",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Restore your sources, library, and albums from a backup file.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            SettingsActionButton(
                                text = if (uiState.isRestoring) "Importing…" else "Import",
                                enabled = !uiState.isRestoring && !uiState.isBackingUp,
                                showProgress = uiState.isRestoring,
                                onClick = onImportBackup
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    spacing: Dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(spacing), content = content)
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        content = content
    )
}

@Composable
private fun SettingsActionButton(
    text: String,
    enabled: Boolean,
    showProgress: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled
    ) {
        if (showProgress) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 12.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        Text(text = text)
    }
}