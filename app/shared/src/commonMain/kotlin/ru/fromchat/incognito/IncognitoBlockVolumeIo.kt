package ru.fromchat.incognito

internal interface IncognitoBlockVolumeIo {
    fun readAt(fileOffset: Long, length: Int): ByteArray

    fun writeAt(fileOffset: Long, data: ByteArray)

    fun setPhysicalLength(length: Long)

    fun physicalLength(): Long

    fun fsync()
}

internal expect fun openIncognitoBlockVolumeIo(containerPath: String): IncognitoBlockVolumeIo
