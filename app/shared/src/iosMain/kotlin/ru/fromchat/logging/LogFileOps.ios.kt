package ru.fromchat.logging

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy
import platform.zlib.uLongVar
import platform.zlib.uncompress

@OptIn(ExperimentalForeignApi::class)
internal actual object LogFileOps {
    actual fun readText(path: String): String {
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return ""
        val raw = NSData.dataWithContentsOfFile(path) ?: return ""
        return raw.toByteArray().decodeToString()
    }

    actual fun readBytes(path: String): ByteArray {
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return ByteArray(0)
        val raw = NSData.dataWithContentsOfFile(path) ?: return ByteArray(0)
        return raw.toByteArray()
    }

    actual suspend fun gzipFile(sourcePath: String, destinationPath: String) {
        withContext(Dispatchers.Default) {
            if (!NSFileManager.defaultManager.fileExistsAtPath(sourcePath)) return@withContext
            val raw = NSData.dataWithContentsOfFile(sourcePath) ?: return@withContext
            val gzipped = gzipCompress(raw.toByteArray())
            val parent = destinationPath.substringBeforeLast('/', missingDelimiterValue = destinationPath)
            NSFileManager.defaultManager.createDirectoryAtPath(parent, true, null, null)
            writeBytes(destinationPath, gzipped)
            NSFileManager.defaultManager.removeItemAtPath(sourcePath, null)
        }
    }

    actual suspend fun readGzipText(path: String, onProgress: (Float) -> Unit): String =
        withContext(Dispatchers.Default) {
            gunzipToByteArray(path, onProgress).decodeToString()
        }

    actual suspend fun gunzipToFile(
        sourcePath: String,
        destinationPath: String,
        onProgress: (Float) -> Unit,
    ) {
        withContext(Dispatchers.Default) {
            val bytes = gunzipToByteArray(sourcePath, onProgress)
            val parent = destinationPath.substringBeforeLast('/', missingDelimiterValue = destinationPath)
            NSFileManager.defaultManager.createDirectoryAtPath(parent, true, null, null)
            writeBytes(destinationPath, bytes)
        }
    }

    actual suspend fun zipFiles(
        entries: List<Pair<String, String>>,
        destinationPath: String,
    ) {
        withContext(Dispatchers.Default) {
            if (entries.isEmpty()) return@withContext
            val zipEntries = entries.mapNotNull { (entryName, sourcePath) ->
                if (!NSFileManager.defaultManager.fileExistsAtPath(sourcePath)) return@mapNotNull null
                ZipFileEntry(entryName, readBytes(sourcePath))
            }
            val bytes = buildStoreZipArchive(zipEntries)
            val parent = destinationPath.substringBeforeLast('/', missingDelimiterValue = destinationPath)
            NSFileManager.defaultManager.createDirectoryAtPath(parent, true, null, null)
            writeBytes(destinationPath, bytes)
        }
    }

    private fun writeBytes(path: String, bytes: ByteArray) {
        bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
                ?.writeToFile(path, true)
        }
    }

    private fun gunzipToByteArray(path: String, onProgress: (Float) -> Unit): ByteArray {
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) {
            onProgress(1f)
            return ByteArray(0)
        }
        val raw = NSData.dataWithContentsOfFile(path) ?: run {
            onProgress(1f)
            return ByteArray(0)
        }
        val compressed = raw.toByteArray()
        if (compressed.size < 18) {
            onProgress(1f)
            return ByteArray(0)
        }
        val deflated = compressed.copyOfRange(10, compressed.size - 8)
        val inflated = inflateGzipPayload(deflated)
        onProgress(1f)
        return inflated
    }

    private fun inflateGzipPayload(deflated: ByteArray): ByteArray = memScoped {
        if (deflated.isEmpty()) return@memScoped ByteArray(0)

        var capacity = (deflated.size * 4).coerceAtLeast(256)
        while (capacity <= deflated.size * 32) {
            val output = allocArray<UByteVar>(capacity)
            val destLength = alloc<uLongVar>()
            destLength.value = capacity.convert()
            val source = deflated.toUByteArray().toCValues()
            val status = uncompress(
                output,
                destLength.ptr,
                source.ptr.reinterpret(),
                deflated.size.convert(),
            )
            if (status == 0) {
                val size = destLength.value.toInt()
                val result = ByteArray(size)
                result.usePinned { pinned ->
                    memcpy(pinned.addressOf(0), output, size.convert())
                }
                return@memScoped result
            }
            capacity *= 2
        }
        ByteArray(0)
    }

    private fun NSData.toByteArray(): ByteArray {
        val length = this.length.toInt()
        if (length == 0) return ByteArray(0)
        val bytes = ByteArray(length)
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, this.length)
        }
        return bytes
    }

    private fun ByteArray.toUByteArray(): UByteArray = UByteArray(size) { this[it].toUByte() }
}
