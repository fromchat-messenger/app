package ru.fromchat.api.local.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.db.store.MessageCacheStore
import ru.fromchat.api.local.download.AttachmentDownloadNotifier.hydrateFromDisk
import ru.fromchat.api.local.download.AttachmentDownloadNotifier.progressFlow
import ru.fromchat.api.local.download.AttachmentDownloadNotifier.progressPercentByKey
import ru.fromchat.api.local.messages.DownloadProgressThrottle
import ru.fromchat.api.local.AttachmentMediaLog
import ru.fromchat.api.local.cache.DecryptedFileCache
import ru.fromchat.api.local.cache.DecryptedImageCache

sealed class AttachmentDownloadProgress {
    data class InProgress(val storageKey: String, val percent: Int) : AttachmentDownloadProgress()
    data class Success(
        val storageKey: String,
        val messageId: Int = 0,
    ) : AttachmentDownloadProgress()
    data class Failed(val storageKey: String, val error: String) : AttachmentDownloadProgress()
}

/**
 * In-chat download/decrypt progress (no system notification).
 * [progressPercentByKey] is the source of truth for UI; [progressFlow] is for one-shot side effects.
 */
object AttachmentDownloadNotifier {
    private var inFlightCheck: (String) -> Boolean = { false }

    internal fun bindInFlightCheck(check: (String) -> Boolean) {
        inFlightCheck = check
    }

    private val _progressFlow = MutableSharedFlow<AttachmentDownloadProgress>(extraBufferCapacity = 64)
    val progressFlow: SharedFlow<AttachmentDownloadProgress> = _progressFlow

    private val _progressPercentByKey = MutableStateFlow<Map<String, Int>>(emptyMap())
    val progressPercentByKey: StateFlow<Map<String, Int>> = _progressPercentByKey.asStateFlow()

    private val _failedKeys = MutableStateFlow<Set<String>>(emptySet())
    val failedKeys: StateFlow<Set<String>> = _failedKeys.asStateFlow()

    private val _cancelledKeys = MutableStateFlow<Set<String>>(emptySet())
    val cancelledKeys: StateFlow<Set<String>> = _cancelledKeys.asStateFlow()

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val resumeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val progressThrottleByKey = mutableMapOf<String, DownloadProgressThrottle>()

    fun emit(
        progress: AttachmentDownloadProgress,
        messageLabel: String? = null,
        messageId: Int = 0,
        fileIndex: Int = 0,
        clientMessageId: String? = null,
        mirrorAsFileAttachment: Boolean = false,
    ) {
        val msg = AttachmentMediaLog.messageLabel(messageLabel)
        val primaryKey = when (progress) {
            is AttachmentDownloadProgress.InProgress -> progress.storageKey
            is AttachmentDownloadProgress.Success -> progress.storageKey
            is AttachmentDownloadProgress.Failed -> progress.storageKey
        }
        val mirrorKeys = mirrorKeysFor(
            primaryKey = primaryKey,
            messageId = messageId,
            fileIndex = fileIndex,
            clientMessageId = clientMessageId,
            mirrorAsFileAttachment = mirrorAsFileAttachment,
        )
        when (progress) {
            is AttachmentDownloadProgress.InProgress -> Unit
            is AttachmentDownloadProgress.Success ->
                AttachmentMediaLog.download(
                    "success",
                    "key" to progress.storageKey,
                    "msgId" to progress.messageId,
                    "msg" to msg,
                )
            is AttachmentDownloadProgress.Failed ->
                AttachmentMediaLog.download(
                    "failed",
                    "key" to progress.storageKey,
                    "err" to progress.error,
                    "msg" to msg,
                )
        }
        when (progress) {
            is AttachmentDownloadProgress.InProgress -> {
                val pct = progress.percent.coerceIn(1, 100)
                val throttle = throttleFor(mirrorKeys)
                val publishUi = throttle.shouldPublishUi(pct)
                val publishNotif = mirrorAsFileAttachment && throttle.shouldPublishNotification(pct)
                if (publishUi || publishNotif) {
                    if (pct == 1 || pct % 15 == 0 || pct >= 95) {
                        AttachmentMediaLog.download(
                            "progress",
                            "key" to progress.storageKey,
                            "pct" to pct,
                            "msg" to msg,
                            "mirror" to mirrorKeys.joinToString(","),
                        )
                    }
                }
                if (publishUi) {
                    _progressPercentByKey.update { map ->
                        map + mirrorKeys.associateWith { pct }
                    }
                }
                if (publishNotif) {
                    AttachmentDownloadForeground.onFileDownloadProgress(
                        percent = pct,
                        displayLabel = messageLabel,
                    )
                }
            }
            is AttachmentDownloadProgress.Success -> {
                mirrorKeys.forEach { progressThrottleByKey.remove(it) }
                _progressPercentByKey.update { map -> map - mirrorKeys.toSet() }
                _cancelledKeys.update { cancelled -> cancelled - mirrorKeys.toSet() }
                _failedKeys.update { failed -> failed - mirrorKeys.toSet() }
                mirrorKeys.forEach { ApiClient.markPartialDownloadUserDismissed(it, dismissed = false) }
                if (mirrorAsFileAttachment) {
                    AttachmentDownloadForeground.onFileDownloadProgress(
                        percent = 100,
                        displayLabel = messageLabel,
                    )
                }
            }
            is AttachmentDownloadProgress.Failed -> {
                mirrorKeys.forEach { progressThrottleByKey.remove(it) }
                _progressPercentByKey.update { map -> map - mirrorKeys.toSet() }
                _failedKeys.update { keys -> keys + mirrorKeys.toSet() }
            }
        }
        mainScope.launch {
            _progressFlow.emit(progress)
        }
    }

