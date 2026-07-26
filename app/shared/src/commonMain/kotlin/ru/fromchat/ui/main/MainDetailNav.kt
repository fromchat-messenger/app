package ru.fromchat.ui.main

import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

/**
 * Opens a main-hub detail destination, replacing any prior detail above this graph's start
 * destination so large-screen switches do not stack previous panes.
 * Matches [ru.fromchat.ui.chat.panels.dm.navigateToDmChat] / public chat.
 *
 * No-ops when [route] is already the current main-detail destination (avoids pop+push
 * churn and AnimatedContent / NavHost transitions for the same screen).
 *
 * On desktop list–detail, call this on the tab's own detail [NavController]
 * ([LocalDesktopSettingsNavController] / [LocalDesktopChatsNavController]) so stacks stay
 * independent. [preserveConversationDetail] is ignored — separate NavHosts retain other tabs.
 */
fun NavController.navigateReplacingMainDetail(
    route: String,
    @Suppress("UNUSED_PARAMETER") preserveConversationDetail: Boolean = false,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    if (isCurrentMainDetailRoute(route)) return

    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        builder()
    }
}

/**
 * Whether the back stack top already shows [route] as the main-detail destination.
 * Handles patterned routes such as `profile/{userId}` vs filled `profile/123`.
 */
fun NavController.isCurrentMainDetailRoute(route: String): Boolean {
    val entry = currentBackStackEntry ?: return false
    val current = entry.destination.route ?: return false
    val target = route.substringBefore("?")
    if (target.startsWith("profile/")) {
        if (!current.startsWith("profile/") || current.startsWith("profile/edit")) return false
        val targetUserId = target.removePrefix("profile/").substringBefore("/")
        return entry.savedStateHandle.get<String>("userId") == targetUserId
    }
    return current == route || current.substringBefore("?") == target
}
