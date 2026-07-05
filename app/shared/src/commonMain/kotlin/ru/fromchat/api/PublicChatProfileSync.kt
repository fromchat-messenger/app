package ru.fromchat.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.fromchat.api.local.WebSocketManager
import ru.fromchat.api.local.cache.CacheContext
import ru.fromchat.api.local.db.store.PublicChatProfileCache
import ru.fromchat.api.schema.chats.publicchat.PublicChatProfile

/**
 * Hydrates [PublicChatProfileCache] from disk on startup and fetches from the network until the
 * profile is fully loaded. Background sync stops after that; use [refreshFromNetwork] when the user
 * opens [ru.fromchat.ui.profile.PublicChatProfileScreen].
 */
object PublicChatProfileSync {
    private const val RETRY_DELAY_MS = 1_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false
    private var syncJob: Job? = null

    fun ensureStarted() {
        if (started) return
        started = true

        WebSocketManager.addSessionReadyHandler {
            scope.launch { refreshFromNetworkIfNeeded() }
        }

        syncJob = scope.launch {
            runCatching { PublicChatProfileCache.hydrateFromDisk() }
            syncUntilLoaded()
        }
    }

    fun resetOnLogout() {
        syncJob?.cancel()
        syncJob = null
        started = false
    }

    suspend fun refreshFromNetwork(): PublicChatProfile {
        if (ApiClient.token.isNullOrEmpty()) {
            return PublicChatProfileCache.profile
                ?: error("No auth token for public chat profile refresh")
        }
        if (CacheContext.activeInstanceId.value.trim().isEmpty()) {
            return PublicChatProfileCache.profile
                ?: error("No active instance for public chat profile refresh")
        }
        val profile = ApiClient.getPublicChatProfile()
        PublicChatProfileCache.put(profile)
        return profile
    }

    private suspend fun syncUntilLoaded() {
        while (currentCoroutineContext().isActive && !PublicChatProfileCache.isFullyLoaded()) {
            if (!canAttemptNetworkSync()) {
                delay(RETRY_DELAY_MS)
                continue
            }
            refreshFromNetworkIfNeeded()
            if (PublicChatProfileCache.isFullyLoaded()) break
            delay(RETRY_DELAY_MS)
        }
    }

    private fun canAttemptNetworkSync(): Boolean {
        if (ApiClient.token.isNullOrEmpty()) return false
        if (CacheContext.activeInstanceId.value.trim().isEmpty()) return false
        if (!WebSocketManager.isConnected) return false
        return true
    }

    private suspend fun refreshFromNetworkIfNeeded() {
        if (PublicChatProfileCache.isFullyLoaded()) return
        runCatching { refreshFromNetwork() }
    }
}
