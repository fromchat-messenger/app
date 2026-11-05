package ru.fromchat.api.local.db.store

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import ru.fromchat.db.MessageDatabase
import ru.fromchat.incognito.IncognitoMode
import ru.fromchat.utils.files.PlatformFileSystem

@OptIn(ExperimentalForeignApi::class)
actual fun provideMessageDatabaseDriver(): SqlDriver {
    if (IncognitoMode.active) {
        return NativeSqliteDriver(
            schema = MessageDatabase.Schema,
            name = ":memory:",
        )
    }
    val base = PlatformFileSystem.getAppCacheDirectory()
    val dir = "$base/fromchat"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
    return NativeSqliteDriver(
        schema = MessageDatabase.Schema,
        name = "message_database.db",
        onConfiguration = { config ->
            config.copy(
                extendedConfig = config.extendedConfig.copy(
                    basePath = dir,
                ),
            )
        },
    )
}
