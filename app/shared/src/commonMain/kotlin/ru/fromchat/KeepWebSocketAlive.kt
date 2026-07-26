package ru.fromchat

/**
 * When true, the WebSocket reconnect loop does not pause while the UI is not
 * "started" (e.g. desktop window hidden to tray). Mobile keeps the existing
 * foreground-gated behavior.
 */
expect fun keepWebSocketAliveInBackground(): Boolean
