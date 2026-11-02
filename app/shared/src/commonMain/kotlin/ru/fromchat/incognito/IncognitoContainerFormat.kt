package ru.fromchat.incognito

internal expect fun packWorkDir(workRoot: String): ByteArray

internal expect fun unpackWorkDir(workRoot: String, packed: ByteArray)
