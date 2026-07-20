package ru.fromchat.api

import com.pr0gramm3r101.utils.settings.settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import ru.fromchat.Logger
import ru.fromchat.api.local.WebSocketManager
import ru.fromchat.api.local.db.store.ConnectionStateStore
import ru.fromchat.api.local.db.store.MessageCacheStore
import ru.fromchat.api.local.db.store.MessageRepository
import ru.fromchat.api.local.db.store.ProfileCache
import ru.fromchat.api.local.messages.DmInboundMessageProcessor
import ru.fromchat.api.local.messages.UpdatesBatchApplier
import ru.fromchat.api.local.messages.parseMessageTimestampMillis
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.api.schema.messages.dm.DmEnvelope
import ru.fromchat.api.schema.websocket.WebSocketCredentials
import ru.fromchat.api.schema.websocket.WebSocketMessage
import ru.fromchat.api.schema.websocket.requests.AckUpdatesRequest
import ru.fromchat.api.schema.websocket.requests.GetUpdatesRequest
import ru.fromchat.api.schema.websocket.requests.GetUpdatesResponse
import kotlin.concurrent.Volatile

/**
 * Per-device update cursor: advance + ack only after batches are applied successfully.
 */
object UpdateSyncManager {
    private const val KEY_UPDATES_LAST_SEQ_PREFIX = "updates_last_seq_user_"
    private const val HISTORY_PAGE_SIZE = 50
    private const val MAX_HISTORY_PAGES = 20

    private val _lastSeq = MutableStateFlow(0)
    val lastSeq: StateFlow<Int> = _lastSeq.asStateFlow()

    private val _lastMissedCount = MutableStateFlow<Int?>(null)
    val lastMissedCount: StateFlow<Int?> = _lastMissedCount.asStateFlow()

    private val applyMutex = Mutex()

    @Volatile
    private var gapDetectionInProgress: Boolean = false

    suspend fun initializeFromStorage(currentUserId: Int?) {
        val userId = currentUserId ?: return
        val key = KEY_UPDATES_LAST_SEQ_PREFIX + userId
        val stored = runCatching { settings.getInt(key, 0) }.getOrDefault(0)
        Logger.d("UpdateSyncManager", "Loaded lastSeq=$stored for userId=$userId")
        _lastSeq.value = stored
        ConnectionStateStore.updateSeqAndMissed(lastSeq = stored, missedCount = null)
    }

    /**
     * Apply a live or replayed updates envelope, then advance cursor and ack the server.
     *
     * Ack is fire-and-forget: [WebSocketManager.request] must not be awaited from the WS
     * receive loop (it would deadlock — the ack response cannot be read while this call blocks).
     */
    suspend fun onUpdatesEnvelope(jsonTree: JsonElement) {
        applyMutex.withLock {
            Logger.d("UpdateSync", "onUpdatesEnvelope begin")
            val seq = UpdatesBatchApplier.applyEnvelope(jsonTree) ?: run {
                Logger.w("UpdateSync", "onUpdatesEnvelope apply returned null")
                return@withLock
            }
            Logger.d(
                "UpdateSync",
                "onUpdatesEnvelope applied seq=$seq lastSeq=${_lastSeq.value}",
            )
            if (seq > _lastSeq.value) {
                persistLastSeq(seq)
                sendAckFireAndForget(seq)
            }
        }
    }

    fun updateMissedCount(missedCount: Int?) {
        _lastMissedCount.value = missedCount
        ConnectionStateStore.updateSeqAndMissed(lastSeq = _lastSeq.value, missedCount = missedCount)
    }

    fun resetInMemoryOnLogout() {
        _lastSeq.value = 0
        _lastMissedCount.value = null
        gapDetectionInProgress = false
        ConnectionStateStore.updateSeqAndMissed(lastSeq = 0, missedCount = null)
    }

    suspend fun clearPersistedSeqForUser(userId: Int) {
        runCatching {
            settings.remove("updates_last_seq_user_$userId")
        }
    }

