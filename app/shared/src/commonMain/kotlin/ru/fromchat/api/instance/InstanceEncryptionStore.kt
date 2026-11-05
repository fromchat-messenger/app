package ru.fromchat.api.instance

import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.fromchat.api.ApiClient
import ru.fromchat.api.schema.instance.InstanceInfoResponse
import ru.fromchat.config.ServerConfig
import ru.fromchat.config.ServerConfigData
import ru.fromchat.config.Settings
import ru.fromchat.logging.Logger

sealed interface EncryptionModeChangeEvent {
    val snapshot: InstanceEncryptionSnapshot
    val previous: InstanceEncryptionSnapshot?

    /** Server broadcast or explicit refresh with version bump. */
    data class PolicyUpdated(
        override val snapshot: InstanceEncryptionSnapshot,
        override val previous: InstanceEncryptionSnapshot?,
    ) : EncryptionModeChangeEvent

    /** Detected from a failed send while policy moved forward (silent server change). */
    data class DetectedOnSendFailure(
        override val snapshot: InstanceEncryptionSnapshot,
        override val previous: InstanceEncryptionSnapshot?,
    ) : EncryptionModeChangeEvent
}

object InstanceEncryptionStore {
    private const val TAG = "InstanceEncryptionStore"

    /** Client-only cool-down after a detected mode change before outbound DM sends are allowed. */
    const val MODE_CHANGE_SEND_COOLDOWN_MS = 5_000L

    private val mutex = Mutex()
    private val _active = MutableStateFlow<InstanceEncryptionSnapshot?>(null)
    val active: StateFlow<InstanceEncryptionSnapshot?> = _active.asStateFlow()

    private val _events = MutableSharedFlow<EncryptionModeChangeEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<EncryptionModeChangeEvent> = _events.asSharedFlow()

    @Volatile
    private var lastModeChangeDetectedAtMs: Long? = null

    /** Demo-only hook for queued-message video; 0 = disabled. */
    var debugOutboundDelayMs: Long = 0L

    suspend fun fetchInstanceInfo(apiBaseUrl: String): InstanceInfoResponse =
        ApiClient.fetchInstanceInfo(apiBaseUrl)

    suspend fun refreshFromNetwork(
        config: ServerConfigData,
        apiBaseUrl: String = ServerConfig.apiBaseUrl,
    ): InstanceEncryptionSnapshot? {
        val info = runCatching { fetchInstanceInfo(apiBaseUrl) }.getOrElse {
            Logger.w(TAG, "fetch instance/info failed", it)
            return _active.value
        }
        return applyInstanceInfo(info, config, emitEvents = true)
    }

    suspend fun applyInstanceInfo(
        info: InstanceInfoResponse,
        config: ServerConfigData,
        emitEvents: Boolean,
    ): InstanceEncryptionSnapshot? {
        val mode = InstanceEncryptionMode.fromWire(info.encryptionMode) ?: run {
            Logger.w(TAG, "unknown encryption mode ${info.encryptionMode}")
            return null
        }
        val snapshot = InstanceEncryptionSnapshot(
            instanceId = info.instanceId.trim(),
            configKey = config.configKey(),
            mode = mode,
            version = info.encryptionModeVersion.coerceAtLeast(1),
            changedAt = info.encryptionModeChangedAt,
            protocol = info.protocol.ifBlank { "1" },
        )
        return mutex.withLock {
            val previous = _active.value
            _active.value = snapshot
            Logger.i(
                TAG,
                "applyInstanceInfo: previous=${previous?.mode}@v${previous?.version}, " +
                    "new=${snapshot.mode}@v${snapshot.version}, emitEvents=$emitEvents",
            )
            InstanceEncryptionCache.upsert(snapshot)
            val versionBumped = (previous != null &&
                (previous.version != snapshot.version || previous.mode != snapshot.mode)) ||
                (previous == null && snapshot.version > 1)
            Logger.i(
                TAG,
                "versionBumped=$versionBumped (prev!=null=${previous != null}, " +
                    "verDiff=${previous?.version != snapshot.version}, " +
                    "modeDiff=${previous?.mode != snapshot.mode})",
            )
            if (emitEvents && versionBumped) {
                noteModeChangeDetectedLocked()
                Logger.i(TAG, "Emitting PolicyUpdated event!")
                _events.tryEmit(
                    EncryptionModeChangeEvent.PolicyUpdated(snapshot, previous),
                )
            }
            snapshot
        }
    }

    suspend fun handleRemotePolicyPush(
        mode: String,
        version: Int,
        changedAt: String?,
    ) {
        val config = ServerConfig.serverConfig.value ?: return
        val current = _active.value
        val instanceId = current?.instanceId?.ifBlank { null }
            ?: Settings.lastKnownServerInstanceId.trim().ifBlank { return }
        applyInstanceInfo(
            InstanceInfoResponse(
                instanceId = instanceId,
                protocol = current?.protocol ?: "1",
                encryptionMode = mode,
                encryptionModeVersion = version,
                encryptionModeChangedAt = changedAt,
            ),
            config,
            emitEvents = true,
        )
    }

    suspend fun onSendPolicyMismatch(fresh: InstanceEncryptionSnapshot) {
        mutex.withLock {
            val previous = _active.value
            _active.value = fresh
            InstanceEncryptionCache.upsert(fresh)
            noteModeChangeDetectedLocked()
            _events.tryEmit(
                EncryptionModeChangeEvent.DetectedOnSendFailure(fresh, previous),
            )
        }
    }

    fun isWithinModeChangeSendCooldown(
        nowMs: Long = Clock.System.now().toEpochMilliseconds(),
    ): Boolean {
        val at = lastModeChangeDetectedAtMs ?: return false
        return nowMs - at < MODE_CHANGE_SEND_COOLDOWN_MS
    }

    fun modeChangeCooldownRemainingMs(
        nowMs: Long = Clock.System.now().toEpochMilliseconds(),
    ): Long {
        val at = lastModeChangeDetectedAtMs ?: return 0L
        return (MODE_CHANGE_SEND_COOLDOWN_MS - (nowMs - at)).coerceAtLeast(0L)
    }

    suspend fun refreshActiveFromNetwork(): InstanceEncryptionSnapshot? {
        val config = ServerConfig.serverConfig.value ?: Settings.readServerConfig()
        return refreshFromNetwork(config, ServerConfig.apiBaseUrl)
    }

    suspend fun ensureActivePolicy(): InstanceEncryptionSnapshot {
        _active.value?.let { return it }
        return refreshActiveFromNetwork() ?: error("Instance encryption policy unavailable")
    }

    fun modeMismatchWarning(
        cached: InstanceEncryptionSnapshot?,
        live: InstanceEncryptionSnapshot,
    ): Boolean {
        if (cached == null) return false
        val sameTarget = cached.instanceId == live.instanceId || cached.configKey == live.configKey
        return sameTarget && (cached.mode != live.mode || cached.version != live.version)
    }

    suspend fun bootstrapFromProbe(info: InstanceInfoResponse, config: ServerConfigData) {
        applyInstanceInfo(info, config, emitEvents = false)
    }

    fun clearSessionPolicy() {
        _active.value = null
        lastModeChangeDetectedAtMs = null
    }

    private fun noteModeChangeDetectedLocked() {
        lastModeChangeDetectedAtMs = Clock.System.now().toEpochMilliseconds()
        Logger.i(TAG, "mode change cooldown started (${MODE_CHANGE_SEND_COOLDOWN_MS}ms)")
    }
}
