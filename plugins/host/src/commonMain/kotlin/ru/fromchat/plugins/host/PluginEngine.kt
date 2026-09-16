package ru.fromchat.plugins.host

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.fromchat.plugins.AppEvent
import ru.fromchat.plugins.BasePlugin
import ru.fromchat.plugins.HookResult
import ru.fromchat.plugins.MenuItemData
import ru.fromchat.plugins.PluginHostBridge
import ru.fromchat.plugins.PluginManifest
import ru.fromchat.plugins.PluginSettingsReload
import ru.fromchat.plugins.SendMessageHookContext
import java.io.File

object PluginEngine : PluginHostBridge {
    private val _engineEnabled = MutableStateFlow(false)
    val engineEnabled: StateFlow<Boolean> = _engineEnabled.asStateFlow()

    private val _developerMode = MutableStateFlow(false)
    val developerMode: StateFlow<Boolean> = _developerMode.asStateFlow()

    private val _installed = MutableStateFlow<List<PluginManifest>>(emptyList())
    val installed: StateFlow<List<PluginManifest>> = _installed.asStateFlow()

    private val enabledIds = linkedSetOf<String>()
    private val pinnedIds = linkedSetOf<String>()
    private val loaded = mutableMapOf<String, LoadedPlugin>()
    private val settings = mutableMapOf<String, MutableMap<String, String>>()
    private val sharedUnhooks = mutableMapOf<String, MutableList<() -> Unit>>()

    var pluginsRootProvider: () -> File = { error("pluginsRootProvider not set") }
    var appVersionProvider: () -> String = { "0.0.0" }
    var onSettingsReload: ((String) -> Unit)? = null

    fun init() {
        PluginSettingsReload.listener = { pluginId -> onSettingsReload?.invoke(pluginId) }
        refreshInstalledList()
        loadEnabledState()
        if (_engineEnabled.value) {
            enabledIds.forEach { loadPlugin(it) }
        }
    }

    fun setEngineEnabled(enabled: Boolean) {
        if (_engineEnabled.value == enabled) return
        _engineEnabled.value = enabled
        persistEngineState()
        if (enabled) {
            enabledIds.forEach { loadPlugin(it) }
        } else {
            loaded.keys.toList().forEach { unloadPlugin(it) }
        }
    }

    fun setDeveloperMode(enabled: Boolean) {
        _developerMode.value = enabled
        persistEngineState()
        PluginDevServer.onDeveloperModeChanged(enabled)
    }

    fun setPluginEnabled(pluginId: String, enabled: Boolean) {
        if (enabled) {
            enabledIds += pluginId
            if (_engineEnabled.value) loadPlugin(pluginId)
        } else {
            enabledIds -= pluginId
            unloadPlugin(pluginId)
        }
        persistEnabledState()
        refreshInstalledList()
    }

    fun isPluginEnabled(pluginId: String): Boolean = pluginId in enabledIds

    fun isPluginPinned(pluginId: String): Boolean = pluginId in pinnedIds

    fun setPluginPinned(pluginId: String, pinned: Boolean) {
        if (pinned) {
            pinnedIds += pluginId
        } else {
            pinnedIds -= pluginId
        }
        persistPinnedState()
        refreshInstalledList()
    }

    fun sortedInstalled(): List<PluginManifest> {
        val manifests = _installed.value
        return manifests.sortedWith(
            compareByDescending<PluginManifest> { it.id in pinnedIds }
                .thenBy { it.name.lowercase() },
        )
    }

    fun removePlugin(pluginId: String) {
        unloadPlugin(pluginId)
        enabledIds -= pluginId
        pinnedIds -= pluginId
        settings.remove(pluginId)
        val dir = pluginsRootProvider().resolve(pluginId)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
        persistEnabledState()
        persistPinnedState()
        refreshInstalledList()
    }

    fun loadedPlugin(pluginId: String): LoadedPlugin? = loaded[pluginId]

    fun getPluginSettingBoolean(pluginId: String, key: String, default: Boolean = false): Boolean =
        getSetting(pluginId, key, default)

    fun getPluginSettingString(pluginId: String, key: String, default: String = ""): String =
        getSettingString(pluginId, key, default)

