package ru.fromchat.incognito

import korlibs.crypto.AES
import korlibs.crypto.SHA256

/**
 * AES-XTS length-preserving file encryption (ciphertext size equals plaintext size).
 * Per-file tweak scope isolates random host filenames from each other.
 */
internal object IncognitoXtsCrypto {
    private const val AES_BLOCK = 16
    private const val DATA_UNIT = 512

    fun encrypt(volumeKey: ByteArray, tweakScope: String, plaintext: ByteArray): ByteArray {
        if (plaintext.isEmpty()) return ByteArray(0)
        val xtsKey = deriveXtsKey(volumeKey, tweakScope)
        return transform(xtsKey, plaintext, encrypt = true)
    }

    fun decrypt(volumeKey: ByteArray, tweakScope: String, ciphertext: ByteArray): ByteArray {
        if (ciphertext.isEmpty()) return ByteArray(0)
        val xtsKey = deriveXtsKey(volumeKey, tweakScope)
        return transform(xtsKey, ciphertext, encrypt = false)
    }

    /** XTS encrypt for bytes that begin at [logicalOffset] in a larger logical file. */
    internal fun encryptAtLogicalOffset(
        volumeKey: ByteArray,
        tweakScope: String,
        plaintext: ByteArray,
        logicalOffset: Int,
    ): ByteArray {
        if (plaintext.isEmpty()) return ByteArray(0)
        val xtsKey = deriveXtsKey(volumeKey, tweakScope)
        return transformAtLogicalOffset(xtsKey, plaintext, logicalOffset, encrypt = true)
    }

    /** XTS decrypt for bytes that begin at [logicalOffset] in a larger logical file. */
    internal fun decryptAtLogicalOffset(
        volumeKey: ByteArray,
        tweakScope: String,
        ciphertext: ByteArray,
        logicalOffset: Int,
    ): ByteArray {
        if (ciphertext.isEmpty()) return ByteArray(0)
        val xtsKey = deriveXtsKey(volumeKey, tweakScope)
        return transformAtLogicalOffset(xtsKey, ciphertext, logicalOffset, encrypt = false)
    }

    fun selfTest(volumeKey: ByteArray): Boolean {
        val samples = listOf(
            ByteArray(0),
            byteArrayOf(0x01),
            ByteArray(17) { it.toByte() },
            ByteArray(512) { 0x5A },
            ByteArray(1000) { (it and 0xFF).toByte() },
        )
        for (plain in samples) {
            val enc = encrypt(volumeKey, tweakScope = "self-test", plain)
            if (enc.size != plain.size) return false
            val dec = decrypt(volumeKey, tweakScope = "self-test", enc)
            if (!dec.contentEquals(plain)) return false
        }
        return true
    }

    private fun deriveXtsKey(volumeKey: ByteArray, tweakScope: String): ByteArray {
        val scope = tweakScope.encodeToByteArray()
        val k1 = SHA256.digest(volumeKey + scope + byteArrayOf(1)).bytes
        val k2 = SHA256.digest(volumeKey + scope + byteArrayOf(2)).bytes
        return k1 + k2
    }

    private fun transform(xtsKey: ByteArray, data: ByteArray, encrypt: Boolean): ByteArray =
        transformAtLogicalOffset(xtsKey, data, logicalOffset = 0, encrypt = encrypt)

    private fun transformAtLogicalOffset(
        xtsKey: ByteArray,
        data: ByteArray,
        logicalOffset: Int,
        encrypt: Boolean,
    ): ByteArray {
        require(xtsKey.size == 64) { "XTS-256 requires a 64-byte key" }
        require(logicalOffset >= 0) { "logicalOffset must be non-negative" }
        val out = data.copyOf()
        var bufferOffset = 0
        var logicalPos = logicalOffset
        while (bufferOffset < out.size) {
            val unitIndex = logicalPos / DATA_UNIT
            val misalignInUnit = logicalPos % DATA_UNIT
            val remainingInDataUnit = DATA_UNIT - misalignInUnit
            val unitLength = minOf(remainingInDataUnit, out.size - bufferOffset)
            if (unitLength < AES_BLOCK) {
                xorShortUnit(xtsKey, unitIndex.toLong(), out, bufferOffset, unitLength)
            } else {
                cryptSector(xtsKey, unitIndex.toLong(), out, bufferOffset, unitLength, encrypt)
            }
            bufferOffset += unitLength
            logicalPos += unitLength
        }
        return out
    }

    /** Length-preserving XOR for sub-block payloads (XTS is undefined below 16 bytes). */
    private fun xorShortUnit(
        xtsKey: ByteArray,
        unitIndex: Long,
        buffer: ByteArray,
        unitOffset: Int,
        unitLength: Int,
    ) {
        val k1 = xtsKey.copyOfRange(0, 32)
        val stream = ByteArray(AES_BLOCK)
        writeUnitIndex(stream, unitIndex)
        AES(k1).encryptBlock(stream, 0)
        for (i in 0 until unitLength) {
            buffer[unitOffset + i] =
                (buffer[unitOffset + i].toInt() xor stream[i].toInt()).toByte()
        }
    }

