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
    private const val GeneralPublicPanelKey = "general"

    private var supervisorJob = SupervisorJob()
    private var panelScope: CoroutineScope = CoroutineScope(supervisorJob + Dispatchers.Main.immediate)

    private var panel: PublicChatPanel? = null
    private var cachedPanelKey: String? = null
    private var cachedDisplayTitle: String? = null
    private var cachedUserId: Int? = null

    private fun ensureScope() {
        if (!supervisorJob.isActive) {
            supervisorJob = SupervisorJob()
            panelScope = CoroutineScope(supervisorJob + Dispatchers.Main.immediate)
        }
    }

    /**
     * @param displayTitle Localized title from e.g. [ru.fromchat.Res.string.public_chat].
     */
    fun getOrCreateGeneralChat(displayTitle: String, currentUserId: Int?): PublicChatPanel {
        ensureScope()
        if (
            panel != null &&
            cachedPanelKey == GeneralPublicPanelKey &&
            cachedUserId == currentUserId
        ) {
            if (cachedDisplayTitle != displayTitle) {
                cachedDisplayTitle = displayTitle
                panel!!.applyDisplayTitle(displayTitle)
            }
            return panel!!
        }
        panel?.destroy()
        panel = PublicChatPanel(
            panelKey = GeneralPublicPanelKey,
            displayTitle = displayTitle,
            currentUserId = currentUserId,
            scope = panelScope
        )
        cachedPanelKey = GeneralPublicPanelKey
        cachedDisplayTitle = displayTitle
        cachedUserId = currentUserId
        return panel!!
    }

    fun clear() {
        panel?.destroy()
        panel = null
        cachedPanelKey = null
        cachedDisplayTitle = null
        cachedUserId = null
        supervisorJob.cancel()
    }
}
