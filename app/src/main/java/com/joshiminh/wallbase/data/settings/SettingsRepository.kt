package com.joshiminh.wallbase.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Persists simple user settings such as dark theme and custom source repository URLs.
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>
) {

    val preferences: Flow<SettingsPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs ->
            SettingsPreferences(
                darkTheme = prefs[Keys.DARK_THEME] ?: false,
                sourceRepoUrl = prefs[Keys.SOURCE_REPO_URL].orEmpty()
            )
        }

    suspend fun setDarkTheme(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.DARK_THEME] = enabled
        }
    }

    suspend fun setSourceRepoUrl(url: String) {
        val sanitized = url.trim()
        dataStore.edit { prefs ->
            if (sanitized.isBlank()) {
                prefs.remove(Keys.SOURCE_REPO_URL)
            } else {
                prefs[Keys.SOURCE_REPO_URL] = sanitized
            }
        }
    }

    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val SOURCE_REPO_URL = stringPreferencesKey("source_repo_url")
    }
}

data class SettingsPreferences(
    val darkTheme: Boolean,
    val sourceRepoUrl: String
)

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
