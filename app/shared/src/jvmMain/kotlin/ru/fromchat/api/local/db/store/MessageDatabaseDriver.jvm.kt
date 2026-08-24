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
    val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
    if (needsCreate) {
        MessageDatabase.Schema.create(driver)
    }
    return driver
}
