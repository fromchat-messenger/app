package ru.fromchat.incognito

import app.cash.sqldelight.db.SqlDriver
import ru.fromchat.api.local.db.store.MessageDatabaseProvider

private const val MESSAGE_DB_RELATIVE = "fromchat/message_database.db"

internal object MessageDatabaseIncognito {
    fun hydrateDriverFromVolume(driver: SqlDriver) {
        if (!IncognitoMode.active) return
        val bytes = IncognitoVolumeFileSystem.read(MESSAGE_DB_RELATIVE)
        if (bytes.isEmpty()) return
        importMessageDatabaseBytes(driver, bytes)
    }

    fun snapshotActiveDatabaseIntoVolume() {
        if (!IncognitoMode.active) return
        MessageDatabaseProvider.withOpenDriver { driver ->
            exportMessageDatabaseBytes(driver)?.let { bytes ->
                IncognitoVolumeFileSystem.write(MESSAGE_DB_RELATIVE, bytes)
            }
        }
    }
}

internal expect fun exportMessageDatabaseBytes(driver: SqlDriver): ByteArray?

internal expect fun importMessageDatabaseBytes(driver: SqlDriver, bytes: ByteArray)