    fun clearProgress(
        messageId: Int,
        fileIndex: Int,
        clientMessageId: String? = null,
        mirrorAsFileAttachment: Boolean = false,
    ) {
        val keys = lookupKeys(messageId, fileIndex, clientMessageId, mirrorAsFileAttachment).toSet()
        _progressPercentByKey.update { map -> map - keys }
        _failedKeys.update { failed -> failed - keys }
        _cancelledKeys.update { cancelled -> cancelled - keys }
        keys.forEach { ApiClient.markPartialDownloadUserDismissed(it, dismissed = false) }
    }

    /**
     * Prepares a new download or resumes a paused partial. Clears stale data only when not resuming.
     */
    fun beginDownload(
        messageId: Int,
        fileIndex: Int,
        clientMessageId: String? = null,
        mirrorAsFileAttachment: Boolean = false,
    ) {
        val keys = lookupKeys(messageId, fileIndex, clientMessageId, mirrorAsFileAttachment)
        val resuming = keys.any { ApiClient.hasResumablePartialOnDisk(it) }
        _cancelledKeys.update { cancelled -> cancelled - keys.toSet() }
        _failedKeys.update { failed -> failed - keys.toSet() }
        if (resuming) {
            val percent = keys.mapNotNull { ApiClient.loadPartialDownloadPercent(it) }.maxOrNull()
                ?.coerceIn(1, 99)
                ?: 1
            applyProgressPercent(keys, percent)
            keys.forEach {
                ApiClient.markPartialDownloadPaused(it, paused = false)
                ApiClient.markPartialDownloadUserDismissed(it, dismissed = false)
            }
        } else {
            keys.forEach { ApiClient.clearPartialEncryptedDownload(it) }
            applyProgressPercent(keys, 1)
        }
    }

    /**
     * Stops UI progress and marks the download paused. Partial encrypted bytes stay on disk for resume.
     */
    fun cancelDownload(
        messageId: Int,
        fileIndex: Int,
        clientMessageId: String? = null,
        mirrorAsFileAttachment: Boolean = false,
    ) {
        val keys = lookupKeys(messageId, fileIndex, clientMessageId, mirrorAsFileAttachment)
        val percent = keys.mapNotNull { _progressPercentByKey.value[it] }.maxOrNull()
            ?: keys.mapNotNull { ApiClient.loadPartialDownloadPercent(it) }.maxOrNull()
            ?: 1
        keys.forEach { key ->
            ApiClient.savePartialDownloadProgress(key, percent)
            ApiClient.markPartialDownloadUserDismissed(key, dismissed = true)
        }
        _progressPercentByKey.update { map -> map - keys.toSet() }
        _failedKeys.update { failed -> failed - keys.toSet() }
        _cancelledKeys.update { cancelled -> cancelled + keys.toSet() }
    }

    suspend fun restorePausedForAttachment(
        messageId: Int,
        fileIndex: Int,
        clientMessageId: String? = null,
        mirrorAsFileAttachment: Boolean = false,
    ) {
        val keys = lookupKeys(messageId, fileIndex, clientMessageId, mirrorAsFileAttachment)
        keys.forEach { key ->
            if (ApiClient.hasResumablePartialOnDisk(key)) {
                ApiClient.anchorPartialDownloadMetaIfNeeded(key)
            }
        }
        val resumable = keys.filter { ApiClient.hasResumablePartialOnDisk(it) }
        if (resumable.isEmpty()) return

        val percent = resumable.mapNotNull { ApiClient.loadPartialDownloadPercent(it) }.maxOrNull()
            ?.coerceIn(1, 99)
            ?: return
        applyProgressPercent(keys, percent)

        val dismissed = resumable.filter { ApiClient.isPartialDownloadUserDismissed(it) }
        if (dismissed.isNotEmpty()) {
            val activeDismissed = dismissed.filter { inFlightCheck(it) }.toSet()
            _cancelledKeys.update { cancelled ->
                (cancelled - dismissed.toSet()) + (dismissed.toSet() - activeDismissed)
            }
        }
    }

    suspend fun hydrateFromDisk() {
        ApiClient.hydratePausedDownloadsFromDisk()
        val dismissed = ApiClient.listResumablePartialDownloadKeys()
            .filter { ApiClient.isPartialDownloadUserDismissed(it) }
        if (dismissed.isNotEmpty()) {
            _cancelledKeys.update { cancelled -> cancelled + dismissed.toSet() }
        }
    }

