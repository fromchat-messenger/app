package ru.fromchat.api.local.cache

import com.pr0gramm3r101.utils.files.PlatformFileSystem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.download.AttachmentDownloadNotifier
import ru.fromchat.api.local.download.AttachmentDownloadProgress
import ru.fromchat.api.schema.messages.dm.DmEnvelope
import ru.fromchat.api.schema.messages.dm.DmFile
import ru.fromchat.api.crypto.decryptFileToPath
import ru.fromchat.api.local.AttachmentMediaLog
import ru.fromchat.api.local.download.AttachmentDownloadScheduler
import ru.fromchat.api.local.download.checkAttachmentDownloadActive
import ru.fromchat.api.local.download.ensureAttachmentDownloadActive
import ru.fromchat.ui.chat.utils.AttachmentDownloadVisibility
import ru.fromchat.api.local.cache.DecryptedImageCache.storageKey
import ru.fromchat.api.local.download.LocalDecodedImageCache

/**
 * Disk + in-memory cache for decrypted DM images.
 * Key is stable per attachment slot ([client_message_id] + index, or [messageId] + index).
 */
object DecryptedImageCache {
    private const val SUBDIR = "decrypted_images"

    /** True when [uri] points at a file under this cache (safe to persist for offline preview). */
    fun isDecryptedImageCacheUri(uri: String?): Boolean {
        if (uri.isNullOrBlank()) return false
        val path = uri.removePrefix("file://")
        return path.contains("/$SUBDIR/") || path.endsWith("/$SUBDIR")
    }

    private var cacheDir: String? = null
    private val cacheMutex = Mutex()
    private val memoryCache = mutableMapOf<String, String>()

    fun init(cacheDirPath: String) {
        cacheDir = cacheDirPath
    }

    /**
     * Stable identity for one attachment slot. Does not include [filePath] so confirm/scroll
     * cannot miss cache when the server path string varies; edits/deletes invalidate explicitly.
     */
    fun storageKey(
        messageId: Int,
        fileIndex: Int,
        clientMessageId: String? = null,
    ): String {
        if (messageId > 0) {
            return "img_${messageId}_$fileIndex"
        }
        val cid = clientMessageId?.trim()?.takeIf { it.isNotEmpty() }
        return if (cid != null) {
            "img_c_${sanitizeKeyPart(cid)}_$fileIndex"
        } else {
            "img_${messageId}_$fileIndex"
        }
    }

