package ru.fromchat.api.local.cache

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun cacheRoot(): File {
    val home = System.getProperty("user.home")
    return if (!home.isNullOrBlank()) {
        File(home, ".fromchat/cache")
    } else {
        File(System.getProperty("java.io.tmpdir"), "fromchat")
    }
}

private fun uploadDir(instanceId: String): File {
    val safe = instanceId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    return File(cacheRoot(), "fromchat/instances/$safe/uploads").apply { mkdirs() }
}

private fun safeId(clientMessageId: String): String =
    clientMessageId.replace(Regex("[^a-zA-Z0-9._-]"), "_")

private fun sourceFile(instanceId: String, clientMessageId: String): File =
    File(uploadDir(instanceId), "${safeId(clientMessageId)}.source")

private fun sourcePartFile(instanceId: String, clientMessageId: String): File =
    File(uploadDir(instanceId), "${safeId(clientMessageId)}.source.part")

private fun sourceOkFile(instanceId: String, clientMessageId: String): File =
    File(uploadDir(instanceId), "${safeId(clientMessageId)}.source.ok")

private fun blobFile(instanceId: String, clientMessageId: String): File =
    File(uploadDir(instanceId), "${safeId(clientMessageId)}.enc")

private fun blobPartFile(instanceId: String, clientMessageId: String): File =
    File(uploadDir(instanceId), "${safeId(clientMessageId)}.enc.part")

private fun blobOkFile(instanceId: String, clientMessageId: String): File =
    File(uploadDir(instanceId), "${safeId(clientMessageId)}.enc.ok")

private fun cipherFile(instanceId: String, clientMessageId: String): File =
    File(uploadDir(instanceId), "${safeId(clientMessageId)}.cipher.json")

private fun cipherPartFile(instanceId: String, clientMessageId: String): File =
    File(uploadDir(instanceId), "${safeId(clientMessageId)}.cipher.json.part")

actual fun encryptedUploadBlobPath(instanceId: String, clientMessageId: String): String =
    blobFile(instanceId, clientMessageId).absolutePath

actual fun encryptedUploadBlobPartPath(instanceId: String, clientMessageId: String): String =
    blobPartFile(instanceId, clientMessageId).absolutePath

private fun readOkMarker(okFile: File, diskFile: File, expectedBytes: Long): Boolean {
    if (!okFile.isFile || !diskFile.isFile) return false
    val marker = decodeUploadArtifactOkMarker(okFile.readText()) ?: return false
    return marker.isValidOnDisk(diskFile.length(), expectedBytes)
}

private fun writeOkMarker(okFile: File, actualBytes: Long, expectedBytes: Long) {
    okFile.writeText(encodeUploadArtifactOkMarker(actualBytes, expectedBytes))
}

private fun File.syncOutput() {
    FileOutputStream(this, true).use { it.fd.sync() }
}

private fun atomicReplace(part: File, final: File) {
    if (!part.isFile) error("Partial upload file missing")
    final.delete()
    if (!part.renameTo(final)) {
        part.copyTo(final, overwrite = true)
        part.delete()
    }
    final.syncOutput()
}

private fun resolveLocalPath(fileUri: String): String? =
    when {
        fileUri.startsWith("file:") -> runCatching { URI(fileUri).path }.getOrNull()
        else -> fileUri
    }

actual suspend fun queryOutboundUriSizeBytes(fileUri: String): Long? = withContext(Dispatchers.IO) {
    val path = resolveLocalPath(fileUri) ?: return@withContext null
    val file = File(path)
    if (!file.isFile) null else file.length()
}

actual suspend fun stageOutboundFileForUpload(
    instanceId: String,
    clientMessageId: String,
    sourceUri: String,
    expectedSizeBytes: Long,
): StagedOutboundFile = withContext(Dispatchers.IO) {
    repairInterruptedUploadArtifacts(instanceId, clientMessageId)
    val dest = sourceFile(instanceId, clientMessageId)
    val destUri = dest.absolutePath
    if (sourceUri == destUri || resolveLocalPath(sourceUri) == destUri) {
        if (!isStagedSourceReady(instanceId, clientMessageId, expectedSizeBytes)) {
            throw OutboundFileUnavailableException("Staged source file is incomplete")
        }
        return@withContext StagedOutboundFile(uri = destUri, sizeBytes = dest.length())
    }
    if (isStagedSourceReady(instanceId, clientMessageId, expectedSizeBytes)) {
        return@withContext StagedOutboundFile(uri = destUri, sizeBytes = dest.length())
    }
    val part = sourcePartFile(instanceId, clientMessageId)
    dest.delete()
    sourceOkFile(instanceId, clientMessageId).delete()
    part.delete()
    val sourcePath = resolveLocalPath(sourceUri)
        ?: throw OutboundFileUnavailableException("Invalid file URI")
    val source = File(sourcePath)
    if (!source.isFile) throw OutboundFileUnavailableException("Failed to read file from URI")
    source.inputStream().use { inputStream ->
        FileOutputStream(part).use { output ->
            inputStream.copyTo(output)
            output.flush()
            output.fd.sync()
        }
    }
    atomicReplace(part, dest)
    val stagedBytes = dest.length()
    val expected = expectedSizeBytes.takeIf { it > 0L } ?: stagedBytes
    if (expectedSizeBytes > 0L && stagedBytes != expectedSizeBytes) {
        dest.delete()
        sourceOkFile(instanceId, clientMessageId).delete()
        throw OutboundFileUnavailableException("Staged file size mismatch")
    }
    writeOkMarker(sourceOkFile(instanceId, clientMessageId), stagedBytes, expected)
    StagedOutboundFile(uri = destUri, sizeBytes = stagedBytes)
}

