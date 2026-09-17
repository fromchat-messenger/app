package ru.fromchat.plugins.host

expect object PluginDevServer {
    fun onDeveloperModeChanged(enabled: Boolean)
    fun stop()
}