    private fun cryptSector(
        xtsKey: ByteArray,
        unitIndex: Long,
        buffer: ByteArray,
        unitOffset: Int,
        unitLength: Int,
        encrypt: Boolean,
    ) {
        val k1 = xtsKey.copyOfRange(0, 32)
        val k2 = xtsKey.copyOfRange(32, 64)
        val cipher1 = AES(k1)
        val cipher2 = AES(k2)
        val t = ByteArray(AES_BLOCK)
        writeUnitIndex(t, unitIndex)
        cipher2.encryptBlock(t, 0)

        val fullBlocks = unitLength / AES_BLOCK
        val tail = unitLength % AES_BLOCK
        val blocksInMain = if (tail == 0) fullBlocks else fullBlocks - 1

        for (j in 0 until blocksInMain) {
            val off = unitOffset + j * AES_BLOCK
            cryptOneBlock(cipher1, t, buffer, off, encrypt)
            multiplyTweakByAlpha(t)
        }

        if (tail > 0) {
            if (encrypt) {
                encryptCiphertextStealing(cipher1, t, buffer, unitOffset, fullBlocks, tail)
            } else {
                decryptCiphertextStealing(cipher1, t, buffer, unitOffset, fullBlocks, tail)
            }
        }
    }

    private fun cryptOneBlock(
        cipher1: AES,
        tweak: ByteArray,
        buffer: ByteArray,
        blockOffset: Int,
        encrypt: Boolean,
    ) {
        xorInPlace(buffer, blockOffset, tweak, AES_BLOCK)
        if (encrypt) {
            cipher1.encryptBlock(buffer, blockOffset)
        } else {
            cipher1.decryptBlock(buffer, blockOffset)
        }
        xorInPlace(buffer, blockOffset, tweak, AES_BLOCK)
    }

    private fun encryptCiphertextStealing(
        cipher1: AES,
        tweak: ByteArray,
        buffer: ByteArray,
        unitOffset: Int,
        fullBlocks: Int,
        tail: Int,
    ) {
        val lastFullOff = unitOffset + (fullBlocks - 1) * AES_BLOCK
        val tailOff = unitOffset + fullBlocks * AES_BLOCK
        val block = buffer.copyOfRange(lastFullOff, lastFullOff + AES_BLOCK)
        xorInPlace(block, 0, tweak, AES_BLOCK)
        cipher1.encryptBlock(block, 0)
        xorInPlace(block, 0, tweak, AES_BLOCK)

        val cc = ByteArray(AES_BLOCK)
        block.copyInto(cc, tail, tail, AES_BLOCK)
        buffer.copyInto(cc, 0, tailOff, tailOff + tail)

        val tAdvanced = tweak.copyOf()
        multiplyTweakByAlpha(tAdvanced)
        xorInPlace(cc, 0, tAdvanced, AES_BLOCK)
        cipher1.encryptBlock(cc, 0)
        xorInPlace(cc, 0, tAdvanced, AES_BLOCK)

        cc.copyInto(buffer, lastFullOff, 0, AES_BLOCK)
        block.copyInto(buffer, tailOff, 0, tail)
    }

    private fun decryptCiphertextStealing(
        cipher1: AES,
        tweak: ByteArray,
        buffer: ByteArray,
        unitOffset: Int,
        fullBlocks: Int,
        tail: Int,
    ) {
        val lastFullOff = unitOffset + (fullBlocks - 1) * AES_BLOCK
        val tailOff = unitOffset + fullBlocks * AES_BLOCK

        val tAdvanced = tweak.copyOf()
        multiplyTweakByAlpha(tAdvanced)

        val block = buffer.copyOfRange(lastFullOff, lastFullOff + AES_BLOCK)
        xorInPlace(block, 0, tAdvanced, AES_BLOCK)
        cipher1.decryptBlock(block, 0)
        xorInPlace(block, 0, tAdvanced, AES_BLOCK)

        val cc = ByteArray(AES_BLOCK)
        buffer.copyInto(cc, 0, tailOff, tailOff + tail)
        block.copyInto(cc, tail, tail, AES_BLOCK)

        xorInPlace(cc, 0, tweak, AES_BLOCK)
        cipher1.decryptBlock(cc, 0)
        xorInPlace(cc, 0, tweak, AES_BLOCK)

        cc.copyInto(buffer, lastFullOff, 0, AES_BLOCK)
        block.copyInto(buffer, tailOff, 0, tail)
    }

    private fun writeUnitIndex(block: ByteArray, unitIndex: Long) {
        block.fill(0)
        var value = unitIndex
        for (i in 0 until 8) {
            block[i] = (value and 0xFF).toByte()
            value = value ushr 8
        }
    }

    private fun xorInPlace(target: ByteArray, targetOffset: Int, mask: ByteArray, length: Int) {
        for (i in 0 until length) {
            target[targetOffset + i] = (target[targetOffset + i].toInt() xor mask[i].toInt()).toByte()
        }
    }

    /** GF(2^128) multiply by α (little-endian 128-bit tweak). */
    private fun multiplyTweakByAlpha(tweak: ByteArray) {
        var carry = 0
        for (i in tweak.indices) {
            val b = tweak[i].toInt() and 0xFF
            val newCarry = b shr 7
            tweak[i] = ((b shl 1) or carry).toByte()
            carry = newCarry
        }
        if (carry != 0) {
            tweak[0] = (tweak[0].toInt() xor 0x87).toByte()
        }
    }
}

internal fun incognitoCryptoSelfTest(key: ByteArray): Boolean = IncognitoXtsCrypto.selfTest(key)
