package com.joshiminh.wallbase.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
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
            val wallpaperColumns = (prefs[Keys.WALLPAPER_GRID_COLUMNS] ?: DEFAULT_WALLPAPER_COLUMNS)
                .coerceIn(MIN_WALLPAPER_COLUMNS, MAX_WALLPAPER_COLUMNS)
            val albumLayout = AlbumLayout.fromStorage(prefs[Keys.ALBUM_LAYOUT])
            val wallpaperLayout = WallpaperLayout.fromStorage(prefs[Keys.WALLPAPER_LAYOUT])

            SettingsPreferences(
                darkTheme = prefs[Keys.DARK_THEME] ?: false,
                sourceRepoUrl = prefs[Keys.SOURCE_REPO_URL].orEmpty(),
                wallpaperGridColumns = wallpaperColumns,
                albumLayout = albumLayout,
                wallpaperLayout = wallpaperLayout
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

    suspend fun setWallpaperGridColumns(columns: Int) {
        val clamped = columns.coerceIn(MIN_WALLPAPER_COLUMNS, MAX_WALLPAPER_COLUMNS)
        dataStore.edit { prefs ->
            prefs[Keys.WALLPAPER_GRID_COLUMNS] = clamped
        }
    }

    suspend fun setWallpaperLayout(layout: WallpaperLayout) {
        dataStore.edit { prefs ->
            prefs[Keys.WALLPAPER_LAYOUT] = layout.storageValue
        }
    }

    suspend fun setAlbumLayout(layout: AlbumLayout) {
        dataStore.edit { prefs ->
            prefs[Keys.ALBUM_LAYOUT] = layout.storageValue
        }
    }

    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val SOURCE_REPO_URL = stringPreferencesKey("source_repo_url")
        val WALLPAPER_GRID_COLUMNS = intPreferencesKey("wallpaper_grid_columns")
        val ALBUM_LAYOUT = stringPreferencesKey("album_layout")
        val WALLPAPER_LAYOUT = stringPreferencesKey("wallpaper_layout")
    }

    companion object {
        private const val DEFAULT_WALLPAPER_COLUMNS = 2
        private const val MIN_WALLPAPER_COLUMNS = 1
        private const val MAX_WALLPAPER_COLUMNS = 4
    }
}

data class SettingsPreferences(
    val darkTheme: Boolean,
    val sourceRepoUrl: String,
    val wallpaperGridColumns: Int,
    val albumLayout: AlbumLayout,
    val wallpaperLayout: WallpaperLayout
)

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class AlbumLayout {
    GRID,
    CARD_LIST,
    LIST;

    val storageValue: String
        get() = when (this) {
            GRID -> "grid"
            CARD_LIST -> "card_list"
            LIST -> "list"
        }

    companion object {
        fun fromStorage(value: String?): AlbumLayout = when (value) {
            "grid" -> GRID
            "list" -> LIST
            else -> CARD_LIST
        }
    }
}

enum class WallpaperLayout {
    GRID,
    LIST;

    val storageValue: String
        get() = when (this) {
            GRID -> "grid"
            LIST -> "list"
        }

    companion object {
        fun fromStorage(value: String?): WallpaperLayout = when (value) {
            "list" -> LIST
            else -> GRID
        }
    }
}