    fun setPluginSetting(pluginId: String, key: String, value: String) {
        setSetting(pluginId, key, value)
    }

    fun refreshInstalledList() {
        val root = pluginsRootProvider()
        if (!root.exists()) {
            root.mkdirs()
        }
        val manifests = root.listFiles()?.filter { it.isDirectory }?.mapNotNull { dir ->
            val manifestFile = dir.resolve("manifest.json")
            if (!manifestFile.exists()) return@mapNotNull null
            runCatching { PluginManifestParser.parse(manifestFile.readText()) }.getOrNull()
        }.orEmpty().sortedBy { it.name.lowercase() }
        _installed.value = manifests
    }

    fun installFcPluginArchive(archiveFile: File) {
        val temp = pluginsRootProvider().resolve(".install-${System.currentTimeMillis()}")
        temp.mkdirs()
        projectZipExtract(archiveFile, temp)
        val manifestFile = temp.walkTopDown().firstOrNull { it.name == "manifest.json" }
            ?: error("manifest.json not found in archive")
        val manifest = PluginManifestParser.parse(manifestFile.readText())
        val target = pluginsRootProvider().resolve(manifest.id)
        if (target.exists()) target.deleteRecursively()
        manifestFile.parentFile?.copyRecursively(target, overwrite = true)
        temp.deleteRecursively()
        refreshInstalledList()
    }

    fun dispatchAppEvent(event: AppEvent) {
        loaded.values.forEach { runCatching { it.instance.onAppEvent(event) } }
    }

    override fun log(pluginId: String, message: String) {
        ru.fromchat.plugins.host.util.PluginLogger.log(pluginId, message)
    }

    override fun getSetting(pluginId: String, key: String, default: Boolean): Boolean =
        settings[pluginId]?.get(key)?.toBooleanStrictOrNull() ?: default

    override fun getSettingString(pluginId: String, key: String, default: String): String =
        settings[pluginId]?.get(key) ?: default

    override fun setSetting(pluginId: String, key: String, value: String) {
        val map = settings.getOrPut(pluginId) { loadSettingsFile(pluginId) }
        map[key] = value
        saveSettingsFile(pluginId, map)
    }

    override fun registerSendMessageHook(
        pluginId: String,
        priority: Int,
        handler: (SendMessageHookContext) -> HookResult<SendMessageHookContext>,
    ) {
        PluginHookDispatcher.registerSendHook(pluginId, priority, handler)
    }

    override fun registerFeatureOverride(pluginId: String, featureId: String, provider: () -> Boolean?) {
        PluginHookDispatcher.registerFeatureOverride(pluginId, featureId, provider)
    }

    override fun registerMenuItem(pluginId: String, item: MenuItemData) {
        PluginHookDispatcher.registerMenuItem(pluginId, item)
    }

    override fun showBulletin(message: String) {
        PluginHookDispatcher.showBulletin(message)
    }

    override fun hookSharedMethod(
        pluginId: String,
        hookId: String,
        priority: Int,
        before: ((Array<Any?>) -> HookResult<Array<Any?>>)?,
        after: ((Array<Any?>, Any?) -> HookResult<Any?>)?,
    ): () -> Unit {
        val registration = SharedHookRegistration(pluginId, hookId, priority, before, after)
        SharedMethodHookRegistry.register(registration)
        val unhook = {
            SharedMethodHookRegistry.unregisterPlugin(pluginId)
        }
        sharedUnhooks.getOrPut(pluginId) { mutableListOf() } += unhook
        return unhook
    }

    private fun loadPlugin(pluginId: String) {
        if (loaded.containsKey(pluginId)) return
        val dir = pluginsRootProvider().resolve(pluginId)
        val manifestFile = dir.resolve("manifest.json")
        if (!manifestFile.exists()) return
        val manifest = PluginManifestParser.parse(manifestFile.readText())
        val platform = PluginPlatform.current ?: return
        val loadedPlugin = platform.loadPlugin(manifest, dir.absolutePath) ?: return
        loadedPlugin.instance.attach(manifest.id, this)
        settings.putIfAbsent(manifest.id, loadSettingsFile(manifest.id))
        runCatching { loadedPlugin.instance.onPluginLoad() }
            .onFailure { log(manifest.id, "onPluginLoad failed: ${it.message}") }
        loaded[manifest.id] = loadedPlugin
    }

