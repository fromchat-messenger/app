package ru.fromchat.incognito

/** Raw host filesystem access under the incognito session directory only. */
internal expect object IncognitoHostRawFs {
    fun attachRoot(absoluteRoot: String)

    fun detachRoot()

    fun readBytes(absolutePath: String): ByteArray

    fun readRange(absolutePath: String, offset: Long, length: Int): ByteArray

    fun writeBytes(absolutePath: String, bytes: ByteArray)

    fun writeBytesAt(absolutePath: String, offset: Long, bytes: ByteArray)

    fun appendBytes(absolutePath: String, bytes: ByteArray)

    fun delete(absolutePath: String)

    fun exists(absolutePath: String): Boolean

    fun fileSize(absolutePath: String): Long

    fun listFileNamesInDirectory(absoluteDir: String): List<String>

    fun ensureDirectory(absoluteDir: String)

    fun fsync(absolutePath: String)
}
