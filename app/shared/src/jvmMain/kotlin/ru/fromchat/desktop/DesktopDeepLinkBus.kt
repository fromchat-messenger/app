package ru.fromchat.desktop

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import ru.fromchat.Logger
import ru.fromchat.auth.yandex.YANDEX_OAUTH_REDIRECT_URI

/**
 * Receives `fromchat://` URIs from the desktop process (startup args, second-instance
 * IPC, or OS protocol launch) and fans them out to listeners (OAuth, future profile).
 */
object DesktopDeepLinkBus {
    private const val TAG = "DesktopDeepLink"

    private val _links = MutableSharedFlow<String>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val links: SharedFlow<String> = _links.asSharedFlow()

    private val _oauthRedirects = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val oauthRedirects: SharedFlow<String> = _oauthRedirects.asSharedFlow()

    fun handleUri(uri: String) {
        val trimmed = uri.trim()
        if (trimmed.isEmpty()) return
        Logger.i(TAG, "handleUri ${shortUri(trimmed)}")
        _links.tryEmit(trimmed)
        if (isYandexOAuthRedirect(trimmed)) {
            _oauthRedirects.tryEmit(trimmed)
        }
    }

    fun isFromChatUri(uri: String): Boolean =
        uri.trim().startsWith("fromchat://", ignoreCase = true)

    private fun isYandexOAuthRedirect(uri: String): Boolean =
        uri.startsWith(YANDEX_OAUTH_REDIRECT_URI, ignoreCase = true) ||
            uri.startsWith("fromchat://oauth/yandex?", ignoreCase = true)

    private fun shortUri(uri: String): String =
        if (uri.length <= 120) uri else uri.take(117) + "..."
}
