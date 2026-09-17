package ru.fromchat.plugins.host

import net.bytebuddy.ByteBuddy
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.asm.Advice
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy
import net.bytebuddy.matcher.ElementMatchers
import ru.fromchat.plugins.BasePlugin
import ru.fromchat.plugins.PluginManifest
import ru.fromchat.plugins.host.util.PluginLogger
import java.io.File
import java.lang.reflect.Method
import java.net.URLClassLoader

actual object PluginPlatform {
    actual val current: PluginPlatform? get() = this

    private val classLoaders = mutableMapOf<String, URLClassLoader>()
    private val instances = mutableMapOf<String, BasePlugin>()
    private val hookHandles = mutableMapOf<String, MutableList<SharedHookHandle>>()
    private var agentInstalled = false

    actual fun installSharedHook(registration: SharedHookRegistration) {
        val handle = JvmSharedHookHandle(registration, registration.target)
        handle.install()
        hookHandles.getOrPut(registration.pluginId) { mutableListOf() } += handle
    }

    actual fun uninstallSharedHook(registration: SharedHookRegistration) {
        hookHandles[registration.pluginId]?.removeAll { it.registration == registration }
        HookAdviceBindings.unbind(registration)
    }

    actual fun loadPlugin(manifest: PluginManifest, pluginDir: String): LoadedPlugin? {
        val artifact = manifest.desktopArtifact ?: return null
        val jar = File(pluginDir, artifact)
        if (!jar.exists()) return null
        val loader = URLClassLoader(arrayOf(jar.toURI().toURL()), BasePlugin::class.java.classLoader)
        classLoaders[manifest.id] = loader
        val clazz = loader.loadClass(manifest.entryClass)
        val instance = clazz.getDeclaredConstructor().newInstance() as BasePlugin
        instances[manifest.id] = instance
        return LoadedPlugin(manifest, instance, pluginDir)
    }

    actual fun unloadPlugin(pluginId: String) {
        hookHandles.remove(pluginId)?.forEach { it.uninstall() }
        instances.remove(pluginId)
        classLoaders.remove(pluginId)?.close()
    }

    private fun ensureAgent() {
        if (agentInstalled) return
        ByteBuddyAgent.install()
        agentInstalled = true
    }

    private interface SharedHookHandle {
        val registration: SharedHookRegistration
        fun install()
        fun uninstall()
    }

    private class JvmSharedHookHandle(
        override val registration: SharedHookRegistration,
        private val target: SharedHookTarget,
    ) : SharedHookHandle {
        private var installed = false
        private var boundMethod: Method? = null

        override fun install() {
            if (installed) return
            runCatching {
                ensureAgent()
                val clazz = Class.forName(target.className)
                val paramTypes = target.paramTypeNames.map { resolveJvmParameterType(it) }.toTypedArray()
                val method = clazz.getDeclaredMethod(target.methodName, *paramTypes)
                HookAdviceBindings.bind(method, registration)
                boundMethod = method
                ByteBuddy()
                    .redefine(clazz)
                    .visit(Advice.to(SharedHookAdvice::class.java).on(ElementMatchers.`is`(method)))
                    .make()
                    .load(clazz.classLoader, ClassReloadingStrategy.fromInstalledAgent())
                installed = true
            }.onFailure {
                boundMethod?.let { HookAdviceBindings.unbind(registration) }
                boundMethod = null
                PluginLogger.log(
                    registration.pluginId,
                    "Raw hook install failed for ${target.className}.${target.methodName}: ${it.message}",
                )
            }
        }

        override fun uninstall() {
            HookAdviceBindings.unbind(registration)
            boundMethod = null
            installed = false
        }
    }
}
