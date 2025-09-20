package com.joshiminh.wallbase.data.local

import android.content.Context
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class DatabaseBackupManager(
    private val context: Context,
    private val database: WallBaseDatabase = WallBaseDatabase.getInstance(context)
) {

    suspend fun exportBackup(destination: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            if (!dbFile.exists()) {
                error("Database file not found")
            }

            // Flush the write-ahead log
            val sqliteDb = database.openHelper.writableDatabase
            sqliteDb.query("PRAGMA wal_checkpoint(FULL)").use { }

            context.contentResolver.openOutputStream(destination)?.use { output ->
                dbFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: error("Unable to open destination for backup")

            // 👇 Make sure the lambda returns Unit, not something else
            Unit
        }
    }

    suspend fun importBackup(source: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val tempFile = File.createTempFile("wallbase_backup_", ".db", context.cacheDir)

            context.contentResolver.openInputStream(source)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: error("Unable to open backup source")

            val sqliteDb = database.openHelper.writableDatabase
            sqliteDb.execSQL("PRAGMA foreign_keys=OFF")
            sqliteDb.beginTransaction()
            try {
                // Attach using string interpolation instead of bindArgs
                sqliteDb.execSQL("ATTACH DATABASE '${tempFile.absolutePath}' AS backup")
                try {
                    DATA_TABLES.forEach { table ->
                        sqliteDb.execSQL("DELETE FROM $table")
                        if (sqliteDb.hasTable("backup", table)) {
                            sqliteDb.execSQL("INSERT INTO $table SELECT * FROM backup.$table")
                        }
                    }

                    sqliteDb.execSQL("DELETE FROM sqlite_sequence")
                    if (sqliteDb.hasTable("backup", "sqlite_sequence")) {
                        sqliteDb.execSQL("INSERT INTO sqlite_sequence SELECT * FROM backup.sqlite_sequence")
                    }

                    sqliteDb.setTransactionSuccessful()
                } finally {
                    runCatching { sqliteDb.execSQL("DETACH DATABASE backup") }
                }
            } finally {
                sqliteDb.endTransaction()
                sqliteDb.execSQL("PRAGMA foreign_keys=ON")
                tempFile.delete()
            }
        }
    }

    private fun SupportSQLiteDatabase.hasTable(databaseName: String, tableName: String): Boolean {
        val cursor = query(
            "SELECT name FROM ${databaseName}.sqlite_master WHERE type='table' AND name=?",
            arrayOf(tableName)
        )
        cursor.use { return it.moveToFirst() }
    }

    companion object {
        private const val DATABASE_NAME = "wallbase.db"
        private val DATA_TABLES = listOf(
            "sources",
            "wallpapers",
            "albums",
            "album_wallpaper_cross_ref"
        )
    }
}