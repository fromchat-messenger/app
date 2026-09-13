package ru.fromchat.api.local.db.store

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val messageDatabaseDispatcher: CoroutineDispatcher = Dispatchers.Default
