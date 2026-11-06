package ru.fromchat.incognito

/** Raw host filesystem access under the incognito session directory only. */
internal expect object IncognitoHostRawFs {
    fun attachRoot(absoluteRoot: String)

    fun detachRoot()

    fun readBytes(absolutePath: String): ByteArray

    fun writeBytes(absolutePath: String, bytes: ByteArray)

    fun delete(absolutePath: String)

    fun exists(absolutePath: String): Boolean

    fun fileSize(absolutePath: String): Long

    fun listFileNamesInDirectory(absoluteDir: String): List<String>

    fun ensureDirectory(absoluteDir: String)

    fun fsync(absolutePath: String)
}
