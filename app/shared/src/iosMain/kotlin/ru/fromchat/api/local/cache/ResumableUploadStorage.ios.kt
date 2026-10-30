@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package ru.fromchat.api.local.cache

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLFileSizeKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToFile
import ru.fromchat.utils.IosPosixFileReader
import ru.fromchat.utils.iosCopyFile
import ru.fromchat.utils.iosFileSize
import ru.fromchat.utils.iosReadFileRange

private fun uploadDir(instanceId: String): String {
    val url = NSFileManager.defaultManager.URLForDirectory(
        directory = NSCachesDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    ) ?: return ""
    val path = url.path +
        "/fromchat/instances/${instanceId.replace(Regex("[^a-zA-Z0-9._-]"), "_")}/uploads"
    NSFileManager.defaultManager.createDirectoryAtPath(path, true, null, null)
    return path
}

private fun safeId(clientMessageId: String): String =
    clientMessageId.replace(Regex("[^a-zA-Z0-9._-]"), "_")

private fun sourcePath(instanceId: String, clientMessageId: String): String =
    "${uploadDir(instanceId)}/${safeId(clientMessageId)}.source"

private fun sourcePartPath(instanceId: String, clientMessageId: String): String =
    "${uploadDir(instanceId)}/${safeId(clientMessageId)}.source.part"

private fun sourceOkPath(instanceId: String, clientMessageId: String): String =
    "${uploadDir(instanceId)}/${safeId(clientMessageId)}.source.ok"

private fun blobPath(instanceId: String, clientMessageId: String): String =
    "${uploadDir(instanceId)}/${safeId(clientMessageId)}.enc"

private fun blobPartPath(instanceId: String, clientMessageId: String): String =
    "${uploadDir(instanceId)}/${safeId(clientMessageId)}.enc.part"

private fun blobOkPath(instanceId: String, clientMessageId: String): String =
    "${uploadDir(instanceId)}/${safeId(clientMessageId)}.enc.ok"

private fun cipherPath(instanceId: String, clientMessageId: String): String =
    "${uploadDir(instanceId)}/${safeId(clientMessageId)}.cipher.json"

private fun cipherPartPath(instanceId: String, clientMessageId: String): String =
    "${uploadDir(instanceId)}/${safeId(clientMessageId)}.cipher.json.part"

actual fun encryptedUploadBlobPath(instanceId: String, clientMessageId: String): String =
    blobPath(instanceId, clientMessageId)

actual fun encryptedUploadBlobPartPath(instanceId: String, clientMessageId: String): String =
    blobPartPath(instanceId, clientMessageId)

private fun readOkMarker(okPath: String, diskPath: String, expectedBytes: Long): Boolean {
    if (!NSFileManager.defaultManager.fileExistsAtPath(okPath)) return false
    if (!NSFileManager.defaultManager.fileExistsAtPath(diskPath)) return false
    val raw = NSData.create(contentsOfFile = okPath)?.let { data ->
        val bytesPtr = data.bytes ?: return false
        val bytePtr = bytesPtr.reinterpret<ByteVar>()
        ByteArray(data.length.toInt()) { i -> bytePtr[i] }.decodeToString()
    } ?: return false
    val marker = decodeUploadArtifactOkMarker(raw) ?: return false
    return marker.isValidOnDisk(iosFileSize(diskPath), expectedBytes)
}

private fun writeOkMarker(okPath: String, actualBytes: Long, expectedBytes: Long) {
    val bytes = encodeUploadArtifactOkMarker(actualBytes, expectedBytes).encodeToByteArray()
    bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong()).writeToFile(okPath, true)
    }
}

private fun atomicReplace(partPath: String, finalPath: String) {
    if (!NSFileManager.defaultManager.fileExistsAtPath(partPath)) {
        error("Partial upload file missing")
    }
    NSFileManager.defaultManager.removeItemAtPath(finalPath, null)
    if (!NSFileManager.defaultManager.moveItemAtPath(partPath, toPath = finalPath, error = null)) {
        iosCopyFile(partPath, finalPath)
        NSFileManager.defaultManager.removeItemAtPath(partPath, null)
    }
}

