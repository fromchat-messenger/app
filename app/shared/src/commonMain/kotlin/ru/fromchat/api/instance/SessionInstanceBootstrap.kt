package ru.fromchat.api.instance

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.db.store.InstanceRegistryStore
import ru.fromchat.api.local.db.store.MessageRepository
import ru.fromchat.api.local.download.AttachmentDownloadNotifier
import ru.fromchat.api.local.send.scheduleOutboxProcessing
import ru.fromchat.config.Settings
import ru.fromchat.api.local.cache.CacheContext

sealed interface SessionBootstrapResult {
    data object Ready : SessionBootstrapResult
    data object OfflineCached : SessionBootstrapResult
    data object LogoutRequired : SessionBootstrapResult
}

private val bootstrapScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
private val refreshMutex = Mutex()

private val attachmentResumeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

private fun scheduleAttachmentResumeAfterSession() {
    attachmentResumeScope.launch {
        runCatching { AttachmentDownloadNotifier.hydrateFromDisk() }
        runCatching { AttachmentDownloadNotifier.resumeInterruptedDownloadsOnAppStart() }
    }
}

private fun activateInstance(instanceId: String) {
    CacheContext.setActiveInstance(instanceId, ApiClient.user?.id)
    scheduleOutboxProcessing(instanceId)
    scheduleAttachmentResumeAfterSession()
    ApiClient.user?.id?.let { userId ->
        bootstrapScope.launch {
            runCatching { MessageRepository.purgePendingNotFromUser(userId) }
        }
    }
}

/**
 * Applies the last known instance id for the current server config (local DB only, no network).
 */
suspend fun applyCachedSessionInstanceIfAvailable(): Boolean {
    if (ApiClient.token.isNullOrEmpty()) return false
    val config = Settings.serverConfig
    val cached = InstanceRegistryStore.getActiveInstanceIdForConfig(config)?.trim().orEmpty()
    if (cached.isEmpty() || !isValidInstanceUuid(cached)) return false
    activateInstance(cached)
    return true
}

/**
 * Fetches `/instance_id` in the background. Safe to call multiple times; coalesces to one in-flight job.
 */
fun scheduleSessionInstanceNetworkRefresh(onLogoutRequired: () -> Unit = {}) {
    if (ApiClient.token.isNullOrEmpty()) return
    bootstrapScope.launch {
        refreshMutex.withLock {
            runCatching {
                refreshSessionInstanceFromNetwork(onLogoutRequired)
            }
        }
    }
}

private suspend fun refreshSessionInstanceFromNetwork(onLogoutRequired: () -> Unit) {
    val config = Settings.serverConfig
    val apiBase = apiBaseUrlFor(config)
    when (
        val resolve = resolveInstanceId(
            config = config,
            apiBaseUrl = apiBase,
            forceNetwork = true,
        )
    ) {
        is InstanceIdResolveResult.Cached,
        is InstanceIdResolveResult.Fetched,
        is InstanceIdResolveResult.InstanceIdChanged,
        -> {
            val id = when (resolve) {
                is InstanceIdResolveResult.Cached -> resolve.instanceId
                is InstanceIdResolveResult.Fetched -> resolve.instanceId
                is InstanceIdResolveResult.InstanceIdChanged -> resolve.newId
            }
            activateInstance(id)
        }
        InstanceIdResolveResult.Timeout,
        InstanceIdResolveResult.Unreachable,
        -> {
            if (!applyCachedSessionInstanceIfAvailable()) {
                // No cache and no network — instance will be set when connectivity returns.
            }
        }
        InstanceIdResolveResult.Unsupported -> onLogoutRequired()
    }
}

/**
 * Fast startup path: use cached instance immediately, refresh from server in the background.
 */
suspend fun bootstrapSessionOnStartup(
    hasToken: Boolean,
    onLogoutRequired: () -> Unit = {},
): SessionBootstrapResult {
    if (!hasToken) return SessionBootstrapResult.Ready
    val hadCache = applyCachedSessionInstanceIfAvailable()
    scheduleSessionInstanceNetworkRefresh(onLogoutRequired)
    return if (hadCache) SessionBootstrapResult.Ready else SessionBootstrapResult.OfflineCached
}

/**
 * Blocking bootstrap (login, server setup probe follow-up). Prefer [bootstrapSessionOnStartup] for cold start.
 */
suspend fun bootstrapSessionInstance(
    hasToken: Boolean,
    forceNetwork: Boolean = true,
): SessionBootstrapResult {
    if (!hasToken) return SessionBootstrapResult.Ready
    val config = Settings.serverConfig
    val apiBase = apiBaseUrlFor(config)
    val resolve = resolveInstanceId(
        config = config,
        apiBaseUrl = apiBase,
        forceNetwork = forceNetwork,
    )
    return when (resolve) {
        is InstanceIdResolveResult.Cached,
        is InstanceIdResolveResult.Fetched,
        is InstanceIdResolveResult.InstanceIdChanged,
        -> {
            val id = when (resolve) {
                is InstanceIdResolveResult.Cached -> resolve.instanceId
                is InstanceIdResolveResult.Fetched -> resolve.instanceId
                is InstanceIdResolveResult.InstanceIdChanged -> resolve.newId
            }
            activateInstance(id)
            SessionBootstrapResult.Ready
        }
        InstanceIdResolveResult.Timeout,
        InstanceIdResolveResult.Unreachable,
        -> {
            val cached = InstanceRegistryStore.getActiveInstanceIdForConfig(config)?.trim().orEmpty()
            if (cached.isNotEmpty() && isValidInstanceUuid(cached)) {
                activateInstance(cached)
                SessionBootstrapResult.OfflineCached
            } else {
                SessionBootstrapResult.OfflineCached
            }
        }
        InstanceIdResolveResult.Unsupported -> SessionBootstrapResult.LogoutRequired
    }
}

suspend fun logoutIfInstanceUnsupported() {
    runCatching { ApiClient.logout() }
    ApiClient.clearMemorySession()
    CacheContext.clearActive()
}
