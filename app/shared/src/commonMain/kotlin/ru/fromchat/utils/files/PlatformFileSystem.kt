package ru.fromchat.utils.files

/**
 * Multiplatform file system API for basic file operations.
 * Use [getAppCacheDirectory] to obtain the platform cache directory.
 */
object PlatformFileSystem {

    /**
     * Returns true if a file exists at the given path.
     */
    fun exists(path: String): Boolean = expectExists(path)

    /**
     * Writes [bytes] to the file at [path], overwriting if it exists.
     */
    fun writeBytes(path: String, bytes: ByteArray) {
        expectWriteBytes(path, bytes)
    }

    /** Appends [bytes] to the file at [path], creating it when missing. */
    fun appendBytes(path: String, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        expectAppendBytes(path, bytes)
    }

    /** Size in bytes, or 0 when the file does not exist. */
    fun fileSize(path: String): Long = expectFileSize(path)

    /**
     * Deletes the file at [path]. No-op if the file does not exist.
     */
    fun delete(path: String) {
        expectDelete(path)
    }

    /**
     * Deletes all files in the directory [dirPath] whose filename starts with [namePrefix].
     */
    fun deleteFilesWithPrefix(dirPath: String, namePrefix: String) {
        expectDeleteFilesWithPrefix(dirPath, namePrefix)
    }

    /** Returns filenames (not paths) in [dirPath], or empty if the directory is missing. */
    fun listFileNamesInDirectory(dirPath: String): List<String> =
        expectListFileNamesInDirectory(dirPath)

    /**
     * Returns the platform-specific application cache directory path.
     * On Android: context.cacheDir (requires UtilsLibrary.init to be called).
     * On iOS: NSCachesDirectory.
     */
    fun getAppCacheDirectory(): String = expectGetAppCacheDirectory()

    /**
     * Creates the directory at [path] if it does not exist.
     * Returns the absolute path.
     */
    fun ensureDirectory(path: String): String {
        expectEnsureDirectory(path)
        return path
    }
}

internal expect fun expectExists(path: String): Boolean
internal expect fun expectWriteBytes(path: String, bytes: ByteArray)
internal expect fun expectAppendBytes(path: String, bytes: ByteArray)
internal expect fun expectFileSize(path: String): Long
internal expect fun expectDelete(path: String)
internal expect fun expectDeleteFilesWithPrefix(dirPath: String, namePrefix: String)
internal expect fun expectListFileNamesInDirectory(dirPath: String): List<String>
internal expect fun expectGetAppCacheDirectory(): String
internal expect fun expectEnsureDirectory(path: String)
