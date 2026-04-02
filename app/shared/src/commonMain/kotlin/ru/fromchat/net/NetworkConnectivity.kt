package ru.fromchat.net

import kotlinx.coroutines.flow.StateFlow

expect object NetworkConnectivity {
    val isOnline: StateFlow<Boolean>

    /** Register OS callbacks once (Android: ConnectivityManager; iOS: best-effort). */
    fun ensureStarted()
}
