package ru.fromchat.incognito

import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

internal actual object IncognitoContainer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncScheduled = AtomicBoolean(false)

    actual val containerFilePath: String?
        get() = containerPath

    actual val workRootPath: String?
        get() = workRoot

    private var containerPath: String? = null
    private var workRoot: String? = null
    private var volumeDir: String? = null

    actual fun open(key: ByteArray, volumeDirectory: String) {
        volumeDir = volumeDirectory
        File(volumeDirectory).mkdirs()
        val fileName = IncognitoContainerOnDisk.newRandomFileName()
        containerPath = File(volumeDirectory, fileName).absolutePath
        workRoot = File(
            volumeDirectory,
            ".live-${UUID.randomUUID()}",
        ).apply { mkdirs() }.absolutePath
        File(workRoot, "cache").mkdirs()
        val container = File(containerPath!!)
        if (container.isFile && container.length() > 0L) {
            runCatching {
                val plain = IncognitoVault.open(key, container.readBytes())
                unpackWorkDir(workRoot!!, plain)
            }
        }
    }

    actual fun syncToContainer(key: ByteArray) {
        val work = workRoot ?: return
        val target = containerPath ?: return
        val packed = packWorkDir(work)
        File(target).writeBytes(IncognitoVault.seal(key, packed))
    }

    actual fun closeAndWipe(key: ByteArray?) {
        workRoot?.let { File(it).deleteRecursively() }
        volumeDir?.let { File(it).deleteRecursively() }
        workRoot = null
        containerPath = null
        volumeDir = null
    }

    actual fun scheduleSyncAfterWrite(key: ByteArray) {
        if (!syncScheduled.compareAndSet(false, true)) return
        scope.launch {
            delay(750)
            syncScheduled.set(false)
            if (IncognitoMode.active) {
                runCatching { syncToContainer(key) }
            }
        }
    }
}
