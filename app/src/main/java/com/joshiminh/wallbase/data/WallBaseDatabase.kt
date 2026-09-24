@file:Suppress("TYPE_INTERSECTION_AS_REIFIED_WARNING")

package com.joshiminh.wallbase.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.joshiminh.wallbase.data.dao.AlbumDao
import com.joshiminh.wallbase.data.dao.CategoryDao
import com.joshiminh.wallbase.data.dao.SourceDao
import com.joshiminh.wallbase.data.dao.WallpaperDao
import com.joshiminh.wallbase.data.entity.AlbumEntity
import com.joshiminh.wallbase.data.entity.AlbumWallpaperCrossRef
import com.joshiminh.wallbase.data.entity.CategoryEntity
import com.joshiminh.wallbase.data.entity.CategoryWallpaperCrossRef
import com.joshiminh.wallbase.data.entity.DefaultSources
import com.joshiminh.wallbase.data.entity.SourceEntity
import com.joshiminh.wallbase.data.entity.SourceSeed
import com.joshiminh.wallbase.data.entity.SourceKeys
import com.joshiminh.wallbase.data.entity.WallpaperEntity
import com.joshiminh.wallbase.util.wallpapers.WallpaperAdjustments
import com.joshiminh.wallbase.util.wallpapers.WallpaperAdjustmentsJson
import com.joshiminh.wallbase.util.wallpapers.WallpaperCrop
import com.joshiminh.wallbase.util.wallpapers.WallpaperCropSettings
import java.net.URI
import java.util.Locale

@Database(
    entities = [
        AlbumEntity::class,
        WallpaperEntity::class,
        AlbumWallpaperCrossRef::class,
        SourceEntity::class,
        CategoryEntity::class,
        CategoryWallpaperCrossRef::class
    ],
    version = 12,
    exportSchema = false
)
abstract class WallBaseDatabase : RoomDatabase() {

    abstract fun sourceDao(): SourceDao
    abstract fun wallpaperDao(): WallpaperDao
    abstract fun albumDao(): AlbumDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: WallBaseDatabase? = null

