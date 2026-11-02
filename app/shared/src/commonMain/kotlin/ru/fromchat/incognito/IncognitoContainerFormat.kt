package ru.fromchat.incognito

internal expect fun packWorkDir(
    workRoot: String,
    extraRelativeFiles: List<Pair<String, ByteArray>> = emptyList(),
): ByteArray

internal expect fun unpackWorkDir(workRoot: String, packed: ByteArray)
