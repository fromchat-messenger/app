package ru.fromchat.plugins.host

import android.content.Context
import dalvik.system.DexClassLoader
import ru.fromchat.plugins.BasePlugin
import ru.fromchat.plugins.PluginManifest
import top.canyie.pine.Pine
import top.canyie.pine.callback.MethodHook
import java.io.File

private lateinit var appContext: Context

fun initAndroidPluginPlatform(context: Context) {
    appContext = context.applicationContext
}

actual object PluginPlatform {
    actual val current: PluginPlatform? get() = this

    private val classLoaders = mutableMapOf<String, DexClassLoader>()
    private val pineHooks = mutableMapOf<String, MutableList<top.canyie.pine.Pine.HookRecord>>()

    actual fun installSharedHook(registration: SharedHookRegistration) {
        val target = SharedMethodHookRegistry.targetFor(registration.hookId) ?: return
        val clazz = Class.forName(target.className)
        val paramTypes = target.paramTypeNames.map { Class.forName(it) }.toTypedArray()
        val method = clazz.getDeclaredMethod(target.methodName, *paramTypes)
        val record = Pine.hook(method, object : MethodHook() {
            override fun beforeCall(callFrame: Pine.CallFrame) {
                registration.before?.invoke(callFrame.args)?.let { result ->
                    when (result.strategy) {
                        ru.fromchat.plugins.HookStrategy.CANCEL -> callFrame.setResult(null)
                        ru.fromchat.plugins.HookStrategy.MODIFY, ru.fromchat.plugins.HookStrategy.MODIFY_FINAL -> {
                            result.value?.let { callFrame.args = it }
                        }
                        ru.fromchat.plugins.HookStrategy.DEFAULT -> Unit
                    }
                }
            }

            override fun afterCall(callFrame: Pine.CallFrame) {
                registration.after?.invoke(callFrame.args, callFrame.result)?.let { result ->
                    when (result.strategy) {
                        ru.fromchat.plugins.HookStrategy.CANCEL -> callFrame.setResult(null)
                        ru.fromchat.plugins.HookStrategy.MODIFY, ru.fromchat.plugins.HookStrategy.MODIFY_FINAL -> {
                            result.value?.let { callFrame.setResult(it) }
                        }
                        ru.fromchat.plugins.HookStrategy.DEFAULT -> Unit
                    }
                }
            }
        })
        pineHooks.getOrPut(registration.pluginId) { mutableListOf() } += record
    }

    actual fun uninstallSharedHook(registration: SharedHookRegistration) {
        pineHooks.remove(registration.pluginId)?.forEach { Pine.unhook(it) }
    }

    actual fun loadPlugin(manifest: PluginManifest, pluginDir: String): LoadedPlugin? {
        val artifact = manifest.androidArtifact ?: return null
        val dex = File(pluginDir, artifact)
        if (!dex.exists()) return null
        val optimizedDir = File(appContext.codeCacheDir, "plugins_opt").apply { mkdirs() }
        val loader = DexClassLoader(
            dex.absolutePath,
            optimizedDir.absolutePath,
            null,
            appContext.classLoader,
        )
        classLoaders[manifest.id] = loader
        val clazz = loader.loadClass(manifest.entryClass)
        val instance = clazz.getDeclaredConstructor().newInstance() as BasePlugin
        return LoadedPlugin(manifest, instance, pluginDir)
    }

    actual fun unloadPlugin(pluginId: String) {
        pineHooks.remove(pluginId)?.forEach { Pine.unhook(it) }
        classLoaders.remove(pluginId)
    }
}
