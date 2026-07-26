@file:OptIn(ExperimentalForeignApi::class)

package ru.fromchat.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_monitor_t
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_main_queue
import ru.fromchat.api.local.WebSocketManager

actual object NetworkConnectivity {
    private val _isOnline = MutableStateFlow(true)
    actual val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var started = false
    // Retained so the path monitor is not collected while the process is alive.
    private var monitor: nw_path_monitor_t = null

    actual fun ensureStarted() {
        if (started) return
        started = true
        val pathMonitor = nw_path_monitor_create() ?: return
        monitor = pathMonitor
        nw_path_monitor_set_update_handler(pathMonitor) { path ->
            val online = nw_path_get_status(path) == nw_path_status_satisfied
            val wasOnline = _isOnline.value
            _isOnline.value = online
            when {
                online && !wasOnline -> WebSocketManager.onNetworkAvailable()
                !online && wasOnline -> WebSocketManager.onNetworkLost()
            }
        }
        nw_path_monitor_set_queue(pathMonitor, dispatch_get_main_queue())
        nw_path_monitor_start(pathMonitor)
    }
}
