package ru.fromchat.api.local.cache

import com.pr0gramm3r101.utils.files.PlatformFileSystem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.fromchat.api.ApiClient
import ru.fromchat.api.crypto.decryptFileToPath
import ru.fromchat.api.crypto.dm.decryptFailureMessage
import ru.fromchat.api.local.AttachmentMediaLog
import ru.fromchat.api.local.download.AttachmentDownloadNotifier
import ru.fromchat.api.local.download.AttachmentDownloadProgress
import ru.fromchat.api.local.download.AttachmentDownloadScheduler
import ru.fromchat.api.local.download.checkAttachmentDownloadActive
import ru.fromchat.api.local.download.ensureAttachmentDownloadActive
import ru.fromchat.api.schema.messages.dm.DmEnvelope
import ru.fromchat.api.schema.messages.dm.DmFile
import ru.fromchat.api.local.download.DownloadedFileRegistry

/**
 * Disk cache for decrypted non-image DM attachments (bytes on disk, opened via platform URI).
 */
object DecryptedFileCache {
    private const val SUBDIR = "decrypted_files"

    private var cacheDir: String? = null
    private val cacheMutex = Mutex()
    private val memoryCache = mutableMapOf<String, String>()

    fun isDecryptedFileCacheUri(uri: String?): Boolean {
        if (uri.isNullOrBlank()) return false
        val path = uri.removePrefix("file://")
        return path.contains("/$SUBDIR/")
    }

    fun storageKey(
        messageId: Int,
        fileIndex: Int,
        clientMessageId: String? = null,
    ): String = DownloadedFileRegistry.storageKey(messageId, fileIndex, clientMessageId)

    fun getCached(
        messageId: Int,
        fileIndex: Int,
        clientMessageId: String? = null,
    ): String? {
        val key = storageKey(messageId, fileIndex, clientMessageId)
        memoryCache[key]?.takeIf { uriFileExists(it) }?.let { return it }
        return readDisk(key)
    }

    /** Resolves cache URI for a [DownloadedFileRegistry.storageKey] (server id or client-id key). */
    fun getCachedUriForStorageKey(storageKey: String): String? {
        val messageId = DownloadedFileRegistry.messageIdFromStorageKey(storageKey)
        val fileIndex = DownloadedFileRegistry.fileIndexFromStorageKey(storageKey) ?: 0
        if (messageId != null && messageId > 0) {
            getCached(messageId, fileIndex, clientMessageId = null)?.let { return it }
        }
        memoryCache[storageKey]?.takeIf { uriFileExists(it) }?.let { return it }
        return readDisk(storageKey)
    }

    /**
     * Copies a local file into the decrypted-file cache using [displayFilename] for the on-disk name
     * (preserves extension for installers / "Open with").
     */
    suspend fun seedFromLocalFile(
        messageId: Int,
        fileIndex: Int,
        localFileUri: String,
        displayFilename: String,
        clientMessageId: String? = null,
    ): String? = withContext(Dispatchers.Default) {
        val key = storageKey(messageId, fileIndex, clientMessageId)
        cacheMutex.withLock { resolveUriLocked(key) }?.let { existing ->
            AttachmentMediaLog.diskCache("file_seed_skip_exists", "key" to key, "uri" to existing)
            return@withContext existing
        }
        val path = diskPath(key, displayFilename) ?: return@withContext null
        val t0 = AttachmentMediaLog.nowMs()
        val copied = runCatching {
            copyOutboundFileToPath(localFileUri, path)
        }.onFailure {
            AttachmentMediaLog.diskCache(
                "file_seed_copy_failed",
                "key" to key,
                "src" to localFileUri,
                "err" to (it.message ?: it::class.simpleName),
            )
        }.isSuccess
        if (!copied || !PlatformFileSystem.exists(path)) {
            return@withContext null
        }
        val uri = cacheMutex.withLock {
            commitCachePathLocked(key, path)
        }
        AttachmentMediaLog.diskCache(
            if (uri != null) "file_seed_ok" else "file_seed_write_failed",
            "key" to key,
            "bytes" to PlatformFileSystem.fileSize(path),
            "ms" to (AttachmentMediaLog.nowMs() - t0),
            "uri" to uri,
        )
        uri
    }

