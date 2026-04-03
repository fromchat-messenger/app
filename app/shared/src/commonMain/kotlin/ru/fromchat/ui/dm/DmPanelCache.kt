package ru.fromchat.ui.dm

import kotlinx.coroutines.CoroutineScope
import ru.fromchat.api.ApiClient

/**
 * Cache of DM panels by otherUserId so that when navigating back from profile
 * we reuse the same panel and don't reload messages.
 */
object DmPanelCache {
    private val panels = mutableMapOf<Int, DmPanel>()

    fun getOrCreate(
        otherUserId: Int,
        scope: CoroutineScope
    ): DmPanel {
        return panels.getOrPut(otherUserId) {
            DmPanel(
                otherUserId = otherUserId,
                coroutineScope = scope,
                currentUserId = ApiClient.user?.id
            )
        }
    }

    fun remove(otherUserId: Int) {
        panels.remove(otherUserId)?.destroy()
    }

    fun clearAll() {
        panels.values.forEach { it.destroy() }
        panels.clear()
    }
}
