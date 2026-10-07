package ru.fromchat.plugins.host

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

actual object PluginDevServer {
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    actual fun onDeveloperModeChanged(enabled: Boolean) {
        if (enabled) start() else stop()
    }

    actual fun stop() {
        serverJob?.cancel()
        serverJob = null
    }

    private fun start() {
        if (serverJob != null) return
        serverJob = scope.launch {
            runCatching {
                ServerSocket(42690, 50, InetAddress.getByName("127.0.0.1")).use { server ->
                    while (true) {
                        val socket = server.accept()
                        scope.launch { handleClient(socket) }
                    }
                }
            }.onFailure {
                PluginEngine.log("devserver", "failed: ${it.message}")
            }
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val writer = PrintWriter(client.getOutputStream(), true)
            val line = reader.readLine() ?: return
            val requestId = Regex("\"#\"\\s*:\\s*\"([^\"]+)\"").find(line)?.groupValues?.getOrNull(1) ?: "0"
            val command = Regex("\"@\"\\s*:\\s*\"([^\"]+)\"").find(line)?.groupValues?.getOrNull(1)
            when (command) {
                "ping" -> writer.println("""{"#":"$requestId","pong":true}""")
                "get_plugins" -> {
                    val plugins = PluginEngine.installed.value.joinToString(",") { manifest ->
                        """{"id":"${manifest.id}","enabled":${PluginEngine.isPluginEnabled(manifest.id)}}"""
                    }
                    writer.println("""{"#":"$requestId","plugins":[$plugins]}""")
                }
                "enable_plugin" -> {
                    Regex("\"plugin_id\"\\s*:\\s*\"([^\"]+)\"").find(line)?.groupValues?.getOrNull(1)?.let {
                        PluginEngine.setPluginEnabled(it, true)
                    }
                    writer.println("""{"#":"$requestId","success":true}""")
                }
                "disable_plugin" -> {
                    Regex("\"plugin_id\"\\s*:\\s*\"([^\"]+)\"").find(line)?.groupValues?.getOrNull(1)?.let {
                        PluginEngine.setPluginEnabled(it, false)
                    }
                    writer.println("""{"#":"$requestId","success":true}""")
                }
                "reload_plugin" -> {
                    Regex("\"plugin_id\"\\s*:\\s*\"([^\"]+)\"").find(line)?.groupValues?.getOrNull(1)?.let { id ->
                        PluginEngine.setPluginEnabled(id, false)
                        PluginEngine.setPluginEnabled(id, true)
                    }
                    writer.println("""{"#":"$requestId","success":true}""")
                }
                else -> writer.println("""{"#":"$requestId","error":"unknown command"}""")
            }
        }
    }
}

private fun PluginEngine.log(tag: String, message: String) {
    ru.fromchat.plugins.host.util.PluginLogger.log(tag, message)
}
