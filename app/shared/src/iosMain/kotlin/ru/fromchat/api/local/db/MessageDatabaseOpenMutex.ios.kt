package ru.fromchat.api.local.db

import platform.Foundation.NSRecursiveLock

private val lock = NSRecursiveLock()

internal actual fun <T> withMessageDatabaseOpenMutex(block: () -> T): T {
    lock.lock()
    try {
        return block()
    } finally {
        lock.unlock()
    }
}