    private fun unloadPlugin(pluginId: String) {
        val plugin = loaded.remove(pluginId) ?: return
        runCatching { plugin.instance.onPluginUnload() }
        PluginHookDispatcher.unregisterPlugin(pluginId)
        sharedUnhooks.remove(pluginId)
        PluginPlatform.current?.unloadPlugin(pluginId)
    }

    private fun loadSettingsFile(pluginId: String): MutableMap<String, String> {
        val file = pluginsRootProvider().resolve(pluginId).resolve("settings.json")
        if (!file.exists()) return mutableMapOf()
        return runCatching {
            file.readLines()
                .mapNotNull { line ->
                    val idx = line.indexOf('=')
                    if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 1)
                }
                .toMap()
                .toMutableMap()
        }.getOrElse { mutableMapOf() }
    }

    private fun saveSettingsFile(pluginId: String, map: Map<String, String>) {
        val file = pluginsRootProvider().resolve(pluginId).resolve("settings.json")
        file.writeText(map.entries.joinToString("\n") { "${it.key}=${it.value}" })
    }

    private fun persistEngineState() {
        val file = pluginsRootProvider().resolve("engine_state.properties")
        file.parentFile?.mkdirs()
        file.writeText(
            buildString {
                appendLine("engineEnabled=${_engineEnabled.value}")
                appendLine("developerMode=${_developerMode.value}")
            },
        )
    }

    private fun loadEnabledState() {
        val file = pluginsRootProvider().resolve("engine_state.properties")
        if (file.exists()) {
            file.readLines().forEach { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    when (parts[0]) {
                        "engineEnabled" -> _engineEnabled.value = parts[1].toBooleanStrictOrNull() == true
                        "developerMode" -> _developerMode.value = parts[1].toBooleanStrictOrNull() == true
                    }
                }
            }
        }
        val enabledFile = pluginsRootProvider().resolve("enabled_plugins.txt")
        if (enabledFile.exists()) {
            enabledIds.clear()
            enabledIds += enabledFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        }
        val pinnedFile = pluginsRootProvider().resolve("pinned_plugins.txt")
        if (pinnedFile.exists()) {
            pinnedIds.clear()
            pinnedIds += pinnedFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    private fun persistEnabledState() {
        pluginsRootProvider().resolve("enabled_plugins.txt").writeText(enabledIds.joinToString("\n"))
    }

    private fun persistPinnedState() {
        pluginsRootProvider().resolve("pinned_plugins.txt").writeText(pinnedIds.joinToString("\n"))
    }

    private fun projectZipExtract(archive: File, dest: File) {
        java.util.zip.ZipFile(archive).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val out = dest.resolve(entry.name)
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                }
            }
        }
    }

    fun ensureBundledPlugins(bundledDir: File) {
        if (!bundledDir.exists()) return
        bundledDir.listFiles()?.filter { it.extension == "fcplugin" }?.forEach { archive ->
            val manifestInArchive = readManifestFromArchive(archive) ?: return@forEach
            val installedDir = pluginsRootProvider().resolve(manifestInArchive.id)
            val installedManifest = installedDir.resolve("manifest.json")
            val shouldInstall = when {
                !installedManifest.exists() -> true
                else -> {
                    val current = runCatching {
                        PluginManifestParser.parse(installedManifest.readText())
                    }.getOrNull()
                    current == null || current.version != manifestInArchive.version
                }
            }
            if (shouldInstall) {
                runCatching { installFcPluginArchive(archive) }
            }
        }
    }

    private fun readManifestFromArchive(archive: File): PluginManifest? {
        return runCatching {
            java.util.zip.ZipFile(archive).use { zip ->
                val entry = zip.getEntry("manifest.json") ?: return null
                zip.getInputStream(entry).bufferedReader().use { PluginManifestParser.parse(it.readText()) }
            }
        }.getOrNull()
    }
}