    /** @deprecated Use [hydrateFromDisk] + [ru.fromchat.api.local.workers.AttachmentTransferBootstrap.runColdStart]. */
    suspend fun restoreAllPausedFromDisk() = hydrateFromDisk()

    suspend fun resumeInterruptedDownloadsOnAppStart() {
        val keys = ApiClient.listAutoResumablePartialDownloadKeys()
        if (keys.isEmpty()) return
        val currentUserId = ApiClient.user?.id
        for (storageKey in keys.distinct()) {
            val resolved = MessageCacheStore.findMessageForAttachmentStorageKey(storageKey) ?: continue
            val message = resolved.message
            val fileIndex = resolved.fileIndex
            val file = message.files?.getOrNull(fileIndex) ?: continue
            val envelope = message.dmEnvelope ?: continue
            val clientMessageId = message.client_message_id?.trim()?.takeIf { it.isNotEmpty() }
            val mirrorAsFile = storageKey.startsWith("file_")
            if (mirrorAsFile) {
                if (DecryptedFileCache.getCached(message.id, fileIndex, clientMessageId) != null) continue
            } else {
                if (DecryptedImageCache.getCached(message.id, fileIndex, clientMessageId) != null) continue
            }
            beginDownload(
                messageId = message.id,
                fileIndex = fileIndex,
                clientMessageId = clientMessageId,
                mirrorAsFileAttachment = mirrorAsFile,
            )
            resumeScope.launch {
                runCatching {
                    if (mirrorAsFile) {
                        DmFileDownloader.downloadToCache(
                            messageId = message.id,
                            fileIndex = fileIndex,
                            file = file,
                            envelope = envelope,
                            currentUserId = currentUserId,
                            clientMessageId = clientMessageId,
                        )
                    } else {
                        DecryptedImageCache.getOrDecrypt(
                            messageId = message.id,
                            fileIndex = fileIndex,
                            file = file,
                            envelope = envelope,
                            currentUserId = currentUserId,
                            clientMessageId = clientMessageId,
                        )
                    }
                }
            }
        }
    }

    fun isCancelled(storageKey: String): Boolean =
        storageKey in _cancelledKeys.value || ApiClient.isPartialDownloadUserDismissed(storageKey)

    fun isCancelled(
        messageId: Int,
        fileIndex: Int,
        clientMessageId: String? = null,
        mirrorAsFileAttachment: Boolean = false,
    ): Boolean {
        val keys = lookupKeys(messageId, fileIndex, clientMessageId, mirrorAsFileAttachment)
        return keys.any { isCancelled(it) }
    }

    fun hasResumablePartial(
        messageId: Int,
        fileIndex: Int,
        clientMessageId: String? = null,
        mirrorAsFileAttachment: Boolean = false,
    ): Boolean {
        val keys = lookupKeys(messageId, fileIndex, clientMessageId, mirrorAsFileAttachment)
        return keys.any { ApiClient.hasResumablePartialOnDisk(it) }
    }

    private fun lookupKeys(
        messageId: Int,
        fileIndex: Int,
        clientMessageId: String? = null,
        mirrorAsFileAttachment: Boolean = false,
    ): List<String> = if (mirrorAsFileAttachment) {
        DownloadedFileRegistry.progressLookupKeys(messageId, fileIndex, clientMessageId)
    } else {
        DecryptedImageCache.progressLookupKeys(messageId, fileIndex, clientMessageId)
    }

    fun isFailed(
        messageId: Int,
        fileIndex: Int,
        clientMessageId: String? = null,
        mirrorAsFileAttachment: Boolean = false,
    ): Boolean {
        val keys = lookupKeys(messageId, fileIndex, clientMessageId, mirrorAsFileAttachment)
        return keys.any { it in _failedKeys.value }
    }

    private fun mirrorKeysFor(
        primaryKey: String,
        messageId: Int,
        fileIndex: Int,
        clientMessageId: String?,
        mirrorAsFileAttachment: Boolean,
    ): List<String> = when {
        mirrorAsFileAttachment || primaryKey.startsWith("file_") ->
            DownloadedFileRegistry.progressLookupKeys(messageId, fileIndex, clientMessageId)
        else ->
            DecryptedImageCache.progressLookupKeys(messageId, fileIndex, clientMessageId)
    }.ifEmpty { listOf(primaryKey) }

    private fun applyProgressPercent(keys: List<String>, percent: Int) {
        val pct = percent.coerceIn(1, 99)
        val throttle = throttleFor(keys)
        if (throttle.shouldPublishUi(pct)) {
            _progressPercentByKey.update { map -> map + keys.associateWith { pct } }
        }
    }

    private fun throttleFor(keys: List<String>): DownloadProgressThrottle {
        val id = keys.firstOrNull() ?: return DownloadProgressThrottle()
        return progressThrottleByKey.getOrPut(id) { DownloadProgressThrottle() }
    }

    fun resetOnLogout() {
        _progressPercentByKey.value = emptyMap()
        _failedKeys.value = emptySet()
        _cancelledKeys.value = emptySet()
        progressThrottleByKey.clear()
    }
}
