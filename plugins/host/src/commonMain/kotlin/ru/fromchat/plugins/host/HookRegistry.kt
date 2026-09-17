package ru.fromchat.plugins.host

import ru.fromchat.plugins.HookResult

data class SharedHookTarget(
    val className: String,
    val methodName: String,
    val paramTypeNames: Array<String>,
)

object HookRegistry {
    const val LOGGER_DEBUG = "logger.debug"

    val targets = mapOf(
        LOGGER_DEBUG to SharedHookTarget(
            className = "ru.fromchat.Logger",
            methodName = "d",
            paramTypeNames = arrayOf("java.lang.String", "java.lang.String", "java.lang.Throwable"),
        ),
    )
}

data class SharedHookRegistration(
    val pluginId: String,
    val hookId: String,
    val target: SharedHookTarget,
    val priority: Int,
    val before: ((Array<Any?>) -> HookResult<Array<Any?>>)?,
    val after: ((Array<Any?>, Any?) -> HookResult<Any?>)?,
)

object SharedMethodHookRegistry {
    private val registrations = mutableListOf<SharedHookRegistration>()

    fun register(registration: SharedHookRegistration) {
        registrations += registration
        registrations.sortByDescending { it.priority }
        PluginPlatform.current?.installSharedHook(registration)
    }

    fun unregisterPlugin(pluginId: String) {
        val removed = registrations.filter { it.pluginId == pluginId }
        registrations.removeAll { it.pluginId == pluginId }
        removed.forEach { PluginPlatform.current?.uninstallSharedHook(it) }
    }

    fun targetFor(hookId: String): SharedHookTarget? = HookRegistry.targets[hookId]
}

expect object PluginPlatform {
    val current: PluginPlatform?
    fun installSharedHook(registration: SharedHookRegistration)
    fun uninstallSharedHook(registration: SharedHookRegistration)
    fun loadPlugin(manifest: ru.fromchat.plugins.PluginManifest, pluginDir: String): LoadedPlugin?
    fun unloadPlugin(pluginId: String)
}

data class LoadedPlugin(
    val manifest: ru.fromchat.plugins.PluginManifest,
    val instance: ru.fromchat.plugins.BasePlugin,
    val pluginDir: String,
)
