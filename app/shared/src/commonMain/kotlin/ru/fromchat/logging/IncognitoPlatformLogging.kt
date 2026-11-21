package ru.fromchat.logging

import kotlin.concurrent.Volatile
import ru.fromchat.incognito.IncognitoMode

/**
 * Opt-in mirroring of in-app diagnostics to the host OS log (logcat / console) during incognito.
 * The flag lives only in RAM for the current incognito session.
 */
object IncognitoPlatformLogging {
    @Volatile
    var sessionLogcatEnabled: Boolean = false
        private set

    fun shouldMirrorToPlatform(): Boolean =
        !IncognitoMode.active || sessionLogcatEnabled

    fun setSessionLogcatEnabled(enabled: Boolean) {
        if (!IncognitoMode.active) {
            sessionLogcatEnabled = false
            return
        }
        sessionLogcatEnabled = enabled
    }

    fun resetSession() {
        sessionLogcatEnabled = false
    }
}