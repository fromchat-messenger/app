package ru.fromchat.api.local.db.store

import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import ru.fromchat.db.MessageDatabase
import ru.fromchat.incognito.IncognitoMessageDatabase
import ru.fromchat.incognito.IncognitoMode
import ru.fromchat.incognito.IncognitoPersistingSqlDriver
import ru.fromchat.utils.files.PlatformFileSystem
import ru.fromchat.utils.UtilsLibrary
import java.io.File

actual fun provideMessageDatabaseDriver(): SqlDriver {
    val context = UtilsLibrary.context
    if (IncognitoMode.active) {
        System.loadLibrary("sqlcipher")
        val dbPath = IncognitoMessageDatabase.hostAbsolutePath()
        File(dbPath).parentFile?.mkdirs()
        val factory = SupportOpenHelperFactory(IncognitoMessageDatabase.sqlCipherPassphrase())
        return IncognitoPersistingSqlDriver(
            AndroidSqliteDriver(
                schema = MessageDatabase.Schema,
                context = context,
                name = dbPath,
                factory = factory,
                callback = DiffOnlyDatabaseCallback(),
            ),
        )
    }
    val fromchatDir = File(PlatformFileSystem.getAppCacheDirectory(), "fromchat").apply { mkdirs() }
    removeLegacyDatabaseFiles(context.getDatabasePath("message_database.db"))
    return AndroidSqliteDriver(
        schema = MessageDatabase.Schema,
        context = context,
        name = File(fromchatDir, "message_database.db").absolutePath,
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
    override fun onCreate(db: SupportSQLiteDatabase) = Unit

    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}
