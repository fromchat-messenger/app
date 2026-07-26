package ru.fromchat.api.crypto.dm

import com.pr0gramm3r101.utils.files.PlatformFileSystem
import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.buffered

private const val AES_KEY_SIZE = 32
private const val GCM_IV_SIZE = 12
private const val GCM_TAG_SIZE = 16
private const val COPY_BUFFER_BYTES = 256 * 1024

@OptIn(DelicateCryptographyApi::class)
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

    FileEncryptedRawSource(encryptedPath).buffered().use { encryptedSource ->
        FileRawSink(outputPath).buffered().use { plainSink ->
            cipher.decryptingSinkWithIv(iv, plainSink).buffered().use { decryptSink ->
                pumpRawSourceToSink(encryptedSource, decryptSink)
            }
        }
    }

    val plaintextBytes = PlatformFileSystem.fileSize(outputPath)
    require(plaintextBytes > 0L) { "Decrypted file is empty" }
    return plaintextBytes
}

private suspend fun pumpRawSourceToSink(source: RawSource, sink: RawSink) {
    val chunk = Buffer()
    while (true) {
        val read = source.readAtMostTo(chunk, COPY_BUFFER_BYTES.toLong())
        if (read < 0L) break
        if (read == 0L) continue
        sink.write(chunk, read)
    }
    sink.flush()
}

private class FileEncryptedRawSource(
    path: String,
) : RawSource {
    private val input = FileInputStream(File(path))

    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        if (byteCount <= 0L) return 0L
        val toRead = byteCount.coerceAtMost(COPY_BUFFER_BYTES.toLong()).toInt()
        val array = ByteArray(toRead)
        val read = input.read(array)
        if (read <= 0) return -1L
        sink.write(array, 0, read)
        return read.toLong()
    }

    override fun close() {
        input.close()
    }
}

private class FileRawSink(
    path: String,
) : RawSink {
    private val output = FileOutputStream(File(path))

    override fun write(source: Buffer, byteCount: Long) {
        if (byteCount <= 0L) return
        var remaining = byteCount
        while (remaining > 0L) {
            val toRead = minOf(remaining, COPY_BUFFER_BYTES.toLong()).toInt()
            val array = ByteArray(toRead)
            val read = source.readAtMostTo(array, startIndex = 0, endIndex = toRead)
            if (read <= 0) break
            output.write(array, 0, read)
            remaining -= read
        }
    }

    override fun flush() {
        output.flush()
    }

    override fun close() {
        output.flush()
        output.close()
    }
}
