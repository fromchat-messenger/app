package ru.fromchat.api.local.cache

import java.io.File
import java.io.FileOutputStream
import ru.fromchat.incognito.IncognitoMode
import ru.fromchat.incognito.IncognitoVolumeFileSystem
import ru.fromchat.utils.files.PlatformFileSystem

internal actual class FileWriteSink actual constructor(
    path: String,
    append: Boolean,
) : AutoCloseable {
    private val filePath = path
    private val volumeBacked =
        IncognitoMode.active && IncognitoVolumeFileSystem.relativeKeyForAppPath(filePath) != null
    private val fileOut = if (volumeBacked) null else FileOutputStream(File(filePath), append)
    private var volumeHasBytes =
        volumeBacked && append && PlatformFileSystem.exists(filePath) &&
        PlatformFileSystem.fileSize(filePath) > 0L

    init {
        if (volumeBacked) {
            val parent = filePath.substringBeforeLast('/', missingDelimiterValue = "")
            if (parent.isNotEmpty()) {
                PlatformFileSystem.ensureDirectory(parent)
            }
            if (!append) {
                runCatching { PlatformFileSystem.delete(filePath) }
                volumeHasBytes = false
            }
        } else {
            File(filePath).parentFile?.mkdirs()
        }
    }

    actual fun write(buffer: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        if (volumeBacked) {
            val chunk = buffer.copyOfRange(offset, offset + length)
            if (volumeHasBytes) {
                PlatformFileSystem.appendBytes(filePath, chunk)
            } else {
                PlatformFileSystem.writeBytes(filePath, chunk)
                volumeHasBytes = true
            }
        } else {
            fileOut!!.write(buffer, offset, length)
        }
    }

    actual fun flush() {
        fileOut?.flush()
    }

    actual override fun close() {
        fileOut?.run {
            flush()
            fd.sync()
            close()
        }
    }
}
