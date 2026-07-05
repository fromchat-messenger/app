package ru.fromchat.api.local.cache

import com.pr0gramm3r101.utils.files.PlatformFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.fromchat.api.local.download.cachedAttachmentFileSize

@Serializable
data class PendingFileSaveEntry(
    val storageKey: String,
    val destinationUri: String,
    val filename: String,
    val mimeType: String,
    val clientMessageId: String? = null,
)

/**
 * Outbox for "save attachment to user folder" — survives process death until copy completes.
 */
object PendingFileSaveRegistry {
    private const val INDEX_FILE = "pending_file_saves.json"
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val memory = mutableListOf<PendingFileSaveEntry>()
    private var diskLoaded = false

    suspend fun schedule(entry: PendingFileSaveEntry) {
        ensureLoaded()
        mutex.withLock {
            memory.removeAll { it.storageKey == entry.storageKey }
            memory.add(entry)
            persistLocked()
        }
        enqueuePlatformCopy(entry.storageKey)
    }

    suspend fun remove(storageKey: String) {
        ensureLoaded()
        mutex.withLock {
            if (memory.removeAll { it.storageKey == storageKey }) {
                persistLocked()
            }
        }
    }

    suspend fun listPending(): List<PendingFileSaveEntry> {
        ensureLoaded()
        return mutex.withLock { memory.toList() }
    }

    suspend fun onCacheReady(storageKey: String) {
        ensureLoaded()
        val hasPending = mutex.withLock {
            memory.any { it.storageKey == storageKey }
        }
        if (hasPending) {
            enqueuePlatformCopy(storageKey)
        }
    }

    private suspend fun ensureLoaded() {
        if (diskLoaded) return
        val fromDisk = withContext(Dispatchers.Default) { readIndexFromDisk() }
        mutex.withLock {
            if (!diskLoaded) {
                memory.clear()
                memory.addAll(fromDisk)
                diskLoaded = true
            }
        }
        memory.forEach { entry ->
            val cacheUri = DecryptedFileCache.getCachedUriForStorageKey(entry.storageKey)
            if (cacheUri != null && cachedAttachmentFileSize(cacheUri) > 0L) {
                enqueuePlatformCopy(entry.storageKey)
            }
        }
    }

    private fun indexPath(): String? = instanceIndexPath(
        runCatching { CacheContext.requireActiveInstanceId() }.getOrNull() ?: "default",
    )

    private fun instanceIndexPath(instanceId: String): String? {
        val base = PlatformFileSystem.getAppCacheDirectory()
        if (base.isEmpty()) return null
        val safe = instanceId.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")
        if (safe.isEmpty()) return null
        val dir = "$base/fromchat/instances/$safe"
        PlatformFileSystem.ensureDirectory(dir)
        return "$dir/$INDEX_FILE"
    }

    suspend fun clearForInstance(instanceId: String) {
        val path = instanceIndexPath(instanceId) ?: return
        mutex.withLock {
            memory.clear()
            diskLoaded = false
        }
        withContext(Dispatchers.Default) {
            runCatching { PlatformFileSystem.delete(path) }
        }
    }

    private suspend fun readIndexFromDisk(): List<PendingFileSaveEntry> {
        val path = indexPath() ?: return emptyList()
        if (!PlatformFileSystem.exists(path)) return emptyList()
        val bytes = runCatching {
            readOutboundFileBytes("file://$path")
        }.getOrNull() ?: return emptyList()
        if (bytes.isEmpty()) return emptyList()
        return runCatching {
            json.decodeFromString<List<PendingFileSaveEntry>>(bytes.decodeToString())
        }.getOrDefault(emptyList())
    }

    private fun persistLocked() {
        val path = indexPath() ?: return
        val bytes = json.encodeToString(memory).encodeToByteArray()
        PlatformFileSystem.writeBytes(path, bytes)
    }
}

internal expect fun enqueuePlatformCopy(storageKey: String)
