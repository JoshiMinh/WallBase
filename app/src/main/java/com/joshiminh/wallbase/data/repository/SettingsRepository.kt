package com.joshiminh.wallbase.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Persists simple user settings such as dark theme and layout preferences.
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

            val storageLimit = prefs[Keys.STORAGE_LIMIT_BYTES] ?: DEFAULT_STORAGE_LIMIT_BYTES
            SettingsPreferences(
                darkTheme = prefs[Keys.DARK_THEME] ?: false,
                wallpaperGridColumns = wallpaperColumns,
                albumLayout = albumLayout,
                wallpaperLayout = wallpaperLayout,
                autoDownload = prefs[Keys.AUTO_DOWNLOAD_ENABLED] ?: false,
                storageLimitBytes = storageLimit.coerceIn(0L, MAX_STORAGE_LIMIT_BYTES),
                dismissedUpdateVersion = prefs[Keys.DISMISSED_UPDATE_VERSION],
                appLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: false,
                onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            )
        }

    suspend fun setDarkTheme(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.DARK_THEME] = enabled
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

    suspend fun setAutoDownload(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.AUTO_DOWNLOAD_ENABLED] = enabled
        }
    }

    suspend fun setStorageLimitBytes(limitBytes: Long) {
        val clamped = limitBytes.coerceIn(0L, MAX_STORAGE_LIMIT_BYTES)
        dataStore.edit { prefs ->
            prefs[Keys.STORAGE_LIMIT_BYTES] = clamped
        }
    }

    suspend fun setDismissedUpdateVersion(version: String?) {
        dataStore.edit { prefs ->
            if (version.isNullOrBlank()) {
                prefs.remove(Keys.DISMISSED_UPDATE_VERSION)
            } else {
                prefs[Keys.DISMISSED_UPDATE_VERSION] = version
            }
        }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.APP_LOCK_ENABLED] = enabled
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETED] = completed
        }
    }

    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val WALLPAPER_GRID_COLUMNS = intPreferencesKey("wallpaper_grid_columns")
        val ALBUM_LAYOUT = stringPreferencesKey("album_layout")
        val WALLPAPER_LAYOUT = stringPreferencesKey("wallpaper_layout")
        val AUTO_DOWNLOAD_ENABLED = booleanPreferencesKey("auto_download_enabled")
        val STORAGE_LIMIT_BYTES = longPreferencesKey("storage_limit_bytes")
        val DISMISSED_UPDATE_VERSION = stringPreferencesKey("dismissed_update_version")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    companion object {
        private const val DEFAULT_WALLPAPER_COLUMNS = 2
        private const val MIN_WALLPAPER_COLUMNS = 1
        private const val MAX_WALLPAPER_COLUMNS = 3
        private const val MAX_STORAGE_LIMIT_BYTES = 10L * 1024 * 1024 * 1024 // 10 GB
        private const val DEFAULT_STORAGE_LIMIT_BYTES = 2L * 1024 * 1024 * 1024 // 2 GB
    }
}

data class SettingsPreferences(
    val darkTheme: Boolean,
    val wallpaperGridColumns: Int,
    val albumLayout: AlbumLayout,
    val wallpaperLayout: WallpaperLayout,
    val autoDownload: Boolean,
    val storageLimitBytes: Long,
    val dismissedUpdateVersion: String?,
    val appLockEnabled: Boolean,
    val onboardingCompleted: Boolean,
)

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class AlbumLayout {
    GRID,
    CARD_LIST;

    val storageValue: String
        get() = when (this) {
            GRID -> "grid"
            CARD_LIST -> "card_list"
        }

    companion object {
        fun fromStorage(value: String?): AlbumLayout = when (value) {
            "grid" -> GRID
            // Previously saved "list" values now fall back to the card list layout.
            "list" -> CARD_LIST
            else -> CARD_LIST
        }
    }
}

enum class WallpaperLayout {
    GRID,
    JUSTIFIED,
    LIST;

    val storageValue: String
        get() = when (this) {
            GRID -> "grid"
            JUSTIFIED -> "justified"
            LIST -> "list"
        }

    companion object {
        fun fromStorage(value: String?): WallpaperLayout = when (value) {
            "justified" -> JUSTIFIED
            "list" -> LIST
            else -> GRID
        }
    }
}