actual suspend fun queryOutboundUriSizeBytes(fileUri: String): Long? = withContext(Dispatchers.Default) {
    ((NSURL.URLWithString(fileUri) ?: return@withContext null)
        .resourceValuesForKeys(listOf(NSURLFileSizeKey), null)
        ?.get(NSURLFileSizeKey) as? NSNumber)?.longValue
}

actual suspend fun stageOutboundFileForUpload(
    instanceId: String,
    clientMessageId: String,
    sourceUri: String,
    expectedSizeBytes: Long,
): StagedOutboundFile = withContext(Dispatchers.Default) {
    repairInterruptedUploadArtifacts(instanceId, clientMessageId)
    val dest = sourcePath(instanceId, clientMessageId)
    if (sourceUri == dest) {
        if (!isStagedSourceReady(instanceId, clientMessageId, expectedSizeBytes)) {
            throw OutboundFileUnavailableException("Staged source file is incomplete")
        }
        return@withContext StagedOutboundFile(uri = dest, sizeBytes = iosFileSize(dest))
    }
    if (isStagedSourceReady(instanceId, clientMessageId, expectedSizeBytes)) {
        return@withContext StagedOutboundFile(uri = dest, sizeBytes = iosFileSize(dest))
    }
    val part = sourcePartPath(instanceId, clientMessageId)
    val sourcePathOnDisk = when {
        sourceUri.startsWith("file://") -> NSURL.URLWithString(sourceUri)?.path
        else -> sourceUri
    } ?: throw OutboundFileUnavailableException("Invalid file URI")
    NSFileManager.defaultManager.removeItemAtPath(dest, null)
    NSFileManager.defaultManager.removeItemAtPath(sourceOkPath(instanceId, clientMessageId), null)
    NSFileManager.defaultManager.removeItemAtPath(part, null)
    iosCopyFile(sourcePathOnDisk, part)
    atomicReplace(part, dest)
    val stagedBytes = iosFileSize(dest)
    val expected = expectedSizeBytes.takeIf { it > 0L } ?: stagedBytes
    if (expectedSizeBytes > 0L && stagedBytes != expectedSizeBytes) {
        NSFileManager.defaultManager.removeItemAtPath(dest, null)
        NSFileManager.defaultManager.removeItemAtPath(sourceOkPath(instanceId, clientMessageId), null)
        throw OutboundFileUnavailableException("Staged file size mismatch")
    }
    writeOkMarker(sourceOkPath(instanceId, clientMessageId), stagedBytes, expected)
    StagedOutboundFile(uri = dest, sizeBytes = stagedBytes)
}

actual suspend fun isStagedSourceReady(
    instanceId: String,
    clientMessageId: String,
    expectedSizeBytes: Long,
): Boolean = withContext(Dispatchers.Default) {
    readOkMarker(
        sourceOkPath(instanceId, clientMessageId),
        sourcePath(instanceId, clientMessageId),
        expectedSizeBytes,
    )
}

actual suspend fun isEncryptedBlobReady(
    instanceId: String,
    clientMessageId: String,
    expectedEncryptedSizeBytes: Long?,
): Boolean = withContext(Dispatchers.Default) {
    if (!readOkMarker(
            blobOkPath(instanceId, clientMessageId),
            blobPath(instanceId, clientMessageId),
            expectedEncryptedSizeBytes?.takeIf { it > 0L } ?: 0L,
        )
    ) {
        return@withContext false
    }
    NSFileManager.defaultManager.fileExistsAtPath(cipherPath(instanceId, clientMessageId))
}

actual suspend fun commitEncryptedUploadBlob(
    instanceId: String,
    clientMessageId: String,
    encryptedSizeBytes: Long,
) {
    withContext(Dispatchers.Default) {
        val part = blobPartPath(instanceId, clientMessageId)
        val final = blobPath(instanceId, clientMessageId)
        if (NSFileManager.defaultManager.fileExistsAtPath(part)) {
            atomicReplace(part, final)
        } else if (!NSFileManager.defaultManager.fileExistsAtPath(final)) {
            error("Encrypted upload blob missing")
        }
        if (iosFileSize(final) != encryptedSizeBytes) {
            throw OutboundFileUnavailableException("Encrypted blob size mismatch after commit")
        }
        writeOkMarker(blobOkPath(instanceId, clientMessageId), encryptedSizeBytes, encryptedSizeBytes)
    }
}

