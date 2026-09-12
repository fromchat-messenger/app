package ru.fromchat.api.local.db.store

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import ru.fromchat.api.local.db.withSqliteBusyRetry

/**
 * Serializes JDBC SQLite access and retries transient [SQLITE_BUSY] errors.
 * Desktop can hit the DB from multiple threads while the provider lock is not held per query.
 */
internal class SynchronizedRetryingSqlDriver(
    private val delegate: SqlDriver,
) : SqlDriver {
    private val accessLock = Any()

    override fun close() = synchronized(accessLock) { delegate.close() }

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> = synchronized(accessLock) {
        withSqliteBusyRetry { delegate.execute(identifier, sql, parameters, binders) }
    }

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> = synchronized(accessLock) {
        withSqliteBusyRetry { delegate.executeQuery(identifier, sql, mapper, parameters, binders) }
    }

    override fun newTransaction(): QueryResult<Transacter.Transaction> = synchronized(accessLock) {
        withSqliteBusyRetry { delegate.newTransaction() }
    }

    override fun currentTransaction(): Transacter.Transaction? = synchronized(accessLock) {
        delegate.currentTransaction()
    }

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) {
        delegate.addListener(*queryKeys, listener = listener)
    }

    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) {
        delegate.removeListener(*queryKeys, listener = listener)
    }

    override fun notifyListeners(vararg queryKeys: String) {
        delegate.notifyListeners(*queryKeys)
    }
}
