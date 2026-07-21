package ru.fromchat.api.crypto.dm

import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

private const val AES_KEY_SIZE = 32
private const val GCM_IV_SIZE = 12
private const val GCM_TAG_SIZE = 16
private const val FILE_DECRYPT_BUFFER_BYTES = 256 * 1024

/**
 * Bouncy Castle AES-GCM streaming decrypt — matches server hazmat [encrypt_message_to_file]
 * (ciphertext || tag). JCA [Cipher] buffers the full ciphertext in GCM decrypt mode and OOMs
 * on large files; BC [GCMBlockCipher.processBytes] does not.
 */
internal actual suspend fun platformAesGcmStreamDecryptMekFile(
    iv: ByteArray,
    encryptedPath: String,
    key: ByteArray,
    outputPath: String,
): Long {
    require(key.size == AES_KEY_SIZE) { "MEK must be 32 bytes" }
    require(iv.size == GCM_IV_SIZE) { "IV must be 12 bytes" }

    val inputFile = File(encryptedPath)
    val outputFile = File(outputPath)
    outputFile.parentFile?.mkdirs()

    val encryptedSize = inputFile.length()
    require(encryptedSize >= GCM_TAG_SIZE) { "Ciphertext too short" }

    if (outputFile.exists()) {
        outputFile.delete()
    }

    val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
    cipher.init(false, AEADParameters(KeyParameter(key), 128, iv))

    val inBuf = ByteArray(FILE_DECRYPT_BUFFER_BYTES)
    val outBuf = ByteArray(FILE_DECRYPT_BUFFER_BYTES)
    var plaintextBytes = 0L

    BufferedInputStream(FileInputStream(inputFile)).use { input ->
        BufferedOutputStream(FileOutputStream(outputFile)).use { output ->
            while (true) {
                val read = input.read(inBuf)
                if (read <= 0) break
                val outLen = cipher.processBytes(inBuf, 0, read, outBuf, 0)
                if (outLen > 0) {
                    output.write(outBuf, 0, outLen)
                    plaintextBytes += outLen
                }
            }
            val finalLen = cipher.doFinal(outBuf, 0)
            if (finalLen > 0) {
                output.write(outBuf, 0, finalLen)
                plaintextBytes += finalLen
            }
        }
    }

    require(plaintextBytes > 0L) { "Decrypted file is empty" }
    return plaintextBytes
}
