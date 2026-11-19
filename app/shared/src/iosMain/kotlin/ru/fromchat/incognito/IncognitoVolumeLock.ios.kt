package ru.fromchat.incognito

import platform.Foundation.NSRecursiveLock

private val lock = NSRecursiveLock()

internal actual fun <T> withIncognitoVolumeLock(block: () -> T): T {
    lock.lock()
    try {
        return block()
    } finally {
        lock.unlock()
    }
}
