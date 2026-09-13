package ru.fromchat.api.local.db

object MessageDatabaseConcurrency {
    var onSqliteBusy: ((Throwable) -> Unit)? = null

    fun notifySqliteBusy(throwable: Throwable) {
        onSqliteBusy?.invoke(throwable)
    }
}
