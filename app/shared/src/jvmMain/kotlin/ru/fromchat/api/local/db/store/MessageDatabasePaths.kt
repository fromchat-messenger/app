package ru.fromchat.api.local.db.store

import com.pr0gramm3r101.utils.files.PlatformFileSystem
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.OverlappingFileLockException

object MessageDatabasePaths {
    fun databaseFile(): File {
        val dir = File(PlatformFileSystem.getAppCacheDirectory(), "fromchat")
        return File(dir, "message_database.db")
    }

    fun lockTargetFiles(): List<File> {
        val db = databaseFile()
        return listOf(
            db,
            File("${db.path}-wal"),
            File("${db.path}-shm"),
            File("${db.path}-journal"),
        ).filter { it.exists() }
    }

    /** Database files plus the desktop instance lock sidecar for handle-based lock discovery. */
    fun lockProbeFiles(): List<File> {
        val db = databaseFile()
        val instanceLock = File(db.parentFile, "message_database.instance.lock")
        return listOf(
            db,
            File("${db.path}-wal"),
            File("${db.path}-shm"),
            File("${db.path}-journal"),
            instanceLock,
        ).distinctBy { it.absolutePath }
    }

    /**
     * Best-effort check for a **foreign** lock while this process does **not** have the DB open.
     * Returns false when only this process holds the files (JDBC keeps them open), so do not use
     * this while the app is running — use handle-based discovery instead.
     */
    fun isDatabaseLockedByAnotherProcess(): Boolean {
        val targets = lockTargetFiles()
        if (targets.isEmpty()) return false
        return targets.any { isFileLockedExclusively(it) }
    }

    private fun isFileLockedExclusively(file: File): Boolean =
        runCatching {
            RandomAccessFile(file, "rw").use { raf ->
                val channel = raf.channel
                val lock = try {
                    channel.tryLock()
                } catch (_: OverlappingFileLockException) {
                    return true
                }
                lock?.release()
                lock == null
            }
        }.getOrDefault(true)
}
