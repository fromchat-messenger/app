package ru.fromchat.utils.files

import ru.fromchat.incognito.IncognitoMode
import ru.fromchat.incognito.IncognitoVolumeFileSystem

object PlatformFileSystem {

    fun exists(path: String): Boolean {
        IncognitoVolumeFileSystem.relativeKeyForAppPath(path)?.let { key ->
            return IncognitoVolumeFileSystem.exists(key)
        }
        return expectExists(path)
    }

    fun writeBytes(path: String, bytes: ByteArray) {
        IncognitoVolumeFileSystem.relativeKeyForAppPath(path)?.let { key ->
            IncognitoVolumeFileSystem.write(key, bytes)
            return
        }
        expectWriteBytes(path, bytes)
    }

    fun appendBytes(path: String, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        IncognitoVolumeFileSystem.relativeKeyForAppPath(path)?.let { key ->
            IncognitoVolumeFileSystem.append(key, bytes)
            return
        }
        expectAppendBytes(path, bytes)
    }

    fun fileSize(path: String): Long {
        IncognitoVolumeFileSystem.relativeKeyForAppPath(path)?.let { key ->
            return IncognitoVolumeFileSystem.fileSize(key)
        }
        return expectFileSize(path)
    }

    fun delete(path: String) {
        IncognitoVolumeFileSystem.relativeKeyForAppPath(path)?.let { key ->
            IncognitoVolumeFileSystem.delete(key)
            return
        }
        expectDelete(path)
    }

    fun deleteFilesWithPrefix(dirPath: String, namePrefix: String) {
        IncognitoVolumeFileSystem.relativeKeyForAppPath(dirPath)?.let { key ->
            IncognitoVolumeFileSystem.deleteFilesWithPrefix(key, namePrefix)
            return
        }
        expectDeleteFilesWithPrefix(dirPath, namePrefix)
    }

    fun listFileNamesInDirectory(dirPath: String): List<String> {
        IncognitoVolumeFileSystem.relativeKeyForAppPath(dirPath)?.let { key ->
            return IncognitoVolumeFileSystem.listFileNamesInDirectory(key)
        }
        return expectListFileNamesInDirectory(dirPath)
    }

    fun getAppCacheDirectory(): String =
        if (IncognitoMode.active) {
            defaultAppDataRootForPlatform()
        } else {
            expectGetAppCacheDirectory()
        }

    fun ensureDirectory(path: String): String {
        IncognitoVolumeFileSystem.relativeKeyForAppPath(path)?.let { key ->
            IncognitoVolumeFileSystem.ensureDirectory(key)
            return path
        }
        expectEnsureDirectory(path)
        return path
    }

    internal fun readBytes(path: String): ByteArray {
        IncognitoVolumeFileSystem.relativeKeyForAppPath(path)?.let { key ->
            return IncognitoVolumeFileSystem.read(key)
        }
        return expectReadBytes(path)
    }
}

internal expect fun defaultAppDataRootForPlatform(): String
internal expect fun expectExists(path: String): Boolean
internal expect fun expectWriteBytes(path: String, bytes: ByteArray)
internal expect fun expectAppendBytes(path: String, bytes: ByteArray)
internal expect fun expectFileSize(path: String): Long
internal expect fun expectDelete(path: String)
internal expect fun expectDeleteFilesWithPrefix(dirPath: String, namePrefix: String)
internal expect fun expectListFileNamesInDirectory(dirPath: String): List<String>
internal expect fun expectGetAppCacheDirectory(): String
internal expect fun expectEnsureDirectory(path: String)
internal expect fun expectReadBytes(path: String): ByteArray
