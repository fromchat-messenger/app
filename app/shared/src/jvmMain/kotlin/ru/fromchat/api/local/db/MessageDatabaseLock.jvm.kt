package ru.fromchat.api.local.db

internal actual fun <T> withMessageDatabaseLock(block: () -> T): T =
    synchronized(MessageDatabaseLock) { block() }

private object MessageDatabaseLock
