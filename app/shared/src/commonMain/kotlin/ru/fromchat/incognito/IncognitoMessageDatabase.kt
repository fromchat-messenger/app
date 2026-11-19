package ru.fromchat.incognito

/** Logical DB path inside the incognito volume (opaque host file + SQLCipher, same behavior as normal SQLite files). */
internal object IncognitoMessageDatabase {
    const val RELATIVE_PATH = "fromchat/message_database.db"

    fun hostAbsolutePath(): String =
        IncognitoVolumeFileSystem.resolveOrCreateOpaqueHostAbsolutePath(RELATIVE_PATH)

    fun sqlCipherPassphrase(): ByteArray {
        val key = IncognitoMode.volumeKeyOrNull()
            ?: error("Incognito volume key required for message database")
        return key.copyOf()
    }
}

internal fun checkpointIncognitoMessageDatabase(driver: app.cash.sqldelight.db.SqlDriver) {
    if (!IncognitoMode.active) return
    driver.execute(identifier = null, sql = "PRAGMA wal_checkpoint(FULL)", parameters = 0)
}
