package ru.fromchat.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iOS: wired later to NWPathMonitor; default [true] avoids false "offline" when APIs are absent.
 */
actual object NetworkConnectivity {
    private val _isOnline = MutableStateFlow(true)
    actual val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    actual fun ensureStarted() {
        // No-op until native path monitoring is bound.
    }
}
