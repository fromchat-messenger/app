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
 */
fun NavController.navigateReplacingMainDetail(
    route: String,
    preserveConversationDetail: Boolean = false,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
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

private fun isConversationChatRoute(route: String?): Boolean =
    route == PublicChatNav.CHAT_ROUTE ||
        (route != null && route.startsWith("dm/") && "/chat" in route)
