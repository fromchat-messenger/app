package ru.fromchat.api

import io.ktor.client.plugins.logging.Logger as KtorHttpLogger
import ru.fromchat.logging.IncognitoPlatformLogging

/** Ktor HTTP traffic logger; quiet during incognito unless session logcat is enabled. */
internal object FromChatKtorHttpLogger : KtorHttpLogger {
    override fun log(message: String) {
        if (IncognitoPlatformLogging.shouldMirrorToPlatform()) {
            println("HttpClient: $message")
        }
    }
}