package ru.fromchat.incognito

import ru.fromchat.api.ApiClient
import ru.fromchat.api.instance.bootstrapSessionInstance
import ru.fromchat.api.local.WebSocketManager
import ru.fromchat.logging.Logger
import kotlin.concurrent.Volatile

private const val TAG = "IncognitoOverlaySession"

/**
 * Incognito sign-in pushed from the chats list on top of an active normal session.
 * In-memory auth and the back stack stay intact until login succeeds or the user backs out.
 */
object IncognitoOverlaySession {
    @Volatile
    var overlayActive: Boolean = false
        private set

    fun beginFromChats() {
        if (IncognitoMode.active) return
        if (overlayActive) return
        if (ApiClient.token.isNullOrBlank()) return
        overlayActive = true
        Logger.i(TAG, "incognito auth from chats (keeping current session until login)")
    }

    suspend fun restoreNormalSessionAfterOverlayCancel() {
        IncognitoMode.destroy()
        if (!overlayActive) return
        overlayActive = false
        if (ApiClient.token.isNullOrBlank()) {
            ApiClient.loadPersistedData()
            val hasToken = ApiClient.token?.isNotBlank() == true
            if (hasToken) {
                runCatching {
                    bootstrapSessionInstance(hasToken = true, forceNetwork = false)
                }
            }
        }
        if (!ApiClient.token.isNullOrBlank()) {
            WebSocketManager.connect(forceRestart = true)
        }
        Logger.i(TAG, "restored normal session after incognito auth cancel")
    }

    fun onOverlayIncognitoLoginSuccess() {
        overlayActive = false
    }
}
