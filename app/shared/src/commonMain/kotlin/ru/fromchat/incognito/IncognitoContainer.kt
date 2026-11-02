package ru.fromchat.incognito

/**
 * Single on-disk container file ([containerFilePath]) holding a sealed encrypted filesystem snapshot.
 * While the session runs, [workRootPath] is a plaintext working tree (e.g. tmpfs on Linux desktop).
 */
internal expect object IncognitoContainer {
    val containerFilePath: String?
    val workRootPath: String?

    fun open(key: ByteArray, volumeDirectory: String)

    /** Writes the working tree into the sealed container file. */
    fun syncToContainer(key: ByteArray)

    fun closeAndWipe(key: ByteArray?)

    fun scheduleSyncAfterWrite(key: ByteArray)
}
