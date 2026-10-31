package ru.fromchat.api.local.db.store

import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import ru.fromchat.utils.UtilsLibrary
import ru.fromchat.db.MessageDatabase
import java.io.File

actual fun provideMessageDatabaseDriver(): SqlDriver {
    val context = UtilsLibrary.context
    val fromchatDir = File(context.cacheDir, "fromchat").apply { mkdirs() }
    val dbFile = File(fromchatDir, "message_database.db")
    removeLegacyDatabaseFiles(context.getDatabasePath("message_database.db"))
    return AndroidSqliteDriver(
        schema = MessageDatabase.Schema,
        context = context,
        name = dbFile.absolutePath,
        callback = DiffOnlyDatabaseCallback(),
    )
}

/** Drops the pre-cache-dir DB; message cache lives under cache only. */
private fun removeLegacyDatabaseFiles(legacyDb: File) {
    if (!legacyDb.exists()) return
    runCatching { legacyDb.delete() }
    runCatching { File("${legacyDb.path}-journal").delete() }
    runCatching { File("${legacyDb.path}-wal").delete() }
    runCatching { File("${legacyDb.path}-shm").delete() }
}

/**
 * SQLDelight [user_version] is not used for migrations; [ru.fromchat.api.local.db.ensureMessageDatabaseSchema] diffs structure.
 */
private class DiffOnlyDatabaseCallback : AndroidSqliteDriver.Callback(MessageDatabase.Schema) {
    override fun onCreate(db: SupportSQLiteDatabase) {
        // Schema is created by structural diff on first open.
    }

    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No version-based migration.
    }
}