        fun getInstance(context: Context): WallBaseDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext)
            }
        }

        private fun buildDatabase(context: Context): WallBaseDatabase {
            val callback = DefaultSourcesCallback(DefaultSources)
            return Room.databaseBuilder(context, WallBaseDatabase::class.java, "wallbase.db")
                .addMigrations(
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12
                )
                .addCallback(callback)
                .fallbackToDestructiveMigration(false)
                .build()
                .also { database ->
                    INSTANCE = database
                }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Preserved historical migration
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.query("SELECT source_id, provider_key, config, icon_url FROM sources").use { cursor ->
                    val idIndex = cursor.getColumnIndex("source_id")
                    val providerIndex = cursor.getColumnIndex("provider_key")
                    if (idIndex == -1 || providerIndex == -1) return@use

                    val configIndex = cursor.getColumnIndex("config")
                    val iconUrlIndex = cursor.getColumnIndex("icon_url")

                    while (cursor.moveToNext()) {
                        val provider = cursor.getString(providerIndex) ?: continue
                        if (provider == SourceKeys.LOCAL) continue

                        val id = cursor.getLong(idIndex)
                        val config = if (configIndex != -1 && !cursor.isNull(configIndex)) {
                            cursor.getString(configIndex)
                        } else {
                            null
                        }
                        val existingIconUrl = if (iconUrlIndex != -1 && !cursor.isNull(iconUrlIndex)) {
                            cursor.getString(iconUrlIndex).takeIf { it.isNotBlank() }
                        } else {
                            null
                        }

                        val resolvedIconUrl = existingIconUrl ?: resolveFaviconUrl(provider, config)
                        db.execSQL(
                            "UPDATE sources SET icon_res = NULL, icon_url = ? WHERE source_id = ?",
                            arrayOf(resolvedIconUrl, id)
                        )
                    }
                }
            }

            private fun resolveFaviconUrl(provider: String, config: String?): String? {
                return when (provider) {
                    SourceKeys.REDDIT -> buildFaviconUrl("reddit.com")
                    SourceKeys.PINTEREST -> {
                        val host = extractHost(config)
                        val domain = when (host) {
                            null -> "pinterest.com"
                            "pin.it" -> "pinterest.com"
                            else -> host
                        }
                        buildFaviconUrl(domain)
                    }
                    SourceKeys.WALLHAVEN -> extractHost(config)?.let(::buildFaviconUrl)
                    SourceKeys.WEBSITES -> extractHost(config)?.let(::buildFaviconUrl)
                    else -> null
                }
            }

            private fun extractHost(config: String?): String? {
                if (config.isNullOrBlank()) return null
                val normalized = config.trim()
                val candidate = if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
                    normalized
                } else {
                    "https://$normalized"
                }
                return runCatching { URI(candidate).host }
                    .getOrNull()
                    ?.lowercase(Locale.ROOT)
                    ?.removePrefix("www.")
                    ?.takeIf { it.isNotBlank() }
            }

            private fun buildFaviconUrl(host: String): String {
                val sanitizedHost = host.removePrefix("www.").ifBlank { host }
                return "https://www.google.com/s2/favicons?sz=128&domain=$sanitizedHost"
            }
        }

        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE wallpapers ADD COLUMN crop_settings TEXT")
            }
        }

        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE wallpapers ADD COLUMN edit_settings TEXT")
                db.query("SELECT wallpaper_id, crop_settings FROM wallpapers WHERE crop_settings IS NOT NULL")
                    .use { cursor ->
                        val idIndex = cursor.getColumnIndex("wallpaper_id")
                        val cropIndex = cursor.getColumnIndex("crop_settings")
                        if (idIndex == -1 || cropIndex == -1) return@use

                        while (cursor.moveToNext()) {
                            if (cursor.isNull(cropIndex)) continue
                            val id = cursor.getLong(idIndex)
                            val rawCrop = cursor.getString(cropIndex)
                            val cropSettings = WallpaperCropSettings.fromString(rawCrop)
                            if (cropSettings != null) {
                                val sanitized = cropSettings.sanitized()
                                val adjustments = WallpaperAdjustments(
                                    crop = WallpaperCrop.Custom(sanitized)
                                )
                                val json = WallpaperAdjustmentsJson.encode(adjustments)
                                val csv = sanitized.encodeToString()
                                db.execSQL(
                                    "UPDATE wallpapers SET crop_settings = ?, edit_settings = ? WHERE wallpaper_id = ?",
                                    arrayOf(csv, json, id)
                                )
                            }
                        }
                    }
            }
        }

        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO sources
                    (key, provider_key, title, description, icon_res, icon_url, show_in_explore, is_enabled, is_local, config)
                    VALUES
                    ('wallhaven:featured', 'wallhaven', 'Featured wallpapers',
                     'Fresh wallpapers from Wallhaven''s public catalog', NULL,
                     'https://www.google.com/s2/favicons?sz=128&domain=wallhaven.cc', 1, 1, 0,
                     'https://wallhaven.cc/search?q=wallpapers&purity=100&sorting=toplist')
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO sources
                    (key, provider_key, title, description, icon_res, icon_url, show_in_explore, is_enabled, is_local, config)
                    VALUES
                    ('reddit:wallpapers', 'reddit', 'r/wallpapers',
                     'Top posts from r/wallpapers', NULL,
                     'https://www.google.com/s2/favicons?sz=128&domain=reddit.com', 1, 1, 0,
                     'wallpapers')
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE wallpapers ADD COLUMN custom_title TEXT")
            }
        }

        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS categories (
                        category_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        sort_order INTEGER NOT NULL DEFAULT 0,
                        is_preset INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_categories_title ON categories (title)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_sort_order_title ON categories (sort_order, title)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS category_wallpaper_cross_ref (
                        category_id INTEGER NOT NULL,
                        wallpaper_id INTEGER NOT NULL,
                        PRIMARY KEY(category_id, wallpaper_id),
                        FOREIGN KEY(category_id) REFERENCES categories(category_id) ON DELETE CASCADE,
                        FOREIGN KEY(wallpaper_id) REFERENCES wallpapers(wallpaper_id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_category_wallpaper_cross_ref_wallpaper_id ON category_wallpaper_cross_ref (wallpaper_id)")

                preloadCategories(db)
            }
        }

        private fun preloadCategories(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            val presets = listOf("Anime", "Nature", "AMOLED", "Minimal", "Art")
            presets.forEachIndexed { index, name ->
                db.execSQL(
                    "INSERT OR IGNORE INTO categories (title, sort_order, is_preset, created_at, updated_at) VALUES (?, ?, 1, ?, ?)",
                    arrayOf(name, index, now, now)
                )
            }
        }

        private fun preloadSources(db: SupportSQLiteDatabase, seeds: List<SourceSeed>) {
            db.beginTransaction()
            try {
                seeds.forEach { seed ->
                    db.execSQL(
                        "INSERT INTO sources (key, provider_key, title, description, icon_res, icon_url, show_in_explore, is_enabled, is_local, config) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        arrayOf(
                            seed.key,
                            seed.providerKey,
                            seed.title,
                            seed.description,
                            seed.iconRes,
                            seed.iconUrl,
                            if (seed.showInExplore) 1 else 0,
                            if (seed.enabledByDefault) 1 else 0,
                            if (seed.isLocal) 1 else 0,
                            seed.config
                        )
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private class DefaultSourcesCallback(
        private val seeds: List<SourceSeed>,
    ) : Callback() {

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            preloadSources(db, seeds)
            preloadCategories(db)
        }
    }
}
