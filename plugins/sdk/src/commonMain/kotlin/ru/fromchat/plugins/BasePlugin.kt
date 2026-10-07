package ru.fromchat.plugins

interface PluginHostBridge {
    fun log(pluginId: String, message: String)
    fun getSetting(pluginId: String, key: String, default: Boolean): Boolean
    fun getSettingString(pluginId: String, key: String, default: String): String
    fun setSetting(pluginId: String, key: String, value: String)
    fun registerSendMessageHook(pluginId: String, priority: Int, handler: (SendMessageHookContext) -> HookResult<SendMessageHookContext>)
    fun registerFeatureOverride(pluginId: String, featureId: String, provider: () -> Boolean?)
    fun registerMenuItem(pluginId: String, item: MenuItemData)
    fun registerUiOverlay(pluginId: String, slot: String, title: String, message: String)
    fun showBulletin(message: String)
    fun hookSharedMethod(
        pluginId: String,
        hookId: String,
        priority: Int,
        before: ((Array<Any?>) -> HookResult<Array<Any?>>)?,
        after: ((Array<Any?>, Any?) -> HookResult<Any?>)?,
    ): () -> Unit

    fun hookRawMethod(
        pluginId: String,
        className: String,
        methodName: String,
        paramTypeNames: Array<String>,
        priority: Int,
        before: ((Array<Any?>) -> HookResult<Array<Any?>>)?,
        after: ((Array<Any?>, Any?) -> HookResult<Any?>)?,
    ): () -> Unit
}

abstract class BasePlugin {
    lateinit var pluginId: String
        internal set
    private lateinit var bridge: PluginHostBridge

    fun attach(id: String, hostBridge: PluginHostBridge) {
        pluginId = id
        bridge = hostBridge
    }

    open fun onPluginLoad() {}
    open fun onPluginUnload() {}
    open fun onAppEvent(event: AppEvent) {}
    open fun onMenuItemClick(key: String) {}
    open fun createSettings(): List<PluginSetting> = emptyList()

    protected fun log(message: String) = bridge.log(pluginId, message)

    protected fun getSetting(key: String, default: Boolean = false): Boolean =
        bridge.getSetting(pluginId, key, default)

    protected fun getSettingString(key: String, default: String = ""): String =
        bridge.getSettingString(pluginId, key, default)

    protected fun setSetting(key: String, value: String, reloadSettings: Boolean = false) {
        bridge.setSetting(pluginId, key, value)
        if (reloadSettings) {
            PluginSettingsReload.request(pluginId)
        }
    }

    protected fun addOnSendMessageHook(
        priority: Int = 0,
        handler: (SendMessageHookContext) -> HookResult<SendMessageHookContext>,
    ) {
        bridge.registerSendMessageHook(pluginId, priority, handler)
    }

    protected fun registerFeatureOverride(featureId: String, provider: () -> Boolean?) {
        bridge.registerFeatureOverride(pluginId, featureId, provider)
    }

    protected fun addMenuItem(item: MenuItemData) {
        bridge.registerMenuItem(pluginId, item)
    }

    protected fun registerUiOverlay(slot: String, title: String, message: String) {
        bridge.registerUiOverlay(pluginId, slot, title, message)
    }

    protected fun showBulletin(message: String) {
        bridge.showBulletin(message)
    }

    protected fun hookShared(
        hookId: String,
        priority: Int = 0,
        before: ((Array<Any?>) -> HookResult<Array<Any?>>)? = null,
        after: ((Array<Any?>, Any?) -> HookResult<Any?>)? = null,
    ): () -> Unit = bridge.hookSharedMethod(pluginId, hookId, priority, before, after)

    protected fun hookRawMethod(
        className: String,
        methodName: String,
        paramTypeNames: Array<String> = emptyArray(),
        priority: Int = 0,
        before: ((Array<Any?>) -> HookResult<Array<Any?>>)? = null,
        after: ((Array<Any?>, Any?) -> HookResult<Any?>)? = null,
    ): () -> Unit = bridge.hookRawMethod(
        pluginId,
        className,
        methodName,
        paramTypeNames,
        priority,
        before,
        after,
    )
}

object PluginSettingsReload {
    var listener: ((String) -> Unit)? = null
    fun request(pluginId: String) {
        listener?.invoke(pluginId)
    }
}
