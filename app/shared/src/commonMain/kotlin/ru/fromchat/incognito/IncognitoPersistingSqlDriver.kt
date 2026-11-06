package ru.fromchat.incognito

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Debounced WAL checkpoint while incognito message DB uses a live on-disk SQLCipher file. */
internal class IncognitoPersistingSqlDriver(
    private val delegate: SqlDriver,
) : SqlDriver by delegate {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val syncMutex = Mutex()
    private var syncScheduled = false

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (app.cash.sqldelight.db.SqlPreparedStatement.() -> Unit)?,
    ): app.cash.sqldelight.db.QueryResult<Long> {
        val result = delegate.execute(identifier, sql, parameters, binders)
        schedulePersistIfMutating(sql)
        return result
    }

    private fun schedulePersistIfMutating(sql: String) {
        if (!IncognitoMode.active) return
        val verb = sql.trimStart().substringBefore(' ').uppercase()
        if (verb !in MUTATING_SQL_VERBS) return
        val key = IncognitoMode.volumeKeyOrNull() ?: return
        scope.launch {
            val shouldRun = syncMutex.withLock {
                if (syncScheduled) {
                    false
                } else {
                    syncScheduled = true
                    true
                }
            }
            if (!shouldRun) return@launch
            delay(SYNC_DEBOUNCE_MS)
            syncMutex.withLock { syncScheduled = false }
            if (!IncognitoMode.active) return@launch
            IncognitoContainer.scheduleSyncAfterWrite(key)
        }
    }

    private companion object {
        val MUTATING_SQL_VERBS = setOf(
            "INSERT",
            "UPDATE",
            "DELETE",
            "REPLACE",
            "CREATE",
            "DROP",
            "ALTER",
        )
        const val SYNC_DEBOUNCE_MS = 400L
    }
}
