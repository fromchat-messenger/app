package ru.fromchat.plugins.integration

import android.content.Context
import ru.fromchat.Logger
import ru.fromchat.plugins.AppEvent
import ru.fromchat.plugins.host.FeatureGate
import ru.fromchat.plugins.host.PluginEngine
import ru.fromchat.plugins.host.PluginHookDispatcher
import ru.fromchat.plugins.host.initAndroidPluginPlatform
import ru.fromchat.plugins.host.ui.PluginBulletinStore
import ru.fromchat.plugins.host.util.PluginLogger
import java.io.File

object AndroidPluginBootstrap {
    fun init(context: Context) {
        initAndroidPluginPlatform(context)
        PluginLogger.sink = { tag, message -> Logger.d(tag, message) }
        PluginHookDispatcher.bulletinHandler = PluginBulletinStore::show
        PluginEngine.pluginsRootProvider = {
            File(context.filesDir, "plugins").apply { mkdirs() }
        }
        PluginEngine.appVersionProvider = { ru.fromchat.AppBuildInfo.version }
        PluginEngine.init()
        PluginEngine.dispatchAppEvent(AppEvent.START)
        FeatureGate.registerDefault("calls") { ru.fromchat.config.ServerConfig.callsEnabled }
    }
}
