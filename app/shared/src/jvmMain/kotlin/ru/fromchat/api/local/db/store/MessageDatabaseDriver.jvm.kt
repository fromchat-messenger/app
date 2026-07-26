package ru.fromchat.api.local.db.store

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import ru.fromchat.db.MessageDatabase

actual fun provideMessageDatabaseDriver(): SqlDriver {
    val home = System.getProperty("user.home")
    val dir = if (!home.isNullOrBlank()) {
        File(home, ".fromchat/cache/fromchat")
    } else {
        File(System.getProperty("java.io.tmpdir"), "fromchat")
    }
    dir.mkdirs()
    val dbFile = File(dir, "message_database.db")
    val needsCreate = !dbFile.exists()
    val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
    if (needsCreate) {
        MessageDatabase.Schema.create(driver)
    }
    return driver
}