    /** Keys that may refer to the same slot (progress UI + cache aliases). */
    fun progressLookupKeys(
        messageId: Int,
        fileIndex: Int,
        clientMessageId: String? = null,
    ): List<String> = buildList {
        add(storageKey(messageId, fileIndex, clientMessageId))
        if (messageId > 0) add("img_${messageId}_$fileIndex")
        val cid = clientMessageId?.trim()?.takeIf { it.isNotEmpty() }
        if (cid != null) add("img_c_${sanitizeKeyPart(cid)}_$fileIndex")
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

    /** Synchronous disk lookup (safe to call from the main thread during composition). */
    fun getCached(
        messageId: Int,
        fileIndex: Int,
        clientMessageId: String? = null,
    ): String? {
        val cid = clientMessageId?.trim()?.takeIf { it.isNotEmpty() }
        if (cid != null) {
            readDisk(storageKey(messageId, fileIndex, cid))?.let { return it }
        }
        return readDisk(storageKey(messageId, fileIndex, null))
    }

    fun getUriForStorageKey(storageKey: String): String? = readDisk(storageKey)

    fun messageIdFromStorageKey(storageKey: String): Int? {
        if (!storageKey.startsWith("img_") || storageKey.startsWith("img_c_")) return null
        return storageKey.removePrefix("img_").substringBefore('_').toIntOrNull()
    }

    /** After confirm, cache file may only exist under client-id key; copy to message-id key for reopen. */
    suspend fun ensureDiskAliasForMessageId(
        messageId: Int,
        fileIndex: Int,
        clientMessageId: String?,
    ) {
        if (messageId <= 0) return
        val idKey = storageKey(messageId, fileIndex, null)
        if (readDisk(idKey) != null) return
        val cid = clientMessageId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val cidKey = storageKey(messageId, fileIndex, cid)
        val sourceUri = readDisk(cidKey) ?: return
        val bytes = runCatching {
            readOutboundFileBytes(sourceUri)
        }.getOrNull() ?: return
        if (bytes.isEmpty()) return
        withContext(Dispatchers.Default) {
            cacheMutex.withLock {
                if (readDisk(idKey) == null) {
                    writeCacheLocked(idKey, bytes)
                    AttachmentMediaLog.diskCache(
                        "alias_ok",
                        "from" to cidKey,
                        "to" to idKey,
                        "bytes" to bytes.size,
                    )
                }
            }
        }
    }

    /**
     * Returns a cached file URI, decrypting and persisting once per [storageKey].
     */
    suspend fun getOrDecrypt(
        messageId: Int,
        fileIndex: Int,
        file: DmFile,
        envelope: DmEnvelope?,
        currentUserId: Int?,
        clientMessageId: String? = null,
        messageLabel: String? = null,
    ): String? {
        if (envelope == null) return null
        val key = storageKey(messageId, fileIndex, clientMessageId)

        getCached(messageId, fileIndex, clientMessageId)?.let { uri ->
            AttachmentMediaLog.diskCache(
                "getOrDecrypt_hit",
                "key" to key,
                "msgId" to messageId,
                "clientId" to clientMessageId,
                "uri" to uri,
            )
            return uri
        }

        AttachmentMediaLog.diskCache(
            "getOrDecrypt_miss",
            "key" to key,
            "msgId" to messageId,
            "clientId" to clientMessageId,
            "file" to file.path,
        )

        cacheMutex.withLock { resolveUriLocked(key) }?.let { return it }

        val label = AttachmentMediaLog.messageLabel(messageLabel)
        AttachmentDownloadNotifier.beginDownload(
            messageId = messageId,
            fileIndex = fileIndex,
            clientMessageId = clientMessageId,
        )
        val uri = withContext(Dispatchers.Default) {
            runCatching {
                AttachmentDownloadScheduler.run(
                    storageKey = key,
                    messageId = messageId,
                    work = {
                        AttachmentMediaLog.download(
                            "decrypt_start",
                            "key" to key,
                            "file" to file.path,
                            "msgId" to messageId,
                            "visible" to AttachmentDownloadVisibility.isPrioritized(messageId),
                            "msg" to label,
                        )
                        decryptAndPersist(
                            key = key,
                            messageId = messageId,
                            fileIndex = fileIndex,
                            clientMessageId = clientMessageId,
                            file = file,
                            envelope = envelope,
                            currentUserId = currentUserId,
                            messageLabel = label,
                        )
                    },
                )
            }.onFailure { error ->
                if (error !is CancellationException) {
                    ApiClient.clearPartialEncryptedDownload(key)
                    AttachmentMediaLog.download(
                        "decrypt_exception",
                        "key" to key,
                        "msgId" to messageId,
                        "msg" to label,
                        "err" to (error.message ?: error::class.simpleName),
                    )
                    AttachmentDownloadNotifier.emit(
                        AttachmentDownloadProgress.Failed(
                            storageKey = key,
                            error = error.message ?: "decrypt_failed",
                        ),
                        messageLabel = label,
                        messageId = messageId,
                        fileIndex = fileIndex,
                        clientMessageId = clientMessageId,
                    )
                }
            }.getOrNull()
        }
        if (uri != null && messageId > 0) {
            ensureDiskAliasForMessageId(messageId, fileIndex, clientMessageId)
        }
        return uri
    }

    suspend fun invalidateForMessage(messageId: Int) {
        val dir = ensureCacheDir() ?: return
        withContext(Dispatchers.Default) {
            cacheMutex.withLock {
                memoryCache.keys.removeAll { it.startsWith("img_${messageId}_") }
            }
            runCatching {
                PlatformFileSystem.deleteFilesWithPrefix(dir, "img_${messageId}_")
            }
            LocalDecodedImageCache.evictPrefix("img_${messageId}_")
        }
    }

    suspend fun invalidateForClientMessage(clientMessageId: String) {
        val cid = clientMessageId.trim()
        if (cid.isEmpty()) return
        val prefix = "img_c_${sanitizeKeyPart(cid)}_"
        val dir = ensureCacheDir() ?: return
        withContext(Dispatchers.Default) {
            cacheMutex.withLock {
                memoryCache.keys.removeAll { it.startsWith(prefix) }
            }
            runCatching {
                PlatformFileSystem.deleteFilesWithPrefix(dir, prefix)
            }
            LocalDecodedImageCache.evictPrefix(prefix)
        }
    }

    suspend fun invalidateForFile(
        messageId: Int,
        fileIndex: Int,
        clientMessageId: String? = null,
    ) {
        val key = storageKey(messageId, fileIndex, clientMessageId)
        withContext(Dispatchers.Default) {
            cacheMutex.withLock { memoryCache.remove(key) }
            diskPath(key)?.let { invalidatePath(it) }
            LocalDecodedImageCache.evict(key)
        }
    }

    suspend fun seedFromLocalFile(
        messageId: Int,
        fileIndex: Int,
        localFileUri: String,
        clientMessageId: String? = null,
    ): String? = withContext(Dispatchers.Default) {
        val key = storageKey(messageId, fileIndex, clientMessageId)
        cacheMutex.withLock { resolveUriLocked(key) }?.let { existing ->
            AttachmentMediaLog.diskCache(
                "seed_skip_exists",
                "key" to key,
                "uri" to existing,
            )
            return@withContext existing
        }
        val t0 = AttachmentMediaLog.nowMs()
        val bytes = runCatching {
            readOutboundFileBytes(localFileUri)
        }.getOrNull()
        if (bytes == null) {
            AttachmentMediaLog.diskCache("seed_read_failed", "key" to key, "src" to localFileUri)
            return@withContext null
        }
        val uri = cacheMutex.withLock {
            resolveUriLocked(key) ?: writeCacheLocked(key, bytes)
        }
        AttachmentMediaLog.diskCache(
            if (uri != null) "seed_ok" else "seed_write_failed",
            "key" to key,
            "bytes" to bytes.size,
            "ms" to (AttachmentMediaLog.nowMs() - t0),
            "uri" to uri,
        )
        uri
    }

    private suspend fun decryptAndPersist(
        key: String,
        messageId: Int,
        fileIndex: Int,
        clientMessageId: String?,
        file: DmFile,
        envelope: DmEnvelope,
        currentUserId: Int?,
        messageLabel: String? = null,
    ): String? {
        ensureAttachmentDownloadActive(key)
        cacheMutex.withLock { resolveUriLocked(key) }?.let { return it }
        AttachmentDownloadNotifier.emit(
            AttachmentDownloadProgress.InProgress(key, 1),
            messageLabel = messageLabel,
            messageId = messageId,
            fileIndex = fileIndex,
            clientMessageId = clientMessageId,
        )
        val t0 = AttachmentMediaLog.nowMs()
        val outputPath = diskPath(key)
        if (outputPath == null) {
            ApiClient.clearPartialEncryptedDownload(key)
            AttachmentDownloadNotifier.emit(
                AttachmentDownloadProgress.Failed(key, "cache_write_failed"),
                messageLabel = messageLabel,
                messageId = messageId,
                fileIndex = fileIndex,
                clientMessageId = clientMessageId,
            )
            return null
        }
        val decryptedSize = runCatching {
            decryptFileToPath(
                file = file,
                envelope = envelope,
                currentUserId = currentUserId,
                outputPath = outputPath,
                downloadResumeKey = key,
                onDownloadProgress = { percent ->
                    checkAttachmentDownloadActive(key)
                    AttachmentDownloadNotifier.emit(
                        AttachmentDownloadProgress.InProgress(key, percent.coerceIn(0, 100)),
                        messageLabel = messageLabel,
                        messageId = messageId,
                        fileIndex = fileIndex,
                        clientMessageId = clientMessageId,
                    )
                },
            )
        }.onFailure { error ->
            if (error is CancellationException) {
                throw error
            }
            ApiClient.clearPartialEncryptedDownload(key)
            AttachmentMediaLog.download(
                "decrypt_failed",
                "key" to key,
                "msgId" to messageId,
                "msg" to messageLabel,
                "file" to file.path,
                "err" to (error.message ?: error::class.simpleName),
            )
            AttachmentDownloadNotifier.emit(
                AttachmentDownloadProgress.Failed(key, error.message ?: "download_failed"),
                messageLabel = messageLabel,
                messageId = messageId,
                fileIndex = fileIndex,
                clientMessageId = clientMessageId,
            )
        }.getOrNull()
        if (decryptedSize == null) {
            ApiClient.clearPartialEncryptedDownload(key)
            return null
        }
        ensureAttachmentDownloadActive(key)
        AttachmentDownloadNotifier.emit(
            AttachmentDownloadProgress.InProgress(key, 99),
            messageLabel = messageLabel,
            messageId = messageId,
            fileIndex = fileIndex,
            clientMessageId = clientMessageId,
        )
        ApiClient.clearPartialEncryptedDownload(key)
        val uri = cacheMutex.withLock {
            resolveUriLocked(key) ?: commitCachePathLocked(key, outputPath)
        }
        if (uri == null) {
            ApiClient.clearPartialEncryptedDownload(key)
            AttachmentMediaLog.download(
                "decrypt_persist_failed",
                "key" to key,
                "msg" to messageLabel,
                "bytes" to decryptedSize,
            )
            AttachmentDownloadNotifier.emit(
                AttachmentDownloadProgress.Failed(key, "cache_write_failed"),
                messageLabel = messageLabel,
                messageId = messageId,
                fileIndex = fileIndex,
                clientMessageId = clientMessageId,
            )
            return null
        }
        AttachmentMediaLog.download(
            "decrypt_persist_ok",
            "key" to key,
            "bytes" to decryptedSize,
            "ms" to (AttachmentMediaLog.nowMs() - t0),
            "uri" to uri,
            "msg" to messageLabel,
        )
        AttachmentDownloadNotifier.emit(
            AttachmentDownloadProgress.Success(storageKey = key, messageId = messageId),
            messageLabel = messageLabel,
            messageId = messageId,
            fileIndex = fileIndex,
            clientMessageId = clientMessageId,
        )
        return uri
    }

    private fun resolveUriLocked(storageKey: String): String? {
        memoryCache[storageKey]?.let { uri ->
            if (uriFileExists(uri)) return uri
            memoryCache.remove(storageKey)
        }
        val fromDisk = readDisk(storageKey) ?: return null
        memoryCache[storageKey] = fromDisk
        AttachmentMediaLog.diskCache("disk_hit", "key" to storageKey, "uri" to fromDisk)
        return fromDisk
    }

    private fun uriFileExists(fileUri: String): Boolean {
        val path = fileUri.removePrefix("file://")
        return path.isNotEmpty() && PlatformFileSystem.exists(path)
    }

    private fun ensureCacheDir(): String? {
        val base = PlatformFileSystem.getAppCacheDirectory()
        if (base.isEmpty()) return null
        val path = cacheDir?.takeIf { it.endsWith(SUBDIR) } ?: "$base/$SUBDIR"
        return runCatching {
            PlatformFileSystem.ensureDirectory(path)
            if (!PlatformFileSystem.exists(path)) return null
            cacheDir = path
            path
        }.getOrNull()
    }

    private fun sanitizeKeyPart(value: String): String =
        value.replace(Regex("[^a-zA-Z0-9._-]"), "_")

    private fun diskPath(storageKey: String): String? {
        val dir = ensureCacheDir() ?: return null
        return "$dir/$storageKey"
    }

    private fun readDisk(storageKey: String): String? {
        val path = diskPath(storageKey) ?: return null
        if (!PlatformFileSystem.exists(path)) return null
        return "file://$path"
    }

    private fun writeCacheLocked(storageKey: String, bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val path = diskPath(storageKey) ?: return null
        return runCatching {
            PlatformFileSystem.writeBytes(path, bytes)
            if (!PlatformFileSystem.exists(path)) return null
            val uri = "file://$path"
            memoryCache[storageKey] = uri
            uri
        }.getOrElse {
            invalidatePath(path)
            null
        }
    }

    private fun commitCachePathLocked(storageKey: String, path: String): String? {
        if (!PlatformFileSystem.exists(path)) return null
        val uri = "file://$path"
        memoryCache[storageKey] = uri
        return uri
    }

    private fun invalidatePath(path: String) {
        runCatching { PlatformFileSystem.delete(path) }
    }

    suspend fun clearMemoryCache() {
        cacheMutex.withLock {
            memoryCache.clear()
        }
    }
}
