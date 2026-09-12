package ru.fromchat.api.local.db.store

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.pr0gramm3r101.utils.files.PlatformFileSystem
import java.io.File
import ru.fromchat.db.MessageDatabase

actual fun provideMessageDatabaseDriver(): SqlDriver {
    val dir = File(PlatformFileSystem.getAppCacheDirectory(), "fromchat")
    dir.mkdirs()
    val dbFile = File(dir, "message_database.db")
    val needsCreate = !dbFile.exists()
    val jdbc = JdbcSqliteDriver(
        "jdbc:sqlite:${dbFile.absolutePath}?busy_timeout=30000&journal_mode=WAL",
    )
    if (needsCreate) {
        MessageDatabase.Schema.create(jdbc)
    }
    configureJvmSqliteDriver(jdbc)
    return SynchronizedRetryingSqlDriver(jdbc)
}

private fun configureJvmSqliteDriver(driver: SqlDriver) {
    driver.execute(null, "PRAGMA busy_timeout = 30000;", 0)
    driver.execute(null, "PRAGMA journal_mode = WAL;", 0)
    driver.execute(null, "PRAGMA synchronous = NORMAL;", 0)
}
