package ru.fromchat.ui.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
/**
 * Single retained [PublicChatPanel] for the app session (same idea as [ru.fromchat.ui.dm.DmPanelCache]).
 * Navigating away from public chat used to dispose [remember] and recreate the panel, so every open
 * paid for cache + network + full list layout again; DM reuses cached panels and only [loadMessages]
 * when the list is still empty.
 *
 * Uses a [SupervisorJob] scope instead of [androidx.compose.runtime.rememberCoroutineScope] so typing
 * collectors and [ChatPanel] state callbacks keep working after the composable is left and re-entered.
 */
object PublicChatPanelCache {
    private const val GENERAL_CHAT_NAME = "General Chat"

    private var supervisorJob = SupervisorJob()
    private var panelScope: CoroutineScope = CoroutineScope(supervisorJob + Dispatchers.Main.immediate)

    private var panel: PublicChatPanel? = null
    private var cachedChatName: String? = null
    private var cachedUserId: Int? = null

    private fun ensureScope() {
        if (!supervisorJob.isActive) {
            supervisorJob = SupervisorJob()
            panelScope = CoroutineScope(supervisorJob + Dispatchers.Main.immediate)
        }
    }

    fun getOrCreateGeneralChat(currentUserId: Int?): PublicChatPanel =
        getOrCreate(GENERAL_CHAT_NAME, currentUserId)

    fun getOrCreate(chatName: String, currentUserId: Int?): PublicChatPanel {
        ensureScope()
        if (
            panel != null &&
            cachedChatName == chatName &&
            cachedUserId == currentUserId
        ) {
            return panel!!
        }
        panel?.destroy()
        panel = PublicChatPanel(
            chatName = chatName,
            currentUserId = currentUserId,
            scope = panelScope
        )
        cachedChatName = chatName
        cachedUserId = currentUserId
        return panel!!
    }

    fun clear() {
        panel?.destroy()
        panel = null
        cachedChatName = null
        cachedUserId = null
        supervisorJob.cancel()
    }
}
