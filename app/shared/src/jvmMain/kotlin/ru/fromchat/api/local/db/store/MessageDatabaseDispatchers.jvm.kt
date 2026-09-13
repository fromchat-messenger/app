package ru.fromchat.api.local.db.store

import kotlinx.coroutines.CoroutineDispatcher

internal actual val messageDatabaseDispatcher: CoroutineDispatcher = SingleThreadSqlDriver.dispatcher
