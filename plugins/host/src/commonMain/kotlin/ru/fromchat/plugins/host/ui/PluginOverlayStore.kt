package ru.fromchat.plugins.host.ui

import androidx.compose.runtime.mutableStateListOf

data class PluginUiOverlay(
    val pluginId: String,
    val slot: String,
    val title: String,
    val message: String,
)

object PluginOverlayStore {
    val overlays = mutableStateListOf<PluginUiOverlay>()

    fun register(pluginId: String, slot: String, title: String, message: String) {
        overlays.removeAll { it.pluginId == pluginId && it.slot == slot }
        overlays += PluginUiOverlay(pluginId, slot, title, message)
    }

    fun unregisterPlugin(pluginId: String) {
        overlays.removeAll { it.pluginId == pluginId }
    }

    fun overlaysFor(slot: String): List<PluginUiOverlay> =
        overlays.filter { it.slot == slot }
}
