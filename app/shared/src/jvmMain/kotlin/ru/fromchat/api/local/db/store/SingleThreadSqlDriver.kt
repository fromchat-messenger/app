package ru.fromchat.api.local.db.store

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import ru.fromchat.api.local.db.withSqliteBusyGuard
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * JDBC SQLite connections are not thread-safe. All access is serialized on one background thread.
 */
internal class SingleThreadSqlDriver(
    private val delegate: SqlDriver,
) : SqlDriver {
    private fun <T> onDbThread(block: () -> T): T {
        if (Thread.currentThread() == dbThread.get()) {
            return withSqliteBusyGuard(block)
        }
        return executor.submit(
            Callable { withSqliteBusyGuard(block) },
        ).get()
    }

    override fun close() = onDbThread { delegate.close() }

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> = onDbThread { delegate.execute(identifier, sql, parameters, binders) }

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> = onDbThread { delegate.executeQuery(identifier, sql, mapper, parameters, binders) }

    override fun newTransaction(): QueryResult<Transacter.Transaction> = onDbThread { delegate.newTransaction() }

    override fun currentTransaction(): Transacter.Transaction? = onDbThread { delegate.currentTransaction() }

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) =
        onDbThread { delegate.addListener(*queryKeys, listener = listener) }

    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) =
        onDbThread { delegate.removeListener(*queryKeys, listener = listener) }

    override fun notifyListeners(vararg queryKeys: String) =
        onDbThread { delegate.notifyListeners(*queryKeys) }

    companion object {
        private val dbThread = AtomicReference<Thread>()
        private val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "fromchat-sqlite").apply {
                isDaemon = true
                dbThread.set(this)
            }
        }

        val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()

        fun <T> run(block: () -> T): T {
            if (Thread.currentThread() == dbThread.get()) {
                return block()
            }
            return executor.submit(Callable(block)).get()
        }
    }
}
