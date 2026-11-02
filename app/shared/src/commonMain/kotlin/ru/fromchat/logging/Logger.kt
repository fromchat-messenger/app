package ru.fromchat.logging

import ru.fromchat.incognito.IncognitoMode

expect object Logger {
    fun d(tag: String, message: String, throwable: Throwable? = null)
    fun i(tag: String, message: String, throwable: Throwable? = null)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
    fun f(tag: String, message: String, throwable: Throwable? = null)
}

/** Platform logcat / NSLog / stdout must not receive incognito session content. */
internal fun mirrorLogsToPlatformConsole(): Boolean = !IncognitoMode.active