    /**
     * Catch up from [lastSeq]: chunked getUpdates, or tooLong → history rebuild.
     * Does not advance the cursor until apply/rebuild succeeds.
     *
     * Loops until [GetUpdatesResponse.hasMore] is false. On repeated getUpdates failures
     * (e.g. prior ack-deadlock timeouts), falls back to a full history rebuild so the UI
     * is not left with first+last holes filled only by slow incremental envelopes.
     */
    suspend fun runGapDetectionIfNeeded() {
        if (gapDetectionInProgress) {
            Logger.d("UpdateSyncManager", "Gap detection already in progress, ignoring request")
            return
        }

        val token = ApiClient.token
        if (token.isNullOrEmpty()) {
            Logger.d("UpdateSyncManager", "No auth token; skipping gap detection")
            return
        }

        gapDetectionInProgress = true
        ConnectionStateStore.onUpdating(start = true)

        try {
            var rounds = 0
            var consecutiveFailures = 0
            while (rounds < 100) {
                rounds++
                val startSeq = _lastSeq.value
                Logger.i("UpdateSyncManager", "Gap detection from lastSeq=$startSeq (round=$rounds)")

                val response = requestGetUpdates(token, startSeq)
                if (response == null) {
                    consecutiveFailures++
                    Logger.w(
                        "UpdateSyncManager",
                        "getUpdates returned null (timeout/disconnect) " +
                            "failures=$consecutiveFailures lastSeq=$startSeq",
                    )
                    if (consecutiveFailures >= 2) {
                        Logger.w(
                            "UpdateSyncManager",
                            "Gap catch-up stalled — rebuilding from history",
                        )
                        rebuildStateFromHistory()
                        break
                    }
                    continue
                }
                consecutiveFailures = 0

                val gapHint = (response.lastSeq - startSeq).coerceAtLeast(response.missedCount)
                Logger.i(
                    "UpdateSyncManager",
                    "Gap detection result: status=${response.status}, lastSeq=${response.lastSeq}, " +
                        "missed=${response.missedCount}, hasMore=${response.hasMore}, " +
                        "gapHint=$gapHint clientSeq=$startSeq",
                )
                updateMissedCount(response.missedCount)

                when (response.status) {
                    "tooLong" -> {
                        val ok = rebuildStateFromHistory()
                        if (ok) {
                            persistLastSeq(response.lastSeq)
                            sendAckFireAndForget(response.lastSeq)
                        } else {
                            Logger.w("UpdateSyncManager", "History rebuild failed; leaving lastSeq=$startSeq")
                        }
                        break
                    }
                    "ok" -> {
                        // Envelopes for this chunk are applied on the receive path before this
                        // response is delivered; advance cursor here only when there was nothing to apply.
                        if (response.lastSeq > _lastSeq.value && response.missedCount == 0) {
                            persistLastSeq(response.lastSeq)
                            sendAckFireAndForget(response.lastSeq)
                        }
                        if (!response.hasMore) {
                            Logger.i(
                                "UpdateSyncManager",
                                "Gap catch-up complete after $rounds round(s) lastSeq=${_lastSeq.value}",
                            )
                            break
                        }
                        if (_lastSeq.value <= startSeq && response.missedCount > 0) {
                            // Chunk was announced but cursor did not advance — avoid tight spin.
                            Logger.w(
                                "UpdateSyncManager",
                                "Gap chunk did not advance cursor " +
                                    "(start=$startSeq now=${_lastSeq.value} missed=${response.missedCount})",
                            )
                            consecutiveFailures++
                            if (consecutiveFailures >= 2) {
                                val ok = rebuildStateFromHistory()
                                if (ok) {
                                    persistLastSeq(response.lastSeq)
                                    sendAckFireAndForget(response.lastSeq)
                                }
                                break
                            }
                        }
                    }
                    else -> {
                        Logger.w("UpdateSyncManager", "Unknown getUpdates status=${response.status}")
                        break
                    }
                }
            }
        } catch (t: Throwable) {
            Logger.w("UpdateSyncManager", "Gap detection failed: ${t.message}", t)
        } finally {
            ConnectionStateStore.onUpdating(start = false)
            gapDetectionInProgress = false
        }
    }

    private suspend fun requestGetUpdates(token: String, lastSeq: Int): GetUpdatesResponse? {
        val requestMessage = WebSocketMessage(
            type = "getUpdates",
            credentials = WebSocketCredentials(
                scheme = "Bearer",
                credentials = token,
            ),
            data = ApiClient.json.encodeToJsonElement(
                GetUpdatesRequest.serializer(),
                GetUpdatesRequest(lastSeq = lastSeq),
            ),
        )
        val response = WebSocketManager.request(requestMessage, timeoutMs = 30_000)
        val data = response?.data ?: return null
        return runCatching {
            ApiClient.json.decodeFromJsonElement(GetUpdatesResponse.serializer(), data)
        }.onFailure {
            Logger.w("UpdateSyncManager", "Failed to parse getUpdates response: ${it.message}", it)
        }.getOrNull()
    }

