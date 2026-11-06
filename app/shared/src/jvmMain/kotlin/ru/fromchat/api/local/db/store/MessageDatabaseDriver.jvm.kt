package ru.fromchat.api.local.db.store

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ru.fromchat.incognito.IncognitoMessageDatabase
import ru.fromchat.incognito.IncognitoMode
import ru.fromchat.incognito.IncognitoPersistingSqlDriver
import ru.fromchat.utils.files.PlatformFileSystem
import java.io.File
import java.util.Properties

actual fun provideMessageDatabaseDriver(): SqlDriver =
    SingleThreadSqlDriver.run {
        if (IncognitoMode.active) {
            val dbFile = File(IncognitoMessageDatabase.hostAbsolutePath())
            dbFile.parentFile?.mkdirs()
            val hexKey = IncognitoMessageDatabase.sqlCipherPassphrase().joinToString("") { byte ->
                "%02x".format(byte)
            }
            val url = buildString {
                append("jdbc:sqlite:")
                append(dbFile.absolutePath)
                append("?busy_timeout=0&journal_mode=WAL&cipher=sqlcipher&legacy=4&key=")
                append(hexKey)
            }
            // Do not call Schema.create here — [ensureMessageDatabaseSchema] applies the full
            // structural diff (avoids duplicate CREATE TABLE with SQLDelight schema create).
            val jdbc = JdbcSqliteDriver(url, Properties())
            configureJvmSqliteDriver(jdbc)
            return@run SingleThreadSqlDriver(IncognitoPersistingSqlDriver(jdbc))
        }
        val dir = File(PlatformFileSystem.getAppCacheDirectory(), "fromchat")
        dir.mkdirs()
        val dbFile = File(dir, "message_database.db")
        // Schema is aligned by [ensureMessageDatabaseSchema], not SQLDelight's create callback.
        val jdbc = JdbcSqliteDriver(
            "jdbc:sqlite:${dbFile.absolutePath}?busy_timeout=0&journal_mode=WAL",
        )
        configureJvmSqliteDriver(jdbc)
        SingleThreadSqlDriver(jdbc)
    }

private fun configureJvmSqliteDriver(driver: SqlDriver) {
    driver.execute(null, "PRAGMA busy_timeout = 0;", 0)
    driver.execute(null, "PRAGMA journal_mode = WAL;", 0)
    driver.execute(null, "PRAGMA synchronous = NORMAL;", 0)
}
