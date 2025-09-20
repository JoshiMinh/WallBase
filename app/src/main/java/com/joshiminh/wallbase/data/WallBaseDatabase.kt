package com.joshiminh.wallbase.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.joshiminh.wallbase.data.dao.AlbumDao
import com.joshiminh.wallbase.data.dao.SourceDao
import com.joshiminh.wallbase.data.dao.WallpaperDao
import com.joshiminh.wallbase.data.entity.album.AlbumEntity
import com.joshiminh.wallbase.data.entity.album.AlbumWallpaperCrossRef
import com.joshiminh.wallbase.data.entity.source.SourceEntity
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperEntity
import com.joshiminh.wallbase.data.entity.source.DefaultSources
import com.joshiminh.wallbase.data.entity.source.SourceSeed

@Database(
    entities = [
        AlbumEntity::class,
        WallpaperEntity::class,
        AlbumWallpaperCrossRef::class,
        SourceEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class WallBaseDatabase : RoomDatabase() {

    abstract fun sourceDao(): SourceDao
    abstract fun wallpaperDao(): WallpaperDao
    abstract fun albumDao(): AlbumDao

    companion object {
        @Volatile
        private var INSTANCE: WallBaseDatabase? = null

        fun getInstance(context: Context): WallBaseDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): WallBaseDatabase {
            return Room.databaseBuilder(context, WallBaseDatabase::class.java, "wallbase.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        preloadSources(db, DefaultSources)
                    }
                })
                .fallbackToDestructiveMigration()
                .build()
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
}