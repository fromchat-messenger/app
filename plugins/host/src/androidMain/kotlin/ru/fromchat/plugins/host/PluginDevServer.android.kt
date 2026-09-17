package ru.fromchat.plugins.host

actual object PluginDevServer {
    actual fun onDeveloperModeChanged(enabled: Boolean) {
        // Android DevServer wiring can mirror JVM; disabled in initial pass.
    }

    actual fun stop() = Unit
}
