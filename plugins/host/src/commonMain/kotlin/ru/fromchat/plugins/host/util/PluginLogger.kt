package ru.fromchat.plugins.host.util

object PluginLogger {
    var sink: ((tag: String, message: String) -> Unit)? = null

    fun log(pluginId: String, message: String) {
        val tag = "Plugin:$pluginId"
        sink?.invoke(tag, message) ?: println("$tag $message")
    }
}