actual suspend fun isStagedSourceReady(
    instanceId: String,
    clientMessageId: String,
    expectedSizeBytes: Long,
): Boolean = withContext(Dispatchers.IO) {
    readOkMarker(
        sourceOkFile(instanceId, clientMessageId),
        sourceFile(instanceId, clientMessageId),
        expectedSizeBytes,
    )
}

actual suspend fun isEncryptedBlobReady(
    instanceId: String,
    clientMessageId: String,
    expectedEncryptedSizeBytes: Long?,
): Boolean = withContext(Dispatchers.IO) {
    val expected = expectedEncryptedSizeBytes?.takeIf { it > 0L } ?: 0L
    val enc = blobFile(instanceId, clientMessageId)
    val ok = blobOkFile(instanceId, clientMessageId)
    if (!readOkMarker(ok, enc, expected)) return@withContext false
    cipherFile(instanceId, clientMessageId).isFile
}

actual suspend fun commitEncryptedUploadBlob(
    instanceId: String,
    clientMessageId: String,
    encryptedSizeBytes: Long,
): Unit = withContext(Dispatchers.IO) {
    val part = blobPartFile(instanceId, clientMessageId)
    val final = blobFile(instanceId, clientMessageId)
    if (part.isFile) {
        atomicReplace(part, final)
    } else if (!final.isFile) {
        error("Encrypted upload blob missing")
    }
    if (final.length() != encryptedSizeBytes) {
        throw OutboundFileUnavailableException("Encrypted blob size mismatch after commit")
    }
    writeOkMarker(blobOkFile(instanceId, clientMessageId), encryptedSizeBytes, encryptedSizeBytes)
}

actual suspend fun repairInterruptedUploadArtifacts(
    instanceId: String,
    clientMessageId: String,
): Unit = withContext(Dispatchers.IO) {
    sourcePartFile(instanceId, clientMessageId).delete()
    blobPartFile(instanceId, clientMessageId).delete()
    cipherPartFile(instanceId, clientMessageId).delete()
    val enc = blobFile(instanceId, clientMessageId)
    val encOk = blobOkFile(instanceId, clientMessageId)
    if (!readOkMarker(encOk, enc, 0L)) {
        enc.delete()
        encOk.delete()
        cipherFile(instanceId, clientMessageId).delete()
    }
    val source = sourceFile(instanceId, clientMessageId)
    val sourceOk = sourceOkFile(instanceId, clientMessageId)
    if (source.isFile && !readOkMarker(sourceOk, source, 0L)) {
        source.delete()
        sourceOk.delete()
    }
}

private class JvmOutboundFileInputStream(
    private val input: InputStream,
) : OutboundFileInputStream {
    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        withContext(Dispatchers.IO) {
            input.read(buffer, offset, length)
        }

    override suspend fun close() {
        withContext(Dispatchers.IO) {
            input.close()
        }
    }
}

actual suspend fun openOutboundFileInputStream(fileUri: String): OutboundFileInputStream? =
    withContext(Dispatchers.IO) {
        val path = resolveLocalPath(fileUri) ?: return@withContext null
        val file = File(path)
        if (!file.isFile) return@withContext null
        JvmOutboundFileInputStream(FileInputStream(file))
    }

actual suspend fun readOutboundFileBytes(fileUri: String): ByteArray =
    withContext(Dispatchers.IO) {
        val path = resolveLocalPath(fileUri) ?: throw OutboundFileUnavailableException("Invalid file URI")
        val file = File(path)
        if (!file.isFile) throw OutboundFileUnavailableException("File no longer exists")
        file.readBytes()
    }

