package ru.fromchat.api.local.db.store

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import ru.fromchat.db.MessageDatabase

@OptIn(ExperimentalForeignApi::class)
actual fun provideMessageDatabaseDriver(): SqlDriver {
    val base = NSFileManager.defaultManager.URLForDirectory(
        directory = NSCachesDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )?.path ?: "."
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