    /** After server confirm, copy client-id cache entry to the real message-id key. */
    suspend fun ensureDiskAliasForMessageId(
        messageId: Int,
        fileIndex: Int,
        clientMessageId: String?,
    ) {
        if (messageId <= 0) return
        val idKey = storageKey(messageId, fileIndex, null)
        if (getCached(messageId, fileIndex, null) != null) return
        val cid = clientMessageId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val cidKey = storageKey(messageId, fileIndex, cid)
        val sourceUri = cacheMutex.withLock { resolveUriLocked(cidKey) }
            ?: readDisk(cidKey)
            ?: return
        val sourcePath = sourceUri.removePrefix("file://")
        if (!PlatformFileSystem.exists(sourcePath)) return
        val displayName = filenameFromDiskBasename(cidKey, sourcePath.substringAfterLast('/'))
            ?: sourcePath.substringAfterLast('/')
        val destPath = diskPath(idKey, displayName) ?: return
        if (sourcePath == destPath) {
            cacheMutex.withLock {
                if (resolveUriLocked(idKey) == null) {
                    commitCachePathLocked(idKey, destPath)
                }
            }
            return
        }
        withContext(Dispatchers.Default) {
            runCatching {
                copyOutboundFileToPath(sourceUri, destPath)
            }.onSuccess {
                cacheMutex.withLock {
                    if (resolveUriLocked(idKey) == null) {
                        commitCachePathLocked(idKey, destPath)
                        AttachmentMediaLog.diskCache("file_alias_ok", "from" to cidKey, "to" to idKey)
                    }
                }
            }
        }
    }

    suspend fun invalidateForClientMessage(clientMessageId: String) {
        val cid = clientMessageId.trim()
        if (cid.isEmpty()) return
        val prefix = "file_c_${sanitizeKeyPart(cid)}_"
        val dir = ensureCacheDir() ?: return
        withContext(Dispatchers.Default) {
            cacheMutex.withLock {
                memoryCache.keys.removeAll { it.startsWith(prefix) }
            }
            runCatching {
                PlatformFileSystem.deleteFilesWithPrefix(dir, prefix)
            }
        }
    }

    suspend fun invalidateForMessage(messageId: Int) {
        if (messageId <= 0) return
        val dir = ensureCacheDir() ?: return
        withContext(Dispatchers.Default) {
            cacheMutex.withLock {
                memoryCache.keys.removeAll { it.startsWith("file_${messageId}_") }
            }
            runCatching {
                PlatformFileSystem.deleteFilesWithPrefix(dir, "file_${messageId}_")
            }
        }
    }

    suspend fun getOrDecrypt(
        messageId: Int,
        fileIndex: Int,
        file: DmFile,
        envelope: DmEnvelope,
        currentUserId: Int?,
        clientMessageId: String? = null,
        messageLabel: String? = null,
    ): String? {
        val key = storageKey(messageId, fileIndex, clientMessageId)
        getCached(messageId, fileIndex, clientMessageId)?.let { return it }
        cacheMutex.withLock { resolveUriLocked(key) }?.let { return it }

        val label = AttachmentMediaLog.messageLabel(messageLabel)
        AttachmentDownloadNotifier.beginDownload(
            messageId = messageId,
            fileIndex = fileIndex,
            clientMessageId = clientMessageId,
            mirrorAsFileAttachment = true,
        )

        return withContext(Dispatchers.Default) {
            runCatching {
                AttachmentDownloadScheduler.run(
                    storageKey = key,
                    messageId = messageId,
                    keepAliveInBackground = true,
                    work = {
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
                }
            }.getOrNull()
        }
    }

    private suspend fun decryptAndPersist(
        key: String,
        messageId: Int,
        fileIndex: Int,
        clientMessageId: String?,
        file: DmFile,
        envelope: DmEnvelope,
        currentUserId: Int?,
        messageLabel: String?,
    ): String? {
        ensureAttachmentDownloadActive(key)
        cacheMutex.withLock { resolveUriLocked(key) }?.let { return it }

        AttachmentDownloadNotifier.emit(
            AttachmentDownloadProgress.InProgress(key, 1),
            messageLabel = messageLabel,
            messageId = messageId,
            fileIndex = fileIndex,
            clientMessageId = clientMessageId,
            mirrorAsFileAttachment = true,
        )

        val outputPath = diskPath(key, file.name)
        if (outputPath == null) {
            AttachmentDownloadNotifier.emit(
                AttachmentDownloadProgress.Failed(key, "cache_write_failed"),
                messageLabel = messageLabel,
                messageId = messageId,
                fileIndex = fileIndex,
                clientMessageId = clientMessageId,
                mirrorAsFileAttachment = true,
            )
            return null
        }

        try {
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
                        mirrorAsFileAttachment = true,
                    )
                },
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (PlatformFileSystem.exists(outputPath)) {
                PlatformFileSystem.delete(outputPath)
            }
            ApiClient.clearPartialEncryptedDownload(key)
            AttachmentDownloadNotifier.emit(
                AttachmentDownloadProgress.Failed(
                    key,
                    error.decryptFailureMessage(),
                ),
                messageLabel = messageLabel,
                messageId = messageId,
                fileIndex = fileIndex,
                clientMessageId = clientMessageId,
                mirrorAsFileAttachment = true,
            )
            return null
        }

