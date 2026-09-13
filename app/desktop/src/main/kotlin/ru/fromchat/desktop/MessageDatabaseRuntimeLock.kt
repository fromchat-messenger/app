package ru.fromchat.desktop

import ru.fromchat.api.local.db.store.MessageDatabasePaths
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock

/**
 * Holds an exclusive lock on a sidecar file for the lifetime of this process so only one
 * desktop instance can use the message database at a time.
 */
internal object MessageDatabaseRuntimeLock {
    private var lockHolder: RandomAccessFile? = null
    private var fileLock: FileLock? = null

    fun isHeld(): Boolean = fileLock?.isValid == true

    fun tryAcquire(): Boolean {
        if (isHeld()) return true
        val lockFile = instanceLockFile()
        lockFile.parentFile?.mkdirs()
        return runCatching {
            val raf = RandomAccessFile(lockFile, "rw")
            val lock = raf.channel.tryLock()
            if (lock == null) {
                raf.close()
                false
            } else {
                lockHolder = raf
                fileLock = lock
                true
            }
        }.getOrDefault(false)
    }

    fun release() {
        runCatching { fileLock?.release() }
        runCatching { lockHolder?.close() }
        fileLock = null
        lockHolder = null
    }

    private fun instanceLockFile(): File {
        val dbDir = MessageDatabasePaths.databaseFile().parentFile
        return File(dbDir, "message_database.instance.lock")
    }
}
