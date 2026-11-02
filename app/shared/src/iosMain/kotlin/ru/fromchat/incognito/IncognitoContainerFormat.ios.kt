package ru.fromchat.incognito

internal actual fun packWorkDir(workRoot: String): ByteArray = ByteArray(0)

internal actual fun unpackWorkDir(workRoot: String, packed: ByteArray) = Unit
