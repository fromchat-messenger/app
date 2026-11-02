package ru.fromchat.logging

expect object Logger {
    fun d(tag: String, message: String, throwable: Throwable? = null)
    fun i(tag: String, message: String, throwable: Throwable? = null)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
    fun f(tag: String, message: String, throwable: Throwable? = null)
}

/** Incognito uses the same platform logging path as normal mode; only on-disk layout is redirected. */
internal fun mirrorLogsToPlatformConsole(): Boolean = true