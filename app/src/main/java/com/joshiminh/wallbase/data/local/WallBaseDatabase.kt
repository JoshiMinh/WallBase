package com.joshiminh.wallbase.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.joshiminh.wallbase.data.local.dao.AlbumDao
import com.joshiminh.wallbase.data.local.dao.SourceDao
import com.joshiminh.wallbase.data.local.dao.WallpaperDao
import com.joshiminh.wallbase.data.local.entity.AlbumEntity
import com.joshiminh.wallbase.data.local.entity.AlbumWallpaperCrossRef
import com.joshiminh.wallbase.data.local.entity.SourceEntity
import com.joshiminh.wallbase.data.local.entity.WallpaperEntity
import com.joshiminh.wallbase.data.source.DefaultSources
import com.joshiminh.wallbase.data.source.SourceSeed

@Database(
    entities = [
        AlbumEntity::class,
        WallpaperEntity::class,
        AlbumWallpaperCrossRef::class,
        SourceEntity::class
    ],
    version = 2,
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
                .addCallback(object : RoomDatabase.Callback() {
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
                        "INSERT INTO sources (key, provider_key, title, description, icon_res, show_in_explore, is_enabled, is_local, config) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        arrayOf(
                            seed.key,
                            seed.providerKey,
                            seed.title,
                            seed.description,
                            seed.icon,
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
