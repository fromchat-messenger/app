package ru.fromchat.incognito

/**
 * Ephemeral incognito session directory on the host filesystem.
 * Payload files use random names; logical paths live in an encrypted map file ([containerFilePath]).
 */
internal expect object IncognitoContainer {
    val containerFilePath: String?

    /** Logical app data root used for path routing (not a plaintext directory on disk). */
    val workRootPath: String?

    fun open(key: ByteArray, volumeDirectory: String)

    fun syncToContainer(key: ByteArray)

    fun closeAndWipe(key: ByteArray?)

    fun scheduleSyncAfterWrite(key: ByteArray)
}
