@file:OptIn(ExperimentalForeignApi::class)

package ru.fromchat.utils.files

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite

internal actual fun expectExists(path: String): Boolean =
    NSFileManager.defaultManager.fileExistsAtPath(path)

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual fun expectWriteBytes(path: String, bytes: ByteArray) {
    val parent = path.substringBeforeLast('/', missingDelimiterValue = "")
    if (parent.isNotEmpty()) {
        NSFileManager.defaultManager.createDirectoryAtPath(parent, true, null, null)
    }
    val nsData = bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
    nsData.writeToFile(path, true)
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun expectAppendBytes(path: String, bytes: ByteArray) {
    val parent = path.substringBeforeLast('/', missingDelimiterValue = "")
    if (parent.isNotEmpty()) {
        NSFileManager.defaultManager.createDirectoryAtPath(parent, true, null, null)
    }
    val file = fopen(path, "ab") ?: error("Failed to open file for append")
    try {
        bytes.usePinned { pinned ->
            val written = fwrite(pinned.addressOf(0), 1.convert(), bytes.size.convert(), file).toInt()
            if (written != bytes.size) {
                error("Short write")
            }
        }
    } finally {
        fclose(file)
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun expectFileSize(path: String): Long {
    if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return 0L
    val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path, null) ?: return 0L
    return (attrs["NSFileSize"] as? NSNumber)?.longValue ?: 0L
}

internal actual fun expectDelete(path: String) {
    NSFileManager.defaultManager.removeItemAtPath(path, null)
}

internal actual fun expectDeleteFilesWithPrefix(dirPath: String, namePrefix: String) {
    val contents = NSFileManager.defaultManager.contentsOfDirectoryAtPath(dirPath, null)
        ?: return
    contents.filterIsInstance<String>().forEach { name ->
        if (name.startsWith(namePrefix)) {
            NSFileManager.defaultManager.removeItemAtPath("$dirPath/$name", null)
        }
    }
}

internal actual fun expectListFileNamesInDirectory(dirPath: String): List<String> {
    val contents = NSFileManager.defaultManager.contentsOfDirectoryAtPath(dirPath, null)
        ?: return emptyList()
    return contents.filterIsInstance<String>()
}

internal actual fun expectGetAppCacheDirectory(): String {
    val paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
    return (paths.firstOrNull() as? String) ?: ""
}

internal actual fun expectEnsureDirectory(path: String) {
    NSFileManager.defaultManager.createDirectoryAtPath(path, true, null, null)
}
