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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.io.IOException

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists simple user settings such as dark theme and layout preferences.
 */
@Singleton
class SettingsRepository @Inject constructor(
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

            val appThemeStr = prefs[Keys.APP_THEME]
            val legacyDarkTheme = prefs[Keys.DARK_THEME] ?: true
            val appTheme = if (appThemeStr != null) {
                AppTheme.fromStorage(appThemeStr)
            } else if (legacyDarkTheme) {
                AppTheme.DARK
            } else {
                AppTheme.DARK
            }

            val dynamicPref = prefs[Keys.DYNAMIC_COLOR] ?: false
            val accentColorStr = prefs[Keys.APP_ACCENT_COLOR]
            val appAccentColor = if (accentColorStr != null) {
                AppAccentColor.fromStorage(accentColorStr)
            } else if (dynamicPref) {
                AppAccentColor.DYNAMIC
            } else {
                AppAccentColor.PINK
            }

            val dynamicColor = dynamicPref && (appAccentColor == AppAccentColor.DYNAMIC)
            val amoledDark = prefs[Keys.AMOLED_DARK] ?: false

            val storageLimit = prefs[Keys.STORAGE_LIMIT_BYTES] ?: DEFAULT_STORAGE_LIMIT_BYTES
            SettingsPreferences(
                appTheme = appTheme,
                appAccentColor = appAccentColor,
                dynamicColor = dynamicColor,
                amoledDark = amoledDark,
                animationsEnabled = prefs[Keys.ANIMATIONS_ENABLED] ?: true,
                wallpaperGridColumns = wallpaperColumns,
                albumLayout = albumLayout,
                wallpaperLayout = wallpaperLayout,
                autoDownload = prefs[Keys.AUTO_DOWNLOAD_ENABLED] ?: false,
                includeSourcesInBackup =
                    prefs[Keys.INCLUDE_SOURCES_IN_BACKUP] ?: DEFAULT_INCLUDE_SOURCES,
                storageLimitBytes = storageLimit.coerceIn(0L, MAX_STORAGE_LIMIT_BYTES),
                dismissedUpdateVersion = prefs[Keys.DISMISSED_UPDATE_VERSION],
                appLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: false,
                onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
                showHorizontalWallpapers = prefs[Keys.SHOW_HORIZONTAL_WALLPAPERS] ?: true,
                showDownloadBadge = prefs[Keys.SHOW_DOWNLOAD_BADGE] ?: true,
                categoriesEnabled = prefs[Keys.CATEGORIES_ENABLED] ?: true,
            )
        }

    suspend fun setAppTheme(theme: AppTheme) {
        dataStore.edit { prefs ->
            prefs[Keys.APP_THEME] = theme.storageValue
            prefs.remove(Keys.DARK_THEME) // Cleanup legacy value
        }
    }

    suspend fun setAppAccentColor(color: AppAccentColor) {
        dataStore.edit { prefs ->
            prefs[Keys.APP_ACCENT_COLOR] = color.storageValue
            if (color != AppAccentColor.DYNAMIC) {
                prefs[Keys.DYNAMIC_COLOR] = false
            } else {
                prefs[Keys.DYNAMIC_COLOR] = true
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

    suspend fun setAutoDownload(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.AUTO_DOWNLOAD_ENABLED] = enabled
        }
    }

    suspend fun setAnimationsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.ANIMATIONS_ENABLED] = enabled
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

    suspend fun setIncludeSourcesInBackup(include: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.INCLUDE_SOURCES_IN_BACKUP] = include
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.DYNAMIC_COLOR] = enabled
            if (enabled) {
                prefs[Keys.APP_ACCENT_COLOR] = AppAccentColor.DYNAMIC.storageValue
            } else if (prefs[Keys.APP_ACCENT_COLOR] == AppAccentColor.DYNAMIC.storageValue) {
                prefs[Keys.APP_ACCENT_COLOR] = AppAccentColor.PINK.storageValue
            }
        }
    }

    suspend fun setAmoledDark(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.AMOLED_DARK] = enabled
        }
    }

    suspend fun setShowHorizontalWallpapers(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SHOW_HORIZONTAL_WALLPAPERS] = show
        }
    }

    suspend fun setShowDownloadBadge(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SHOW_DOWNLOAD_BADGE] = show
        }
    }

    suspend fun setCategoriesEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.CATEGORIES_ENABLED] = enabled
        }
    }

    suspend fun exportSettingsJson(): JSONObject {
        val prefs = preferences.first()
        return JSONObject().apply {
            put("app_theme", prefs.appTheme.storageValue)
            put("app_accent_color", prefs.appAccentColor.storageValue)
            put("dynamic_color", prefs.dynamicColor)
            put("amoled_dark", prefs.amoledDark)
            put("animations_enabled", prefs.animationsEnabled)
            put("wallpaper_grid_columns", prefs.wallpaperGridColumns)
            put("album_layout", prefs.albumLayout.storageValue)
            put("wallpaper_layout", prefs.wallpaperLayout.storageValue)
            put("auto_download", prefs.autoDownload)
            put("include_sources_in_backup", prefs.includeSourcesInBackup)
            put("storage_limit_bytes", prefs.storageLimitBytes)
            put("show_horizontal_wallpapers", prefs.showHorizontalWallpapers)
            put("show_download_badge", prefs.showDownloadBadge)
            put("categories_enabled", prefs.categoriesEnabled)
        }
    }

    suspend fun importSettingsJson(json: JSONObject) {
        dataStore.edit { prefs ->
            if (json.has("app_theme")) {
                prefs[Keys.APP_THEME] = json.optString("app_theme", AppTheme.SYSTEM.storageValue)
            }
            if (json.has("app_accent_color")) {
                prefs[Keys.APP_ACCENT_COLOR] = json.optString("app_accent_color", AppAccentColor.PINK.storageValue)
            }
            if (json.has("dynamic_color")) {
                prefs[Keys.DYNAMIC_COLOR] = json.optBoolean("dynamic_color", false)
            }
            if (json.has("amoled_dark")) {
                prefs[Keys.AMOLED_DARK] = json.optBoolean("amoled_dark", false)
            }
            if (json.has("animations_enabled")) {
                prefs[Keys.ANIMATIONS_ENABLED] = json.optBoolean("animations_enabled", true)
            }
            if (json.has("wallpaper_grid_columns")) {
                prefs[Keys.WALLPAPER_GRID_COLUMNS] = json.optInt("wallpaper_grid_columns", DEFAULT_WALLPAPER_COLUMNS)
            }
            if (json.has("album_layout")) {
                prefs[Keys.ALBUM_LAYOUT] = json.optString("album_layout", AlbumLayout.CARD_LIST.storageValue)
            }
            if (json.has("wallpaper_layout")) {
                prefs[Keys.WALLPAPER_LAYOUT] = json.optString("wallpaper_layout", WallpaperLayout.GRID.storageValue)
            }
            if (json.has("auto_download")) {
                prefs[Keys.AUTO_DOWNLOAD_ENABLED] = json.optBoolean("auto_download", false)
            }
            if (json.has("include_sources_in_backup")) {
                prefs[Keys.INCLUDE_SOURCES_IN_BACKUP] = json.optBoolean("include_sources_in_backup", DEFAULT_INCLUDE_SOURCES)
            }
            if (json.has("storage_limit_bytes")) {
                prefs[Keys.STORAGE_LIMIT_BYTES] = json.optLong("storage_limit_bytes", DEFAULT_STORAGE_LIMIT_BYTES)
            }
            if (json.has("show_horizontal_wallpapers")) {
                prefs[Keys.SHOW_HORIZONTAL_WALLPAPERS] = json.optBoolean("show_horizontal_wallpapers", true)
            }
            if (json.has("show_download_badge")) {
                prefs[Keys.SHOW_DOWNLOAD_BADGE] = json.optBoolean("show_download_badge", true)
            }
            if (json.has("categories_enabled")) {
                prefs[Keys.CATEGORIES_ENABLED] = json.optBoolean("categories_enabled", true)
            }
        }
    }

    private object Keys {
        val APP_THEME = stringPreferencesKey("app_theme")
        val APP_ACCENT_COLOR = stringPreferencesKey("app_accent_color")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val AMOLED_DARK = booleanPreferencesKey("amoled_dark")
        val DARK_THEME = booleanPreferencesKey("dark_theme") // legacy
        val WALLPAPER_GRID_COLUMNS = intPreferencesKey("wallpaper_grid_columns")
        val ALBUM_LAYOUT = stringPreferencesKey("album_layout")
        val WALLPAPER_LAYOUT = stringPreferencesKey("wallpaper_layout")
        val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
        val AUTO_DOWNLOAD_ENABLED = booleanPreferencesKey("auto_download_enabled")
        val INCLUDE_SOURCES_IN_BACKUP = booleanPreferencesKey("include_sources_in_backup")
        val STORAGE_LIMIT_BYTES = longPreferencesKey("storage_limit_bytes")
        val DISMISSED_UPDATE_VERSION = stringPreferencesKey("dismissed_update_version")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val SHOW_HORIZONTAL_WALLPAPERS = booleanPreferencesKey("show_horizontal_wallpapers")
        val SHOW_DOWNLOAD_BADGE = booleanPreferencesKey("show_download_badge")
        val CATEGORIES_ENABLED = booleanPreferencesKey("categories_enabled")
    }

    companion object {
        private const val DEFAULT_WALLPAPER_COLUMNS = 2
        private const val MIN_WALLPAPER_COLUMNS = 1
        private const val MAX_WALLPAPER_COLUMNS = 3
        private const val MAX_STORAGE_LIMIT_BYTES = 10L * 1024 * 1024 * 1024 // 10 GB
        private const val DEFAULT_STORAGE_LIMIT_BYTES = 2L * 1024 * 1024 * 1024 // 2 GB
        private const val DEFAULT_INCLUDE_SOURCES = true
    }
}

