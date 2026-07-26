package ru.fromchat.ui.main

import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import ru.fromchat.ui.chat.panels.publicchat.PublicChatNav

/**
 * Opens a main-hub detail destination, replacing any prior conversation/settings/profile
 * above the main `chat` root so large-screen switches do not stack previous panes.
 * Matches [ru.fromchat.ui.chat.panels.dm.navigateToDmChat] / public chat.
 *
 * When [preserveConversationDetail] is true (desktop list–detail), keeps an open chat under
 * the new settings/profile destination so tab hosts can retain the Chats detail.
 *
 * No-ops when [route] is already the current main-detail destination (avoids pop+push
 * churn and AnimatedContent / NavHost transitions for the same screen).
 */
fun NavController.navigateReplacingMainDetail(
    route: String,
    preserveConversationDetail: Boolean = false,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    if (isCurrentMainDetailRoute(route)) return

    if (preserveConversationDetail) {
        while (true) {
            val current = currentBackStackEntry?.destination?.route ?: break
            if (current == "chat" || isConversationChatRoute(current)) break
            if (!popBackStack()) break
        }
        navigate(route) {
            launchSingleTop = true
            builder()
        }
    } else {
        navigate(route) {
            popUpTo("chat") { saveState = true }
            launchSingleTop = true
            builder()
        }
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

private fun isConversationChatRoute(route: String?): Boolean =
    route == PublicChatNav.CHAT_ROUTE ||
        (route != null && route.startsWith("dm/") && "/chat" in route)
