package ru.fromchat.api.crypto.transport

import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual object TransportFileEncryptor {
    actual suspend fun encryptPlaintextFileToTransportBlob(
        sourceUri: String,
        destinationPath: String,
        transportPublicKeyB64: String,
        ephemeralSecretKey: ByteArray,
        plaintextSizeBytes: Long,
        onPlaintextProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)?,
    ): Long = withContext(Dispatchers.IO) {
        val dest = File(destinationPath)
        dest.parentFile?.mkdirs()
        dest.delete()
        FileOutputStream(dest).use { output ->
            encryptPlaintextFileToFcaeBlob(
                sourceUri = sourceUri,
                writeBytes = { bytes -> output.write(bytes) },
                finish = { dest.length() },
                transportPublicKeyB64 = transportPublicKeyB64,
                ephemeralSecretKey = ephemeralSecretKey,
                plaintextSizeBytes = plaintextSizeBytes,
                onPlaintextProgress = onPlaintextProgress,
            )
        }
    }
}