actual suspend fun repairInterruptedUploadArtifacts(instanceId: String, clientMessageId: String) {
    withContext(Dispatchers.Default) {
        NSFileManager.defaultManager.removeItemAtPath(sourcePartPath(instanceId, clientMessageId), null)
        NSFileManager.defaultManager.removeItemAtPath(blobPartPath(instanceId, clientMessageId), null)
        NSFileManager.defaultManager.removeItemAtPath(cipherPartPath(instanceId, clientMessageId), null)
        val enc = blobPath(instanceId, clientMessageId)
        val encOk = blobOkPath(instanceId, clientMessageId)
        if (!readOkMarker(encOk, enc, 0L)) {
            NSFileManager.defaultManager.removeItemAtPath(enc, null)
            NSFileManager.defaultManager.removeItemAtPath(encOk, null)
            NSFileManager.defaultManager.removeItemAtPath(cipherPath(instanceId, clientMessageId), null)
        }
        val source = sourcePath(instanceId, clientMessageId)
        val sourceOk = sourceOkPath(instanceId, clientMessageId)
        if (NSFileManager.defaultManager.fileExistsAtPath(source) &&
            !readOkMarker(sourceOk, source, 0L)
        ) {
            NSFileManager.defaultManager.removeItemAtPath(source, null)
            NSFileManager.defaultManager.removeItemAtPath(sourceOk, null)
        }
    }
}

private class IosOutboundFileInputStream(
    private val reader: IosPosixFileReader,
) : OutboundFileInputStream {
    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        withContext(Dispatchers.Default) {
            reader.read(buffer, offset, length)
        }

    override suspend fun close() {
        withContext(Dispatchers.Default) {
            reader.close()
        }
    }
}

actual suspend fun openOutboundFileInputStream(fileUri: String): OutboundFileInputStream? =
    withContext(Dispatchers.Default) {
        val path = when {
            fileUri.startsWith("file://") -> NSURL.URLWithString(fileUri)?.path
            else -> fileUri
        } ?: return@withContext null
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return@withContext null
        runCatching { IosOutboundFileInputStream(IosPosixFileReader(path)) }.getOrNull()
    }

private fun readBytesAtPath(path: String): ByteArray? {
    if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return null
    val data = NSData.create(contentsOfFile = path) ?: return null
    val length = data.length.toInt()
    if (length == 0) return null
    val bytesPtr = data.bytes ?: return null
    val bytePtr = bytesPtr.reinterpret<ByteVar>()
    return ByteArray(length) { i -> bytePtr[i] }
}

actual suspend fun readOutboundFileBytes(fileUri: String): ByteArray =
    withContext(Dispatchers.Default) {
        val url = NSURL.URLWithString(fileUri) ?: throw OutboundFileUnavailableException("Invalid file URI")
        val data = NSData.dataWithContentsOfURL(url)
            ?: throw OutboundFileUnavailableException("Failed to read file from URI")
        val length = data.length.toInt()
        val bytesPtr = data.bytes ?: error("Empty file")
        val bytePtr = bytesPtr.reinterpret<ByteVar>()
        ByteArray(length) { i -> bytePtr[i] }
    }

actual suspend fun copyOutboundFileToPath(sourceUri: String, destinationPath: String) {
    withContext(Dispatchers.Default) {
        val sourcePath = when {
            sourceUri.startsWith("file://") -> NSURL.URLWithString(sourceUri)?.path
            else -> sourceUri
        } ?: throw OutboundFileUnavailableException("Invalid file URI")
        val parent = destinationPath.substringBeforeLast('/', missingDelimiterValue = "")
        if (parent.isNotEmpty()) {
            NSFileManager.defaultManager.createDirectoryAtPath(parent, true, null, null)
        }
        NSFileManager.defaultManager.removeItemAtPath(destinationPath, null)
        iosCopyFile(sourcePath, destinationPath)
    }
}

actual suspend fun saveEncryptedUploadBlob(instanceId: String, clientMessageId: String, bytes: ByteArray) {
    withContext(Dispatchers.Default) {
        repairInterruptedUploadArtifacts(instanceId, clientMessageId)
        val part = blobPartPath(instanceId, clientMessageId)
        NSFileManager.defaultManager.removeItemAtPath(part, null)
        NSFileManager.defaultManager.removeItemAtPath(blobOkPath(instanceId, clientMessageId), null)
        bytes.usePinned { pinned ->
            val data = NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
            data.writeToFile(part, true)
        }
    }
}

