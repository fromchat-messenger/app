package ru.fromchat.notifications

import kotlin.concurrent.Volatile

/**
 * Chat titles resolved by the UI, for callers that run outside a composition.
 *
 * The desktop notification sinks need a chat title but cannot use `stringResource`, and
 * `org.jetbrains.compose.resources.getString` called outside a composition falls back to the
 * default (English) resource rather than the locale the UI is actually rendering in — so a Russian
 * UI produced notifications titled "Main chat". The chats screen already resolves the title exactly
 * the way the UI renders it (server profile title, else the localized `public_chat` string), so it
 * publishes the result here and the sinks reuse it verbatim.
 */
internal object LocalizedChatTitles {
    @Volatile
    var fallback: String = ""

    @Volatile
    var publicChatProfileTitle: String = ""

    fun remember(publicChatProfileTitle: String, fallback: String) {
        if (publicChatProfileTitle.isNotBlank()) this.publicChatProfileTitle = publicChatProfileTitle
        if (fallback.isNotBlank()) this.fallback = fallback
    }

    fun publicChatOrNull(): String? =
        publicChatProfileTitle.takeIf { it.isNotBlank() } ?: fallback.takeIf { it.isNotBlank() }
}
