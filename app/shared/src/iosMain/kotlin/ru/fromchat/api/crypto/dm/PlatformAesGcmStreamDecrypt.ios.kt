package ru.fromchat.api.crypto.dm

import ru.fromchat.utils.files.PlatformFileSystem
import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.buffered
import platform.posix.fclose
import platform.posix.fflush
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fwrite

private const val AES_KEY_SIZE = 32
private const val GCM_IV_SIZE = 12
private const val GCM_TAG_SIZE = 16
private const val COPY_BUFFER_BYTES = 256 * 1024

@OptIn(ExperimentalForeignApi::class, DelicateCryptographyApi::class)
internal actual suspend fun platformAesGcmStreamDecryptMekFile(
    iv: ByteArray,
    encryptedPath: String,
    key: ByteArray,
    outputPath: String,
): Long {
    require(key.size == AES_KEY_SIZE) { "MEK must be 32 bytes" }
    require(iv.size == GCM_IV_SIZE) { "IV must be 12 bytes" }
    val encryptedSize = PlatformFileSystem.fileSize(encryptedPath)
    require(encryptedSize >= GCM_TAG_SIZE) { "Ciphertext too short" }

    val parent = outputPath.substringBeforeLast('/', missingDelimiterValue = "")
    if (parent.isNotEmpty()) {
        PlatformFileSystem.ensureDirectory(parent)
    }
    if (PlatformFileSystem.exists(outputPath)) {
        PlatformFileSystem.delete(outputPath)
    }

    val aesKey = CryptographyProvider.Default
        .get(AES.GCM)
        .keyDecoder()
        .decodeFromByteArray(AES.Key.Format.RAW, key)
    val cipher = aesKey.cipher(tagSize = 128.bits)

    var plaintextBytes = 0L
    PosixEncryptedFileRawSource(encryptedPath).buffered().use { encryptedSource ->
        PlatformFileRawSink(outputPath).buffered().use { plainSink ->
            cipher.decryptingSinkWithIv(iv, plainSink).buffered().use { decryptSink ->
                pumpRawSourceToSink(encryptedSource, decryptSink)
            }
        }
    }

    plaintextBytes = PlatformFileSystem.fileSize(outputPath)
    require(plaintextBytes > 0L) { "Decrypted file is empty" }
    return plaintextBytes
}

private fun pumpRawSourceToSink(source: RawSource, sink: RawSink) {
    val chunk = Buffer()
    while (true) {
        val read = source.readAtMostTo(chunk, COPY_BUFFER_BYTES.toLong())
        if (read < 0L) break
        if (read == 0L) continue
        sink.write(chunk, read)
    }
    sink.flush()
}

@OptIn(ExperimentalForeignApi::class)
private class PosixEncryptedFileRawSource(
    path: String,
) : RawSource {
    private val file = fopen(path, "rb") ?: error("Failed to open encrypted file")

    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        if (byteCount <= 0L) return 0L
        val toRead = byteCount.coerceAtMost(COPY_BUFFER_BYTES.toLong()).toInt()
        val array = ByteArray(toRead)
        val read = array.usePinned { pinned ->
            fread(pinned.addressOf(0), 1.convert(), toRead.convert(), file).toInt()
        }
        if (read <= 0) return -1L
        sink.write(array, 0, read)
        return read.toLong()
    }

    override fun close() {
        fclose(file)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class PlatformFileRawSink(
    path: String,
) : RawSink {
    private val file = fopen(path, "wb") ?: error("Failed to open output file: $path")

    override fun write(source: Buffer, byteCount: Long) {
        if (byteCount <= 0L) return
        var remaining = byteCount
        while (remaining > 0L) {
            val toRead = minOf(remaining, COPY_BUFFER_BYTES.toLong()).toInt()
            val array = ByteArray(toRead)
            val read = source.readAtMostTo(array, startIndex = 0, endIndex = toRead)
            if (read <= 0) break
            array.usePinned { pinned ->
                val written = fwrite(
                    pinned.addressOf(0),
                    1.convert(),
                    read.convert(),
                    file,
                ).toInt()
                if (written != read) {
                    error("Short write ($written of $read bytes)")
                }
            }
            remaining -= read
        }
    }

    override fun flush() {
        fflush(file)
    }

    override fun close() {
        fflush(file)
        fclose(file)
    }
}
