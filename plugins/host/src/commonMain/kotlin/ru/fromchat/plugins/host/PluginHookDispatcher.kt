package ru.fromchat.plugins.host

import ru.fromchat.plugins.HookResult
import ru.fromchat.plugins.HookStrategy
import ru.fromchat.plugins.MenuItemData
import ru.fromchat.plugins.SendMessageHookContext
import ru.fromchat.plugins.host.ui.PluginOverlayStore

data class RegisteredSendHook(
    val pluginId: String,
    val priority: Int,
    val handler: (SendMessageHookContext) -> HookResult<SendMessageHookContext>,
)

object PluginHookDispatcher {
    private val sendHooks = mutableListOf<RegisteredSendHook>()
    private val featureOverrides = mutableMapOf<String, MutableList<Pair<String, () -> Boolean?>>>()
    private val menuItems = mutableListOf<Pair<String, MenuItemData>>()
    var bulletinHandler: ((String) -> Unit)? = null

    fun registerSendHook(pluginId: String, priority: Int, handler: (SendMessageHookContext) -> HookResult<SendMessageHookContext>) {
        sendHooks += RegisteredSendHook(pluginId, priority, handler)
        sendHooks.sortByDescending { it.priority }
    }

    fun unregisterPlugin(pluginId: String) {
        sendHooks.removeAll { it.pluginId == pluginId }
        featureOverrides.values.forEach { list -> list.removeAll { it.first == pluginId } }
        menuItems.removeAll { it.first == pluginId }
        SharedMethodHookRegistry.unregisterPlugin(pluginId)
        PluginOverlayStore.unregisterPlugin(pluginId)
    }

    fun registerFeatureOverride(pluginId: String, featureId: String, provider: () -> Boolean?) {
        featureOverrides.getOrPut(featureId) { mutableListOf() } += pluginId to provider
    }

    fun registerMenuItem(pluginId: String, item: MenuItemData) {
        menuItems += pluginId to item
    }

    fun menuItemEntriesFor(type: ru.fromchat.plugins.MenuItemType): List<Pair<String, MenuItemData>> =
        menuItems.filter { it.second.menuType == type }.sortedByDescending { it.second.priority }

    fun menuItemsFor(type: ru.fromchat.plugins.MenuItemType): List<MenuItemData> =
        menuItemEntriesFor(type).map { it.second }

    fun dispatchMenuItemClick(clickKey: String) {
        if (clickKey.isBlank()) return
        val owner = menuItems.firstOrNull { it.second.onClickKey == clickKey } ?: return
        PluginEngine.loadedPlugin(owner.first)?.instance?.onMenuItemClick(clickKey)
    }

    fun featureOverrideValue(id: String): Boolean? {
        featureOverrides[id]?.asReversed()?.forEach { (_, provider) ->
            provider()?.let { return it }
        }
        return null
    }

    fun interceptSendMessage(context: SendMessageHookContext): SendMessageHookContext? {
        var current = context
        for (hook in sendHooks) {
            val result = hook.handler(current)
            when (result.strategy) {
                HookStrategy.DEFAULT -> Unit
                HookStrategy.CANCEL -> return null
                HookStrategy.MODIFY, HookStrategy.MODIFY_FINAL -> {
                    result.value?.let { current = it }
                    if (result.strategy == HookStrategy.MODIFY_FINAL) return current
                }
            }
        }
        return current
    }

    fun showBulletin(message: String) {
        bulletinHandler?.invoke(message)
    }
}

object FeatureGate {
    private val defaults = mutableMapOf<String, () -> Boolean>()
    var pluginResolver: ((String, Boolean) -> Boolean)? = null

    fun registerDefault(id: String, provider: () -> Boolean) {
        defaults[id] = provider
    }

    fun isEnabled(id: String, default: Boolean = true): Boolean {
        PluginHookDispatcher.featureOverrideValue(id)?.let { return it }
        return defaults[id]?.invoke() ?: default
    }
}
