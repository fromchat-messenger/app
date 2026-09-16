package ru.fromchat.plugins.integration

import ru.fromchat.Logger
import ru.fromchat.plugins.AppEvent
import ru.fromchat.plugins.host.FeatureGate
import ru.fromchat.plugins.host.PluginEngine
import ru.fromchat.plugins.host.PluginHookDispatcher
import ru.fromchat.plugins.host.ui.PluginBulletinStore
import ru.fromchat.plugins.host.util.PluginLogger
import java.io.File

object DesktopPluginBootstrap {
    fun init(classLoader: ClassLoader = DesktopPluginBootstrap::class.java.classLoader) {
        PluginLogger.sink = { tag, message -> Logger.d(tag, message) }
        PluginHookDispatcher.bulletinHandler = PluginBulletinStore::show
        PluginEngine.pluginsRootProvider = { desktopPluginsRoot().apply { mkdirs() } }
        PluginEngine.appVersionProvider = { ru.fromchat.AppBuildInfo.version }
        installBundledPlugin(classLoader)
        PluginEngine.init()
        if (ru.fromchat.AppBuildInfo.isDebug) {
            PluginEngine.setEngineEnabled(true)
            PluginEngine.setPluginEnabled("hello_world", true)
        }
        PluginEngine.dispatchAppEvent(AppEvent.START)
        FeatureGate.registerDefault("calls") { ru.fromchat.config.ServerConfig.callsEnabled }
    }

    private fun installBundledPlugin(classLoader: ClassLoader) {
        val installed = desktopPluginsRoot().resolve("hello_world")
        if (installed.exists()) return
        val stream = classLoader.getResourceAsStream("bundled_plugins/hello_world.fcplugin") ?: return
        val temp = File.createTempFile("hello_world", ".fcplugin")
        temp.outputStream().use { out -> stream.copyTo(out) }
        runCatching { PluginEngine.installFcPluginArchive(temp) }
            .onFailure { Logger.w("Plugins", "Bundled plugin install failed: ${it.message}") }
        temp.delete()
    }

    private fun desktopPluginsRoot(): File {
        val home = System.getProperty("user.home")
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("mac") -> File(home, "Library/Application Support/FromChat/plugins")
            os.contains("win") -> File(System.getenv("APPDATA") ?: home, "FromChat/plugins")
            else -> File(home, ".local/share/FromChat/plugins")
        }
    }
}
