package ru.fromchat.plugins.host

import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.implementation.bind.annotation.AllArguments
import net.bytebuddy.implementation.bind.annotation.Origin
import net.bytebuddy.implementation.bind.annotation.RuntimeType
import net.bytebuddy.implementation.bind.annotation.SuperCall
import net.bytebuddy.matcher.ElementMatchers
import ru.fromchat.plugins.BasePlugin
import ru.fromchat.plugins.HookResult
import ru.fromchat.plugins.HookStrategy
import ru.fromchat.plugins.PluginManifest
import java.io.File
import java.lang.reflect.Method
import java.net.URLClassLoader
import java.util.concurrent.Callable
import net.bytebuddy.ByteBuddy

actual object PluginPlatform {
    actual val current: PluginPlatform? get() = this

    private val classLoaders = mutableMapOf<String, URLClassLoader>()
    private val instances = mutableMapOf<String, BasePlugin>()
    private val hookHandles = mutableMapOf<String, MutableList<SharedHookHandle>>()
    private var agentInstalled = false

    actual fun installSharedHook(registration: SharedHookRegistration) {
        val target = SharedMethodHookRegistry.targetFor(registration.hookId) ?: return
        val handle = JvmSharedHookHandle(registration, target)
        handle.install()
        hookHandles.getOrPut(registration.pluginId) { mutableListOf() } += handle
    }

    actual fun uninstallSharedHook(registration: SharedHookRegistration) {
        hookHandles[registration.pluginId]?.removeAll { it.registration == registration }
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
        runCatching { ByteBuddyAgent.install() }
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

        override fun install() {
            if (installed) return
            ensureAgent()
            val clazz = Class.forName(target.className)
            val paramTypes = target.paramTypeNames.map { Class.forName(it) }.toTypedArray()
            val method = clazz.getDeclaredMethod(target.methodName, *paramTypes)
            ByteBuddy()
                .redefine(clazz)
                .method(ElementMatchers.named(target.methodName))
                .intercept(MethodDelegation.to(JvmHookInterceptor(registration)))
                .make()
                .load(clazz.classLoader)
            installed = true
        }

        override fun uninstall() {
            installed = false
        }
    }

    class JvmHookInterceptor(private val registration: SharedHookRegistration) {
        @RuntimeType
        fun intercept(
            @AllArguments args: Array<Any?>,
            @Origin method: Method,
            @SuperCall callable: Callable<Any?>,
        ): Any? {
            registration.before?.let { before ->
                val result = before(args)
                when (result.strategy) {
                    HookStrategy.CANCEL -> return null
                    HookStrategy.MODIFY, HookStrategy.MODIFY_FINAL -> result.value?.let { return@intercept callable.call() }
                    HookStrategy.DEFAULT -> Unit
                }
            }
            val value = callable.call()
            registration.after?.let { after ->
                val result = after(args, value)
                when (result.strategy) {
                    HookStrategy.CANCEL -> return null
                    HookStrategy.MODIFY, HookStrategy.MODIFY_FINAL -> return result.value
                    HookStrategy.DEFAULT -> Unit
                }
            }
            return value
        }
    }
}
