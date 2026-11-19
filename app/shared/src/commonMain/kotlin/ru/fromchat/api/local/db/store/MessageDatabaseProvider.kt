package ru.fromchat.api.local.db.store

import app.cash.sqldelight.db.SqlDriver
import ru.fromchat.api.local.db.ensureMessageDatabaseSchema
import ru.fromchat.api.local.db.isSqliteBusy
import ru.fromchat.api.local.db.MessageDatabaseConcurrency
import ru.fromchat.api.local.db.rebindUnboundMigrationInstance
import ru.fromchat.api.local.db.withMessageDatabaseOpenMutex
import ru.fromchat.api.local.db.withMessageDatabaseLock
import ru.fromchat.db.MessageDatabase

/**
 * Platform-agnostic access to the SQLDelight [MessageDatabase].
 */
expect fun provideMessageDatabaseDriver(): SqlDriver

object MessageDatabaseProvider {
    private var driver: SqlDriver? = null
    private var databaseHolder: MessageDatabase? = null

    val database: MessageDatabase
        get() {
            val cached = withMessageDatabaseLock { databaseHolder }
            if (cached != null) return cached
            val opened = getOrOpenDriver()
            return withMessageDatabaseLock {
                databaseHolder ?: MessageDatabase(opened).also { databaseHolder = it }
            }
        }

    internal fun <T> withOpenDriver(block: (SqlDriver) -> T): T = block(getOrOpenDriver())

    /**
     * Closes the open SQLite connection. Required before deleting `cacheDir/fromchat/`
     * (otherwise the next write hits SQLITE_READONLY_DBMOVED).
     */
    fun closeAndReset() {
        val toClose = withMessageDatabaseLock {
            databaseHolder = null
            val closing = driver
            driver = null
            closing
        }
        runCatching { toClose?.close() }
    }

    /** Runs [block] once; on a stale connection after cache wipe, resets and retries. */
    internal fun <T> withDatabaseRecover(block: () -> T): T {
        try {
            return block()
        } catch (e: Exception) {
            if (isSqliteBusy(e)) {
                MessageDatabaseConcurrency.notifySqliteBusy(e)
                throw e
            }
            if (!isStaleSqliteConnection(e)) throw e
            closeAndReset()
            return block()
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
            val opened = getOrOpenDriver()
            withMessageDatabaseLock {
                rebindUnboundMigrationInstance(opened, targetInstanceId)
            }
        }
    }

    /** Opens the driver without holding [withMessageDatabaseLock] while the SQLite thread is blocked. */
    private fun getOrOpenDriver(): SqlDriver {
        val existing = withMessageDatabaseLock { driver }
        if (existing != null) return existing
        return withMessageDatabaseOpenMutex {
            val again = withMessageDatabaseLock { driver }
            if (again != null) return@withMessageDatabaseOpenMutex again
            val opened = provideMessageDatabaseDriver()
            ensureMessageDatabaseSchema(opened)
            withMessageDatabaseLock {
                val raced = driver
                if (raced != null) {
                    runCatching { opened.close() }
                    raced
                } else {
                    driver = opened
                    opened
                }
            }
        }
    }
}
