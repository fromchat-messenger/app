package ru.fromchat.api.local.db

import ru.fromchat.Logger

fun isSqliteBusy(throwable: Throwable): Boolean {
    var current: Throwable? = throwable
    while (current != null) {
        val message = current.message.orEmpty()
        if (message.contains("SQLITE_BUSY", ignoreCase = true)) return true
        if (message.contains("database is locked", ignoreCase = true)) return true
        if (message.contains("database file is locked", ignoreCase = true)) return true
        current = current.cause
    }
    return false
}

internal fun <T> withSqliteBusyGuard(block: () -> T): T {
    try {
        return block()
    } catch (e: Throwable) {
        if (isSqliteBusy(e)) {
            Logger.w("MessageDatabase", "SQLite busy", e)
            MessageDatabaseConcurrency.notifySqliteBusy(e)
        }
        throw e
    }
}
