package ru.fromchat.incognito

private val incognitoVolumeLock = Any()

internal actual fun <T> withIncognitoVolumeLock(block: () -> T): T =
    synchronized(incognitoVolumeLock, block)