    /**
     * Send ack without waiting for a response. Must not use [WebSocketManager.request] from
     * the receive/apply path — that deadlocks the incoming frame loop for ~10s per envelope.
     */
    private suspend fun sendAckFireAndForget(seq: Int) {
        val token = ApiClient.token ?: return
        if (seq <= 0) return
        runCatching {
            WebSocketManager.send(
                WebSocketMessage(
                    type = "ackUpdates",
                    credentials = WebSocketCredentials(scheme = "Bearer", credentials = token),
                    data = ApiClient.json.encodeToJsonElement(
                        AckUpdatesRequest.serializer(),
                        AckUpdatesRequest(lastSeq = seq),
                    ),
                ),
            )
            Logger.d("UpdateSync", "ackUpdates sent (fire-and-forget) seq=$seq")
        }.onFailure {
            Logger.w("UpdateSyncManager", "ackUpdates failed for seq=$seq: ${it.message}", it)
        }
    }

    private suspend fun persistLastSeq(seq: Int) {
        val currentUserId = ApiClient.user?.id ?: return
        if (seq <= _lastSeq.value) return
        _lastSeq.value = seq
        ConnectionStateStore.updateSeqAndMissed(lastSeq = seq, missedCount = _lastMissedCount.value)
        withContext(Dispatchers.Default) {
            runCatching {
                settings.putInt(KEY_UPDATES_LAST_SEQ_PREFIX + currentUserId, seq)
            }.onFailure {
                Logger.w("UpdateSyncManager", "Failed to persist lastSeq=$seq: ${it.message}", it)
            }
        }
    }

    suspend fun rebuildStateFromHistory(): Boolean = withContext(Dispatchers.Default) {
        Logger.i("UpdateSyncManager", "Rebuilding local state from history (tooLong)")
        runCatching {
            ChatListSync.syncDmConversationsForRebuild()
            rebuildPublicHistory()
            rebuildDmHistories()
            true
        }.onFailure {
            Logger.w("UpdateSyncManager", "rebuildStateFromHistory failed: ${it.message}", it)
        }.getOrDefault(false)
    }

    private suspend fun rebuildPublicHistory() {
        val collected = LinkedHashMap<Int, Message>()
        var beforeId: Int? = null
        repeat(MAX_HISTORY_PAGES) {
            val page = ApiClient.getMessages(limit = HISTORY_PAGE_SIZE, beforeId = beforeId)
            if (page.messages.isEmpty()) return@repeat
            page.messages.forEach { msg ->
                ProfileCache.mergePreviewFromPublicMessage(msg)
                collected[msg.id] = msg
            }
            val oldest = page.messages.minByOrNull { it.id } ?: return@repeat
            if (page.messages.size < HISTORY_PAGE_SIZE) return@repeat
            beforeId = oldest.id
        }
        val ordered = collected.values.sortedBy {
            parseMessageTimestampMillis(it.timestamp) ?: 0L
        }
        MessageCacheStore.clearPublicMessages()
        Logger.i(
            "UpdateSync",
            "rebuildPublicHistory messages=${ordered.size} — replaceAll=true",
        )
        MessageRepository.replacePublicMessages(ordered, replaceAll = true)
    }

    private suspend fun rebuildDmHistories() {
        val conversations = MessageRepository.loadCachedDmConversations()
        Logger.i("UpdateSync", "rebuildDmHistories conversations=${conversations.size}")
        for (conversation in conversations) {
            val otherId = conversation.otherUserId
            MessageCacheStore.clearDmMessages(otherId)
            var beforeId: Int? = null
            var pageCount = 0
            repeat(MAX_HISTORY_PAGES) {
                val page = ApiClient.getDmHistory(otherId, limit = HISTORY_PAGE_SIZE, beforeId = beforeId)
                if (page.messages.isEmpty()) return@repeat
                pageCount++
                for (envelope in page.messages) {
                    val element = ApiClient.json.encodeToJsonElement(DmEnvelope.serializer(), envelope)
                    DmInboundMessageProcessor.processNew(element)
                }
                val oldest = page.messages.minByOrNull { it.id } ?: return@repeat
                if (page.messages.size < HISTORY_PAGE_SIZE) return@repeat
                beforeId = oldest.id
            }
            Logger.d("UpdateSync", "rebuildDmHistories otherUserId=$otherId pages=$pageCount")
        }
    }
}
