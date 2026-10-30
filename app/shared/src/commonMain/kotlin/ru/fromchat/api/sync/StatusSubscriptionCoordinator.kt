package ru.fromchat.api.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.WebSocketManager
import ru.fromchat.api.local.db.store.ConnectionStateStore
import ru.fromchat.api.local.db.store.ConnectionStatus

/**
 * Reference-counted [subscribeStatus] / [unsubscribeStatus] so multiple screens
 * (chat top bar, profile, chats list) can share one backend subscription per user.
 */
object StatusSubscriptionCoordinator {
    private val mutex = Mutex()
    private val refCounts = mutableMapOf<Int, Int>()
    private var started = false

    fun ensureStarted() {
        if (started) return
        started = true
        WebSocketManager.addSessionReadyHandler { resubscribeAll() }
    }

    suspend fun acquire(userId: Int) {
        if (userId <= 0) return
        val shouldSubscribe = mutex.withLock {
            val next = (refCounts[userId] ?: 0) + 1
            refCounts[userId] = next
            next == 1
        }
        if (shouldSubscribe && ConnectionStateStore.status.value == ConnectionStatus.CONNECTED) {
            runCatching { ApiClient.sendSubscribeStatus(userId) }
        }
    }

    suspend fun release(userId: Int) {
        if (userId <= 0) return
        val shouldUnsubscribe = mutex.withLock {
            val current = refCounts[userId] ?: return
            if (current <= 1) {
                refCounts.remove(userId)
                true
            } else {
                refCounts[userId] = current - 1
                false
            }
        }
        if (shouldUnsubscribe) {
            runCatching { ApiClient.sendUnsubscribeStatus(userId) }
        }
    }

    suspend fun resubscribeAll() {
        val userIds = mutex.withLock { refCounts.keys.toList() }
        if (ConnectionStateStore.status.value != ConnectionStatus.CONNECTED) return
        userIds.forEach { userId ->
            runCatching { ApiClient.sendSubscribeStatus(userId) }
        }
    }

    fun resetOnLogout() {
        refCounts.clear()
        started = false
    }
}