actual suspend fun loadEncryptedUploadBlob(instanceId: String, clientMessageId: String): ByteArray? =
    withContext(Dispatchers.Default) {
        if (!isEncryptedBlobReady(instanceId, clientMessageId, null)) return@withContext null
        readBytesAtPath(blobPath(instanceId, clientMessageId))
    }

actual suspend fun encryptedUploadBlobSizeBytes(instanceId: String, clientMessageId: String): Long? =
    withContext(Dispatchers.Default) {
        if (!isEncryptedBlobReady(instanceId, clientMessageId, null)) return@withContext null
        val size = iosFileSize(blobPath(instanceId, clientMessageId))
        if (size <= 0L) null else size
    }

actual suspend fun readEncryptedUploadBlobRange(
    instanceId: String,
    clientMessageId: String,
    offset: Long,
    length: Int,
): ByteArray = withContext(Dispatchers.Default) {
    if (!isEncryptedBlobReady(instanceId, clientMessageId, null)) {
        throw OutboundFileUnavailableException("Encrypted upload blob not committed")
    }
    val path = blobPath(instanceId, clientMessageId)
    if (!NSFileManager.defaultManager.fileExistsAtPath(path)) {
        throw OutboundFileUnavailableException("Encrypted upload blob missing")
    }
    if (length <= 0) return@withContext ByteArray(0)
    iosReadFileRange(path, offset, length)
}

actual suspend fun saveUploadTransportCipherJson(
    instanceId: String,
    clientMessageId: String,
    json: String,
) = saveUploadTransportCipherJsonAtomic(instanceId, clientMessageId, json)

actual suspend fun saveUploadTransportCipherJsonAtomic(
    instanceId: String,
    clientMessageId: String,
    json: String,
) {
    withContext(Dispatchers.Default) {
        val part = cipherPartPath(instanceId, clientMessageId)
        val final = cipherPath(instanceId, clientMessageId)
        json.encodeToByteArray().usePinned { pinned ->
            val data = NSData.create(bytes = pinned.addressOf(0), length = json.length.toULong())
            data.writeToFile(part, true)
        }
        atomicReplace(part, final)
    }
}

actual suspend fun loadUploadTransportCipherJson(instanceId: String, clientMessageId: String): String? =
    withContext(Dispatchers.Default) {
        readBytesAtPath(cipherPath(instanceId, clientMessageId))?.decodeToString()?.takeIf { it.isNotBlank() }
    }

actual suspend fun clearUploadArtifacts(instanceId: String, clientMessageId: String) {
    withContext(Dispatchers.Default) {
        NSFileManager.defaultManager.removeItemAtPath(sourcePath(instanceId, clientMessageId), null)
        NSFileManager.defaultManager.removeItemAtPath(sourcePartPath(instanceId, clientMessageId), null)
        NSFileManager.defaultManager.removeItemAtPath(sourceOkPath(instanceId, clientMessageId), null)
        NSFileManager.defaultManager.removeItemAtPath(blobPath(instanceId, clientMessageId), null)
        NSFileManager.defaultManager.removeItemAtPath(blobPartPath(instanceId, clientMessageId), null)
        NSFileManager.defaultManager.removeItemAtPath(blobOkPath(instanceId, clientMessageId), null)
        NSFileManager.defaultManager.removeItemAtPath(cipherPath(instanceId, clientMessageId), null)
        NSFileManager.defaultManager.removeItemAtPath(cipherPartPath(instanceId, clientMessageId), null)
    }
}

actual suspend fun clearUploadSecretsOnly(instanceId: String, clientMessageId: String) {
    withContext(Dispatchers.Default) {
        NSFileManager.defaultManager.removeItemAtPath(blobPath(instanceId, clientMessageId), null)
        NSFileManager.defaultManager.removeItemAtPath(blobPartPath(instanceId, clientMessageId), null)
        NSFileManager.defaultManager.removeItemAtPath(blobOkPath(instanceId, clientMessageId), null)
        NSFileManager.defaultManager.removeItemAtPath(cipherPath(instanceId, clientMessageId), null)
        NSFileManager.defaultManager.removeItemAtPath(cipherPartPath(instanceId, clientMessageId), null)
    }
}
