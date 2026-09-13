package ru.fromchat.api.local.db.store

import kotlinx.coroutines.CoroutineDispatcher

/** Dispatcher for coroutines that read or write the message database. */
internal expect val messageDatabaseDispatcher: CoroutineDispatcher

/** Public accessor for modules outside `:shared` (e.g. desktop bootstrap). */
val messageDatabaseCoroutineDispatcher: CoroutineDispatcher
    get() = messageDatabaseDispatcher
