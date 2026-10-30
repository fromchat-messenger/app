package ru.fromchat.api.crypto.transport

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import ru.fromchat.utils.iosAppendFile
import ru.fromchat.utils.iosFileSize

@OptIn(ExperimentalForeignApi::class)
actual object TransportFileEncryptor {
    actual suspend fun encryptPlaintextFileToTransportBlob(
        sourceUri: String,
        destinationPath: String,
        transportPublicKeyB64: String,
        ephemeralSecretKey: ByteArray,
        plaintextSizeBytes: Long,
        onPlaintextProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)?,
    ): Long = withContext(Dispatchers.Default) {
        NSFileManager.defaultManager.removeItemAtPath(destinationPath, null)
        val parent = destinationPath.substringBeforeLast('/', missingDelimiterValue = destinationPath)
        if (parent.isNotEmpty()) {
            NSFileManager.defaultManager.createDirectoryAtPath(parent, true, null, null)
        }
        NSFileManager.defaultManager.createFileAtPath(destinationPath, null, null)
        encryptPlaintextFileToFcaeBlob(
            sourceUri = sourceUri,
            writeBytes = { bytes -> iosAppendFile(destinationPath, bytes) },
            finish = { iosFileSize(destinationPath) },
            transportPublicKeyB64 = transportPublicKeyB64,
            ephemeralSecretKey = ephemeralSecretKey,
            plaintextSizeBytes = plaintextSizeBytes,
            onPlaintextProgress = onPlaintextProgress,
        )
    }
}
