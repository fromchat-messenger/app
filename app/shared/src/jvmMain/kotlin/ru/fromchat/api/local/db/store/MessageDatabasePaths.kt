package ru.fromchat.api.local.db.store

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.pr0gramm3r101.utils.files.PlatformFileSystem
import ru.fromchat.api.local.db.isSqliteBusy
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

    fun isDatabaseLocked(): Boolean {
        val targets = lockTargetFiles()
        if (targets.isEmpty()) return false
        if (targets.any { isFileLockedExclusively(it) }) return true
        return isSqliteWriteLocked(databaseFile())
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

    private fun isSqliteWriteLocked(dbFile: File): Boolean =
        runCatching {
            val probe = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}?busy_timeout=1")
            try {
                probe.execute(null, "BEGIN IMMEDIATE;", 0)
                probe.execute(null, "ROLLBACK;", 0)
                false
            } finally {
                probe.close()
            }
        }.getOrElse { isSqliteBusy(it) }
}