actual suspend fun copyOutboundFileToPath(sourceUri: String, destinationPath: String) {
    withContext(Dispatchers.IO) {
        val dest = File(destinationPath)
        dest.parentFile?.mkdirs()
        val path = resolveLocalPath(sourceUri)
            ?: throw OutboundFileUnavailableException("Invalid file URI")
        val source = File(path)
        if (!source.isFile) throw OutboundFileUnavailableException("Failed to read file from URI")
        source.inputStream().use { inputStream ->
            FileOutputStream(dest).use { output ->
                inputStream.copyTo(output)
                output.flush()
                output.fd.sync()
            }
        }
    }
}

actual suspend fun saveEncryptedUploadBlob(instanceId: String, clientMessageId: String, bytes: ByteArray) {
    withContext(Dispatchers.IO) {
        repairInterruptedUploadArtifacts(instanceId, clientMessageId)
        val part = blobPartFile(instanceId, clientMessageId)
        part.delete()
        blobOkFile(instanceId, clientMessageId).delete()
        FileOutputStream(part).use { it.write(bytes) }
        part.syncOutput()
    }
}

actual suspend fun loadEncryptedUploadBlob(instanceId: String, clientMessageId: String): ByteArray? =
    withContext(Dispatchers.IO) {
        if (!isEncryptedBlobReady(instanceId, clientMessageId, null)) return@withContext null
        val f = blobFile(instanceId, clientMessageId)
        if (!f.isFile || f.length() == 0L) null else f.readBytes()
    }

actual suspend fun encryptedUploadBlobSizeBytes(instanceId: String, clientMessageId: String): Long? =
    withContext(Dispatchers.IO) {
        if (!isEncryptedBlobReady(instanceId, clientMessageId, null)) return@withContext null
        val f = blobFile(instanceId, clientMessageId)
        if (!f.isFile || f.length() <= 0L) null else f.length()
    }

actual suspend fun readEncryptedUploadBlobRange(
    instanceId: String,
    clientMessageId: String,
    offset: Long,
    length: Int,
): ByteArray = withContext(Dispatchers.IO) {
    if (!isEncryptedBlobReady(instanceId, clientMessageId, null)) {
        throw OutboundFileUnavailableException("Encrypted upload blob not committed")
    }
    val f = blobFile(instanceId, clientMessageId)
    if (!f.isFile) throw OutboundFileUnavailableException("Encrypted upload blob missing")
    if (length <= 0) return@withContext ByteArray(0)
    f.inputStream().use { input ->
        val skipped = input.skip(offset)
        if (skipped < offset) throw OutboundFileUnavailableException("Encrypted upload blob truncated")
        val buffer = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(buffer, read, length - read)
            if (n <= 0) break
            read += n
        }
        if (read < length) {
            throw OutboundFileUnavailableException("Encrypted upload blob truncated")
        }
        buffer
    }
}

actual suspend fun saveUploadTransportCipherJson(instanceId: String, clientMessageId: String, json: String) {
    saveUploadTransportCipherJsonAtomic(instanceId, clientMessageId, json)
}

actual suspend fun saveUploadTransportCipherJsonAtomic(
    instanceId: String,
    clientMessageId: String,
    json: String,
) {
    withContext(Dispatchers.IO) {
        val part = cipherPartFile(instanceId, clientMessageId)
        val final = cipherFile(instanceId, clientMessageId)
        part.writeText(json)
        part.syncOutput()
        atomicReplace(part, final)
    }
}

actual suspend fun loadUploadTransportCipherJson(instanceId: String, clientMessageId: String): String? =
    withContext(Dispatchers.IO) {
        if (!cipherFile(instanceId, clientMessageId).isFile) return@withContext null
        cipherFile(instanceId, clientMessageId).readText().takeIf { it.isNotBlank() }
    }

actual suspend fun clearUploadArtifacts(instanceId: String, clientMessageId: String) {
    withContext(Dispatchers.IO) {
        sourceFile(instanceId, clientMessageId).delete()
        sourcePartFile(instanceId, clientMessageId).delete()
        sourceOkFile(instanceId, clientMessageId).delete()
        blobFile(instanceId, clientMessageId).delete()
        blobPartFile(instanceId, clientMessageId).delete()
        blobOkFile(instanceId, clientMessageId).delete()
        cipherFile(instanceId, clientMessageId).delete()
        cipherPartFile(instanceId, clientMessageId).delete()
    }
}

actual suspend fun clearUploadSecretsOnly(instanceId: String, clientMessageId: String) {
    withContext(Dispatchers.IO) {
        blobFile(instanceId, clientMessageId).delete()
        blobPartFile(instanceId, clientMessageId).delete()
        blobOkFile(instanceId, clientMessageId).delete()
        cipherFile(instanceId, clientMessageId).delete()
        cipherPartFile(instanceId, clientMessageId).delete()
    }
}
