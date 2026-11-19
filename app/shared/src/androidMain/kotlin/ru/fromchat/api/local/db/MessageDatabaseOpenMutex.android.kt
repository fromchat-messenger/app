package ru.fromchat.api.local.db

private val messageDatabaseOpenMutex = Any()

internal actual fun <T> withMessageDatabaseOpenMutex(block: () -> T): T =
    synchronized(messageDatabaseOpenMutex, block)
