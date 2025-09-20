package com.joshiminh.wallbase.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.joshiminh.wallbase.R

@Composable
fun SettingsScreen(
    darkTheme: Boolean,
    uiState: SettingsViewModel.SettingsUiState,
    onToggleDarkTheme: (Boolean) -> Unit,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = stringResource(id = R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall
            )

            SettingsSection(spacing = 12.dp) {
                Text(
                    text = stringResource(id = R.string.settings_appearance_header),
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
                                text = stringResource(id = R.string.settings_dark_mode_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(id = R.string.settings_dark_mode_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = darkTheme,
                            onCheckedChange = onToggleDarkTheme
                        )
                    }
                }
            }

            SettingsSection(spacing = 12.dp) {
                Text(
                    text = stringResource(id = R.string.settings_backup_header),
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
                                text = stringResource(id = R.string.settings_backup_export_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(id = R.string.settings_backup_export_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        SettingsActionButton(
                            text = if (uiState.isBackingUp) {
                                stringResource(id = R.string.settings_backup_export_in_progress)
                            } else {
                                stringResource(id = R.string.settings_backup_export_action)
                            },
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
                                text = stringResource(id = R.string.settings_backup_import_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(id = R.string.settings_backup_import_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        SettingsActionButton(
                            text = if (uiState.isRestoring) {
                                stringResource(id = R.string.settings_backup_import_in_progress)
                            } else {
                                stringResource(id = R.string.settings_backup_import_action)
                            },
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

@Composable
private fun SettingsSection(
    spacing: Dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(spacing), content = content)
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    @Suppress("UNCHECKED_CAST")
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        content = content as @Composable (ColumnScope.() -> Unit)
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

