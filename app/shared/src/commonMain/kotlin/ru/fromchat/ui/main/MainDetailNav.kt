package ru.fromchat.ui.main

import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

/**
 * Opens a main-hub detail destination, replacing any prior conversation/settings/profile
 * above the main `chat` root so large-screen switches do not stack previous panes.
 * Matches [ru.fromchat.ui.chat.panels.dm.navigateToDmChat] / public chat.
 */
fun NavController.navigateReplacingMainDetail(
    route: String,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    navigate(route) {
        popUpTo("chat") { saveState = true }
        launchSingleTop = true
        builder()
    }
}
