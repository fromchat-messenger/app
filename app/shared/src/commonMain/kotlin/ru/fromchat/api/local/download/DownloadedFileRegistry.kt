package ru.fromchat.api.local.download

import com.pr0gramm3r101.utils.files.PlatformFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.fromchat.api.local.cache.CacheContext
import ru.fromchat.api.local.cache.readOutboundFileBytes

/**
 * Maps DM file attachment slots to a user-chosen export URI (SAF / document picker).
 * Does not store file bytes — only the destination the user selected.
 */
object DownloadedFileRegistry {
    private const val INDEX_FILE = "downloaded_exports.json"
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val memory = mutableMapOf<String, String>()
    private var diskIndexLoaded = false

    fun messageIdFromStorageKey(storageKey: String): Int? {
        if (!storageKey.startsWith("file_") || storageKey.startsWith("file_c_")) return null
        return storageKey.removePrefix("file_").substringBefore('_').toIntOrNull()
    }

    fun fileIndexFromStorageKey(storageKey: String): Int? {
        if (storageKey.startsWith("file_c_")) {
            return storageKey.substringAfterLast('_').toIntOrNull()
        }
        if (storageKey.startsWith("file_")) {
            return storageKey.substringAfterLast('_').toIntOrNull()
        }
        return null
    }

    fun storageKey(
        messageId: Int,
        fileIndex: Int,
        clientMessageId: String? = null,
    ): String {
        if (messageId > 0) return "file_${messageId}_$fileIndex"
        val cid = clientMessageId?.trim()?.takeIf { it.isNotEmpty() }
        return if (cid != null) {
            "file_c_${sanitizeKeyPart(cid)}_$fileIndex"
        } else {
            "file_${messageId}_$fileIndex"
        }
    }

    fun progressLookupKeys(
        messageId: Int,
        fileIndex: Int,
        clientMessageId: String? = null,
    ): List<String> = buildList {
        add(storageKey(messageId, fileIndex, clientMessageId))
        if (messageId > 0) add("file_${messageId}_$fileIndex")
        val cid = clientMessageId?.trim()?.takeIf { it.isNotEmpty() }
        if (cid != null) add("file_c_${sanitizeKeyPart(cid)}_$fileIndex")
    }.distinct()

    fun resolveDownloadPercent(
        messageId: Int,
        fileIndex: Int,
        clientMessageId: String? = null,
        progressByKey: Map<String, Int> = emptyMap(),
    ): Int? {
        for (lookupKey in progressLookupKeys(messageId, fileIndex, clientMessageId)) {
            progressByKey[lookupKey]?.let { return it }
        }
        return null
    }

    suspend fun getExportUri(
        messageId: Int,
        fileIndex: Int,
        clientMessageId: String? = null,
    ): String? {
        ensureDiskIndexLoaded()
        return mutex.withLock {
            memory[storageKey(messageId, fileIndex, clientMessageId)]
        }
    }

    suspend fun setExportUri(
        messageId: Int,
        fileIndex: Int,
        clientMessageId: String? = null,
        exportUri: String,
    ) {
        ensureDiskIndexLoaded()
        mutex.withLock {
            val key = storageKey(messageId, fileIndex, clientMessageId)
            memory[key] = exportUri
            saveIndexLocked(memory)
        }
    }

    suspend fun removeExportUri(
        messageId: Int,
        fileIndex: Int,
        clientMessageId: String? = null,
    ) {
        ensureDiskIndexLoaded()
        mutex.withLock {
            val key = storageKey(messageId, fileIndex, clientMessageId)
            memory.remove(key)
            saveIndexLocked(memory)
        }
    }

    suspend fun invalidateForMessage(messageId: Int) {
        ensureDiskIndexLoaded()
        mutex.withLock {
            val prefix = "file_${messageId}_"
            memory.keys.removeAll { it.startsWith(prefix) }
            saveIndexLocked(memory)
        }
    }

    suspend fun invalidateForClientMessage(clientMessageId: String) {
        ensureDiskIndexLoaded()
        mutex.withLock {
            val prefix = "file_c_${sanitizeKeyPart(clientMessageId.trim())}_"
            memory.keys.removeAll { it.startsWith(prefix) }
            saveIndexLocked(memory)
        }
    }

    private suspend fun ensureDiskIndexLoaded() {
        if (diskIndexLoaded) return
        val index = withContext(Dispatchers.Default) { readIndexFromDisk() }
        mutex.withLock {
            if (!diskIndexLoaded) {
                memory.putAll(index)
                diskIndexLoaded = true
            }
        }
    }

    private fun sanitizeKeyPart(value: String): String =
        value.replace(Regex("[^a-zA-Z0-9._-]"), "_")

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
            diskIndexLoaded = false
        }
        withContext(Dispatchers.Default) {
            runCatching { PlatformFileSystem.delete(path) }
        }
    }

    private suspend fun readIndexFromDisk(): Map<String, String> {
        val path = indexPath() ?: return emptyMap()
        if (!PlatformFileSystem.exists(path)) return emptyMap()
        val bytes = runCatching {
            readOutboundFileBytes("file://$path")
        }.getOrNull() ?: return emptyMap()
        if (bytes.isEmpty()) return emptyMap()
        return runCatching {
            json.decodeFromString<Map<String, String>>(bytes.decodeToString())
        }.getOrDefault(emptyMap())
    }

    private fun saveIndexLocked(index: Map<String, String>) {
        val path = indexPath() ?: return
        val bytes = json.encodeToString(index).encodeToByteArray()
        PlatformFileSystem.writeBytes(path, bytes)
    }
}
