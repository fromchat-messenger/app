package ru.fromchat.ui.chat.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.fromchat.api.local.cache.CacheContext
import ru.fromchat.ui.chat.panels.publicchat.PublicChatPanel

/**
 * Single retained [PublicChatPanel] for the app session (same idea as [ru.fromchat.ui.chat.panels.dm.DmPanelCache]).
 * Navigating away from public chat used to dispose [remember] and recreate the panel, so every open
 * paid for cache + network + full list layout again; DM reuses cached panels and only [loadMessages]
 * when the list is still empty.
 *
 * Uses a [kotlinx.coroutines.SupervisorJob] scope instead of [androidx.compose.runtime.rememberCoroutineScope] so typing
 * collectors and [ru.fromchat.ui.chat.ChatPanel] state callbacks keep working after the composable is left and re-entered.
 */
object PublicChatPanelCache {
    private const val GeneralPublicPanelKey = "general"

    private var supervisorJob = SupervisorJob()
    private var panelScope: CoroutineScope =
        CoroutineScope(supervisorJob + Dispatchers.Main.immediate)

    private var panel: PublicChatPanel? = null
    private var cachedPanelKey: String? = null
    private var cachedUserId: Int? = null
    private var cachedInstanceId: String = ""

    fun onActiveInstanceChanged(instanceId: String) {
        if (cachedInstanceId.isNotEmpty() && cachedInstanceId != instanceId) {
            clear()
        }
        cachedInstanceId = instanceId
    }

    private fun ensureScope() {
        if (!supervisorJob.isActive) {
            supervisorJob = SupervisorJob()
            panelScope = CoroutineScope(supervisorJob + Dispatchers.Main.immediate)
        }
    }

    fun getOrCreateGeneralChat(currentUserId: Int?): PublicChatPanel {
        ensureScope()
        val instanceId = CacheContext.activeInstanceId.value.trim()
        if (instanceId.isNotEmpty() && cachedInstanceId.isNotEmpty() && cachedInstanceId != instanceId) {
            clear()
        }
        if (instanceId.isNotEmpty()) cachedInstanceId = instanceId
        if (
            panel != null &&
            cachedPanelKey == GeneralPublicPanelKey &&
            cachedUserId == currentUserId &&
            (instanceId.isEmpty() || cachedInstanceId == instanceId)
        ) {
            return panel!!
        }
        panel?.destroy()
        panel = PublicChatPanel(
            panelKey = GeneralPublicPanelKey,
            currentUserId = currentUserId,
            scope = panelScope
        )
        cachedPanelKey = GeneralPublicPanelKey
        cachedUserId = currentUserId
        return panel!!
    }

    fun clear() {
        panel?.destroy()
        panel = null
        cachedPanelKey = null
        cachedUserId = null
    }
}