        ensureAttachmentDownloadActive(key)

        AttachmentDownloadNotifier.emit(
            AttachmentDownloadProgress.InProgress(key, 99),
            messageLabel = messageLabel,
            messageId = messageId,
            fileIndex = fileIndex,
            clientMessageId = clientMessageId,
            mirrorAsFileAttachment = true,
        )
        ApiClient.clearPartialEncryptedDownload(key)
        val uri = cacheMutex.withLock {
            resolveUriLocked(key) ?: commitCachePathLocked(key, outputPath)
        }
        if (uri == null) {
            ApiClient.clearPartialEncryptedDownload(key)
            AttachmentDownloadNotifier.emit(
                AttachmentDownloadProgress.Failed(key, "cache_write_failed"),
                messageLabel = messageLabel,
                messageId = messageId,
                fileIndex = fileIndex,
                clientMessageId = clientMessageId,
                mirrorAsFileAttachment = true,
            )
            return null
        }
        DownloadedFileRegistry.setExportUri(
            messageId = messageId,
            fileIndex = fileIndex,
            clientMessageId = clientMessageId,
            exportUri = uri,
        )
        AttachmentDownloadNotifier.emit(
            AttachmentDownloadProgress.Success(storageKey = key, messageId = messageId),
            messageLabel = messageLabel,
            messageId = messageId,
            fileIndex = fileIndex,
            clientMessageId = clientMessageId,
            mirrorAsFileAttachment = true,
        )
        PendingFileSaveRegistry.onCacheReady(key)
        return uri
    }

    private fun resolveUriLocked(storageKey: String): String? {
        memoryCache[storageKey]?.let { uri ->
            if (uriFileExists(uri)) return uri
            memoryCache.remove(storageKey)
        }
        val fromDisk = readDisk(storageKey) ?: return null
        memoryCache[storageKey] = fromDisk
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

    private fun diskPath(storageKey: String, displayFilename: String): String? {
        val dir = ensureCacheDir() ?: return null
        return "$dir/${diskBasename(storageKey, displayFilename)}"
    }

    private fun diskBasename(storageKey: String, displayFilename: String): String {
        val safeName = sanitizeCacheFilename(displayFilename)
        return "${storageKey}_$safeName"
    }

    private fun filenameFromDiskBasename(storageKey: String, basename: String): String? {
        val prefix = "${storageKey}_"
        if (!basename.startsWith(prefix)) return null
        val rest = basename.removePrefix(prefix)
        return rest.takeIf { it.isNotEmpty() }
    }

    private fun resolveDiskPath(storageKey: String): String? {
        val dir = ensureCacheDir() ?: return null
        val legacy = "$dir/$storageKey"
        if (PlatformFileSystem.exists(legacy)) return legacy
        val prefix = "${storageKey}_"
        val match = PlatformFileSystem.listFileNamesInDirectory(dir)
            .firstOrNull { it.startsWith(prefix) }
        return match?.let { "$dir/$it" }
    }

    private fun readDisk(storageKey: String): String? {
        val path = resolveDiskPath(storageKey) ?: return null
        return "file://$path"
    }

    internal fun sanitizeCacheFilename(filename: String): String {
        val base = filename.substringAfterLast('/').substringBefore('?').trim()
        val cleaned = base.replace(Regex("[^a-zA-Z0-9._+-]"), "_")
        return cleaned.take(180).ifEmpty { "attachment" }
    }

    private fun commitCachePathLocked(storageKey: String, path: String): String? {
        if (!PlatformFileSystem.exists(path)) return null
        if (PlatformFileSystem.fileSize(path) <= 0L) {
            PlatformFileSystem.delete(path)
            return null
        }
        val uri = "file://$path"
        memoryCache[storageKey] = uri
        return uri
    }

    private fun sanitizeKeyPart(value: String): String =
        value.replace(Regex("[^a-zA-Z0-9._-]"), "_")
}
