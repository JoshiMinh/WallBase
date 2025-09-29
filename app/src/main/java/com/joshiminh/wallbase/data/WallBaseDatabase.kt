@file:Suppress("TYPE_INTERSECTION_AS_REIFIED_WARNING")

package com.joshiminh.wallbase.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.joshiminh.wallbase.data.dao.AlbumDao
import com.joshiminh.wallbase.data.dao.SourceDao
import com.joshiminh.wallbase.data.dao.RotationScheduleDao
import com.joshiminh.wallbase.data.dao.WallpaperDao
import com.joshiminh.wallbase.data.entity.album.AlbumEntity
import com.joshiminh.wallbase.data.entity.album.AlbumWallpaperCrossRef
import com.joshiminh.wallbase.data.entity.source.DefaultSources
import com.joshiminh.wallbase.data.entity.source.SourceEntity
import com.joshiminh.wallbase.data.entity.source.SourceSeed
import com.joshiminh.wallbase.data.entity.source.SourceEntity.Companion.fromSeed
import com.joshiminh.wallbase.data.entity.source.SourceKeys
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperEntity
import com.joshiminh.wallbase.data.entity.rotation.RotationScheduleEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URI
import java.util.Locale

@Database(
    entities = [
        AlbumEntity::class,
        WallpaperEntity::class,
        AlbumWallpaperCrossRef::class,
        SourceEntity::class,
        RotationScheduleEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class WallBaseDatabase : RoomDatabase() {

    abstract fun sourceDao(): SourceDao
    abstract fun wallpaperDao(): WallpaperDao
    abstract fun albumDao(): AlbumDao
    abstract fun rotationScheduleDao(): RotationScheduleDao

    companion object {
        @Volatile
        private var INSTANCE: WallBaseDatabase? = null

        fun getInstance(context: Context): WallBaseDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext)
            }
        }

        private val databaseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private fun buildDatabase(context: Context): WallBaseDatabase {
            val callback = DefaultSourcesCallback(DefaultSources, databaseScope)
            return Room.databaseBuilder(context, WallBaseDatabase::class.java, "wallbase.db")
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .addCallback(callback)
                .fallbackToDestructiveMigration(false)
                .build()
                .also { database ->
                    INSTANCE = database
                    callback.attach(database)
                }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS rotation_schedules (
                        schedule_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        album_id INTEGER NOT NULL,
                        interval_minutes INTEGER NOT NULL,
                        target TEXT NOT NULL,
                        is_enabled INTEGER NOT NULL,
                        last_applied_at INTEGER,
                        last_wallpaper_id INTEGER,
                        FOREIGN KEY(album_id) REFERENCES albums(album_id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_rotation_schedules_album_id ON rotation_schedules(album_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_rotation_schedules_is_enabled ON rotation_schedules(is_enabled)")
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
                    SourceKeys.DANBOORU -> extractHost(config)?.let(::buildFaviconUrl)
                    SourceKeys.UNSPLASH -> extractHost(config)?.let(::buildFaviconUrl)
                    SourceKeys.ALPHA_CODERS -> extractHost(config)?.let(::buildFaviconUrl)
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
        private val scope: CoroutineScope,
    ) : Callback() {

        @Volatile
        private var database: WallBaseDatabase? = null

        fun attach(database: WallBaseDatabase) {
            this.database = database
        }

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            preloadSources(db, seeds)
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            scope.launch {
                ensureDefaultSources()
            }
        }

        private suspend fun ensureDefaultSources() {
            val database = awaitDatabase()
            val sourceDao = database.sourceDao()
            val existingKeys = sourceDao.getSourceKeys().toSet()
            val missingSources = seeds
                .asSequence()
                .filter { it.key !in existingKeys }
                .map(::fromSeed)
                .toList()

            if (missingSources.isNotEmpty()) {
                sourceDao.insertSourcesIfMissing(missingSources)
            }
        }

        private suspend fun awaitDatabase(): WallBaseDatabase {
            while (true) {
                val current = database
                if (current != null) {
                    return current
                }
                delay(10)
            }
        }
    }
}