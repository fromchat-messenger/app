package ru.fromchat.incognito

/**
 * Userspace encrypted block device: random-access plaintext byte ranges backed by
 * AES-GCM encrypted fixed blocks in a single container file.
 */
internal class IncognitoBlockVolume(
    private val key: ByteArray,
    private val io: IncognitoBlockVolumeIo,
) {
    private val lock = Any()

    fun logicalSize(): Long = synchronized(lock) {
        val physical = io.physicalLength()
        if (physical == 0L) return 0L
        val blocks = physical / IncognitoBlockCrypto.ENCRYPTED_BLOCK_SIZE
        blocks * IncognitoBlockCrypto.PLAIN_BLOCK_SIZE
    }

    fun read(byteOffset: Long, length: Int): ByteArray = synchronized(lock) {
        if (length <= 0) return ByteArray(0)
        val end = byteOffset + length
        val logical = logicalSize().coerceAtLeast(end)
        if (byteOffset >= logical) return ByteArray(0)
        val readLen = (end.coerceAtMost(logical) - byteOffset).toInt()
        val out = ByteArray(readLen)
        var copied = 0
        var offset = byteOffset
        while (copied < readLen) {
            val blockIndex = offset / IncognitoBlockCrypto.PLAIN_BLOCK_SIZE
            val blockStart = blockIndex * IncognitoBlockCrypto.PLAIN_BLOCK_SIZE
            val within = (offset - blockStart).toInt()
            val plain = readPlainBlock(blockIndex)
            val take = minOf(plain.size - within, readLen - copied)
            plain.copyInto(out, copied, within, within + take)
            copied += take
            offset += take
        }
        out
    }

    fun write(byteOffset: Long, data: ByteArray) = synchronized(lock) {
        if (data.isEmpty()) return
        val end = byteOffset + data.size
        ensureLogicalSize(end)
        var written = 0
        var offset = byteOffset
        while (written < data.size) {
            val blockIndex = offset / IncognitoBlockCrypto.PLAIN_BLOCK_SIZE
            val blockStart = blockIndex * IncognitoBlockCrypto.PLAIN_BLOCK_SIZE
            val within = (offset - blockStart).toInt()
            val plain = readPlainBlock(blockIndex)
            val take = minOf(plain.size - within, data.size - written)
            data.copyInto(plain, within, written, written + take)
            writePlainBlock(blockIndex, plain)
            written += take
            offset += take
        }
    }

    fun fsync() = synchronized(lock) { io.fsync() }

    /** Shrinks or grows the on-disk container to fit [logicalByteCount] (rounded up to a block). */
    fun truncateToLogicalByteCount(logicalByteCount: Long) = synchronized(lock) {
        val logical = logicalByteCount.coerceAtLeast(0L)
        val neededBlocks = (logical + IncognitoBlockCrypto.PLAIN_BLOCK_SIZE - 1) /
            IncognitoBlockCrypto.PLAIN_BLOCK_SIZE
        val physical = neededBlocks * IncognitoBlockCrypto.ENCRYPTED_BLOCK_SIZE
        io.setPhysicalLength(physical)
    }

    fun logicalBytesInUse(): Long = synchronized(lock) {
        val physical = io.physicalLength()
        if (physical == 0L) return 0L
        val blocks = (physical + IncognitoBlockCrypto.ENCRYPTED_BLOCK_SIZE - 1) /
            IncognitoBlockCrypto.ENCRYPTED_BLOCK_SIZE
        blocks * IncognitoBlockCrypto.PLAIN_BLOCK_SIZE
    }

    private fun ensureLogicalSize(endExclusive: Long) {
        val neededBlocks = (endExclusive + IncognitoBlockCrypto.PLAIN_BLOCK_SIZE - 1) /
            IncognitoBlockCrypto.PLAIN_BLOCK_SIZE
        val currentBlocks = io.physicalLength() / IncognitoBlockCrypto.ENCRYPTED_BLOCK_SIZE
        for (blockIndex in currentBlocks until neededBlocks) {
            writePlainBlock(blockIndex, ByteArray(IncognitoBlockCrypto.PLAIN_BLOCK_SIZE))
        }
        val physical = neededBlocks * IncognitoBlockCrypto.ENCRYPTED_BLOCK_SIZE
        if (io.physicalLength() < physical) {
            io.setPhysicalLength(physical)
        }
    }

    private fun readPlainBlock(blockIndex: Long): ByteArray {
        val fileOffset = blockIndex * IncognitoBlockCrypto.ENCRYPTED_BLOCK_SIZE
        if (fileOffset >= io.physicalLength()) {
            return ByteArray(IncognitoBlockCrypto.PLAIN_BLOCK_SIZE)
        }
        val encrypted = io.readAt(fileOffset, IncognitoBlockCrypto.ENCRYPTED_BLOCK_SIZE)
        if (encrypted.size < IncognitoBlockCrypto.ENCRYPTED_BLOCK_SIZE) {
            return ByteArray(IncognitoBlockCrypto.PLAIN_BLOCK_SIZE)
        }
        return IncognitoBlockCrypto.decryptBlock(key, encrypted)
    }

    private fun writePlainBlock(blockIndex: Long, plaintext: ByteArray) {
        require(plaintext.size == IncognitoBlockCrypto.PLAIN_BLOCK_SIZE)
        val encrypted = IncognitoBlockCrypto.encryptBlock(key, plaintext)
        require(encrypted.size == IncognitoBlockCrypto.ENCRYPTED_BLOCK_SIZE) {
            "Unexpected encrypted block size ${encrypted.size}, expected ${IncognitoBlockCrypto.ENCRYPTED_BLOCK_SIZE}"
        }
        val fileOffset = blockIndex * IncognitoBlockCrypto.ENCRYPTED_BLOCK_SIZE
        io.writeAt(fileOffset, encrypted)
    }
}