data class SettingsPreferences(
    val appTheme: AppTheme,
    val appAccentColor: AppAccentColor,
    val dynamicColor: Boolean,
    val amoledDark: Boolean,
    val animationsEnabled: Boolean,
    val wallpaperGridColumns: Int,
    val albumLayout: AlbumLayout,
    val wallpaperLayout: WallpaperLayout,
    val autoDownload: Boolean,
    val includeSourcesInBackup: Boolean,
    val storageLimitBytes: Long,
    val dismissedUpdateVersion: String?,
    val appLockEnabled: Boolean,
    val onboardingCompleted: Boolean,
    val showHorizontalWallpapers: Boolean,
    val showDownloadBadge: Boolean,
    val categoriesEnabled: Boolean = true,
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

enum class AppTheme {
    LIGHT,
    DARK,
    AMOLED,
    SYSTEM;

    val storageValue: String
        get() = when (this) {
            LIGHT -> "light"
            DARK -> "dark"
            AMOLED -> "amoled"
            SYSTEM -> "system"
        }

    companion object {
        fun fromStorage(value: String?): AppTheme = when (value) {
            "dark", "amoled" -> DARK
            "system" -> SYSTEM
            else -> LIGHT
        }
    }
}

enum class AppAccentColor {
    DYNAMIC, // Material You dynamic colors (Android 12+)
    PINK, // Default Brand Pink
    RED,
    BLUE,
    GREEN;

    val storageValue: String
        get() = when (this) {
            DYNAMIC -> "dynamic"
            PINK -> "pink"
            RED -> "red"
            BLUE -> "blue"
            GREEN -> "green"
        }

    companion object {
        fun fromStorage(value: String?): AppAccentColor = when (value) {
            "dynamic" -> DYNAMIC
            "red" -> RED
            "blue" -> BLUE
            "green" -> GREEN
            else -> PINK
        }
    }
}
