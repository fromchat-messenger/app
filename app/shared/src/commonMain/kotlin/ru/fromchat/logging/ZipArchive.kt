package ru.fromchat.logging

internal data class ZipFileEntry(
    val name: String,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ZipFileEntry

        if (name != other.name) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode() =
        name.hashCode().let { 31 * it + data.contentHashCode() }
}

internal fun buildStoreZipArchive(entries: List<ZipFileEntry>): ByteArray {
    if (entries.isEmpty()) return ByteArray(0)

    val localParts = mutableListOf<ByteArray>()
    val centralParts = mutableListOf<ByteArray>()
    var offset = 0

    entries.forEach { entry ->
        val nameBytes = entry.name.encodeToByteArray()
        val crc = crc32(entry.data)
        val localHeader = buildLocalFileHeader(
            nameBytes = nameBytes,
            crc = crc,
            compressedSize = entry.data.size,
            uncompressedSize = entry.data.size,
        )
        localParts += localHeader
        localParts += entry.data

        centralParts += buildCentralDirectoryHeader(
            nameBytes = nameBytes,
            crc = crc,
            compressedSize = entry.data.size,
            uncompressedSize = entry.data.size,
            localHeaderOffset = offset,
        )
        offset += localHeader.size + entry.data.size
    }

    val centralDirectory = centralParts.fold(ByteArray(0)) { acc, part -> acc + part }
    val endRecord = buildEndOfCentralDirectory(
        entryCount = entries.size,
        centralDirectorySize = centralDirectory.size,
        centralDirectoryOffset = offset,
    )
    return localParts.fold(ByteArray(0)) { acc, part -> acc + part } + centralDirectory + endRecord
}

private fun buildLocalFileHeader(
    nameBytes: ByteArray,
    crc: UInt,
    compressedSize: Int,
    uncompressedSize: Int,
): ByteArray = buildZipRecord(30 + nameBytes.size) {
    writeUInt16(0x0403) // version needed
    writeUInt16(0) // general purpose bit flag
    writeUInt16(0) // compression method: stored
    writeUInt16(0) // last mod file time
    writeUInt16(0) // last mod file date
    writeUInt32(crc.toLong())
    writeUInt32(compressedSize.toLong())
    writeUInt32(uncompressedSize.toLong())
    writeUInt16(nameBytes.size)
    writeUInt16(0) // extra length
    writeBytes(nameBytes)
}

private fun buildCentralDirectoryHeader(
    nameBytes: ByteArray,
    crc: UInt,
    compressedSize: Int,
    uncompressedSize: Int,
    localHeaderOffset: Int,
): ByteArray = buildZipRecord(46 + nameBytes.size) {
    writeUInt16(0x0314) // version made by
    writeUInt16(0x0403) // version needed
    writeUInt16(0)
    writeUInt16(0)
    writeUInt16(0)
    writeUInt16(0)
    writeUInt32(crc.toLong())
    writeUInt32(compressedSize.toLong())
    writeUInt32(uncompressedSize.toLong())
    writeUInt16(nameBytes.size)
    writeUInt16(0)
    writeUInt16(0)
    writeUInt16(0)
    writeUInt16(0)
    writeUInt32(localHeaderOffset.toLong())
    writeBytes(nameBytes)
}

private fun buildEndOfCentralDirectory(
    entryCount: Int,
    centralDirectorySize: Int,
    centralDirectoryOffset: Int,
): ByteArray = buildZipRecord(22) {
    writeUInt16(0)
    writeUInt16(0)
    writeUInt16(entryCount)
    writeUInt16(entryCount)
    writeUInt32(centralDirectorySize.toLong())
    writeUInt32(centralDirectoryOffset.toLong())
    writeUInt16(0)
}

private class ZipBufferBuilder(val bytes: ByteArray) {
    var index = 0
        private set

    fun writeUInt16(value: Int) {
        bytes[index++] = (value and 0xFF).toByte()
        bytes[index++] = ((value shr 8) and 0xFF).toByte()
    }

    fun writeUInt32(value: Long) {
        bytes[index++] = (value and 0xFF).toByte()
        bytes[index++] = ((value shr 8) and 0xFF).toByte()
        bytes[index++] = ((value shr 16) and 0xFF).toByte()
        bytes[index++] = ((value shr 24) and 0xFF).toByte()
    }

    fun writeBytes(data: ByteArray) {
        data.copyInto(bytes, index)
        index += data.size
    }
}

private inline fun buildZipRecord(size: Int, block: ZipBufferBuilder.() -> Unit): ByteArray {
    val builder = ZipBufferBuilder(ByteArray(size))
    builder.block()
    check(builder.index == size) { "ZIP record size mismatch: expected $size, wrote ${builder.index}" }
    return builder.bytes
}

private fun crc32(data: ByteArray): UInt {
    var crc = 0xFFFF_FFFFu
    for (byte in data) {
        crc = crc xor byte.toUInt()
        repeat(8) {
            crc = if (crc and 1u != 0u) {
                (crc shr 1) xor 0xEDB8_8320u
            } else {
                crc shr 1
            }
        }
    }
    return crc.inv()
}
