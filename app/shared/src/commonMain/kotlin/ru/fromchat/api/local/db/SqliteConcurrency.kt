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

internal fun <T> withSqliteBusyRetry(
    maxAttempts: Int = 8,
    initialDelayMs: Long = 25L,
    block: () -> T,
): T {
    var attempt = 0
    var delayMs = initialDelayMs
    while (true) {
        try {
            return block()
        } catch (e: Throwable) {
            attempt++
            if (!isSqliteBusy(e) || attempt >= maxAttempts) throw e
            Logger.w(
                "MessageDatabase",
                "SQLite busy (attempt $attempt/$maxAttempts), retrying in ${delayMs}ms",
            )
            Thread.sleep(delayMs)
            delayMs = minOf(delayMs * 2, 500L)
        }
    }
}
