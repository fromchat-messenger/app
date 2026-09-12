package ru.fromchat.api.local.db.store

import app.cash.sqldelight.db.SqlDriver
import ru.fromchat.api.local.db.ensureMessageDatabaseSchema
import ru.fromchat.api.local.db.rebindUnboundMigrationInstance
import ru.fromchat.api.local.db.withMessageDatabaseLock
import ru.fromchat.api.local.db.withSqliteBusyRetry
import ru.fromchat.db.MessageDatabase

/**
 * Platform-agnostic access to the SQLDelight [MessageDatabase].
 */
expect fun provideMessageDatabaseDriver(): SqlDriver

object MessageDatabaseProvider {
    private var driver: SqlDriver? = null
    private var databaseHolder: MessageDatabase? = null

    val database: MessageDatabase
        get() = withMessageDatabaseLock {
            databaseHolder ?: MessageDatabase(openDriverLocked()).also { databaseHolder = it }
        }

    /**
     * Closes the open SQLite connection. Required before deleting `cacheDir/fromchat/`
     * (otherwise the next write hits SQLITE_READONLY_DBMOVED).
     */
    fun closeAndReset() {
        withMessageDatabaseLock {
            databaseHolder = null
            runCatching { driver?.close() }
            driver = null
        }
    }

    /** Runs [block] once; on a stale connection after cache wipe, resets and retries. */
    internal fun <T> withDatabaseRecover(block: () -> T): T =
        withSqliteBusyRetry {
            try {
                block()
            } catch (e: Exception) {
                if (!isStaleSqliteConnection(e)) throw e
                closeAndReset()
                block()
            }
        }

    internal fun isStaleSqliteConnection(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        while (current != null) {
            val message = current.message?.lowercase().orEmpty()
            if (message.contains("readonly") && message.contains("database")) return true
            if (message.contains("sqlite_readonly_dbmoved")) return true
            current = current.cause
        }
        return false
    }

    fun rebindUnboundPartition(targetInstanceId: String) {
        runCatching {
            withMessageDatabaseLock {
                rebindUnboundMigrationInstance(openDriverLocked(), targetInstanceId)
            }
        }
    }

    /** Caller must hold the message-database lock via [withMessageDatabaseLock]. */
    private fun openDriverLocked(): SqlDriver {
        driver?.let { return it }
        return provideMessageDatabaseDriver().also {
            ensureMessageDatabaseSchema(it)
            driver = it
        }
    }
}
