package ru.fromchat.incognito

/**
 * Single encrypted block container on disk ([containerFilePath]).
 * App data paths are stored inside via [IncognitoVolumeFileSystem] (userspace block encryption).
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
