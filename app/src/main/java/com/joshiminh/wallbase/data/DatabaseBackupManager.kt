package com.joshiminh.wallbase.data

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
            require(dbFile.exists()) { "Database file not found" }

            val sqliteDb = database.openHelper.writableDatabase
            // Flush WAL to the main DB file
            sqliteDb.execSQL("PRAGMA wal_checkpoint(FULL)")

            val tempFile = File.createTempFile("wallbase_export_", ".db", context.cacheDir)
            var usedVacuum: Boolean

            try {
                // Try VACUUM INTO for a compact, safe snapshot (Android 9+/SQLite 3.27+)
                val quotedPath = tempFile.absolutePath.replace("'", "''")
                usedVacuum = runCatching {
                    sqliteDb.execSQL("VACUUM INTO '$quotedPath'")
                }.isSuccess

                val sourceFile = if (usedVacuum) tempFile else dbFile

                context.contentResolver.openOutputStream(destination)?.use { output ->
                    sourceFile.inputStream().use { input ->
                        // Ignore Long result; we only need the side-effect
                        input.copyTo(output)
                    }
                } ?: error("Unable to open destination for backup")
            } finally {
                if (!tempFile.delete()) tempFile.deleteOnExit()
            }

            // Ensure Result<Unit>
            return@runCatching
        }
    }

    suspend fun importBackup(source: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val tempFile = File.createTempFile("wallbase_backup_", ".db", context.cacheDir)

            context.contentResolver.openInputStream(source)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    // Ignore Long result; we only need the side-effect
                    input.copyTo(output)
                }
            } ?: error("Unable to open backup source")

            val sqliteDb = database.openHelper.writableDatabase
            sqliteDb.execSQL("PRAGMA foreign_keys=OFF")
            sqliteDb.beginTransaction()
            try {
                val backupPath = tempFile.absolutePath.replace("'", "''")
                sqliteDb.execSQL("ATTACH DATABASE '$backupPath' AS backup")
                try {
                    // Clear and copy table-by-table to avoid schema clashes
                    DATA_TABLES.forEach { table ->
                        sqliteDb.execSQL("DELETE FROM $table")
                        if (sqliteDb.hasTable("backup", table)) {
                            sqliteDb.execSQL("INSERT INTO $table SELECT * FROM backup.$table")
                        }
                    }

                    // Restore sqlite_sequence (autoincrement counters) if present
                    if (sqliteDb.hasTable("main", "sqlite_sequence")) {
                        sqliteDb.execSQL("DELETE FROM sqlite_sequence")
                    }
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

            // Ensure Result<Unit>
            return@runCatching
        }
    }

    private fun SupportSQLiteDatabase.hasTable(databaseName: String, tableName: String): Boolean {
        query(
            "SELECT name FROM $databaseName.sqlite_master WHERE type='table' AND name=?",
            arrayOf(tableName)
        ).use { cursor ->
            return cursor.moveToFirst()
        }
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