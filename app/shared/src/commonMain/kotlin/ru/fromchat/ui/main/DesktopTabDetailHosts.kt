package ru.fromchat.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex

/**
 * Which main bottom-nav tab owns the list–detail detail pane on large screens.
 * Inactive tab hosts stay composed (alpha 0) so their detail state is retained.
 */
val LocalDesktopMainTab = compositionLocalOf { MAIN_PAGE_CHATS }

/**
 * Per-tab detail pane hosts for desktop list–detail.
 *
 * Each tab supplies its own detail content. Only the [selectedTab] host is visible
 * (alpha 1 + highest z-index); the others stay composed at alpha 0 underneath so opening
 * Settings does not dispose an open Chats detail, and returning to Chats shows it again
 * without clearing the chat route.
 */
@Composable
fun DesktopTabDetailHosts(
    selectedTab: Int,
    chatsDetail: @Composable () -> Unit,
    settingsDetail: @Composable () -> Unit,
    contactsDetail: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        TabDetailHost(
            active = selectedTab == MAIN_PAGE_CHATS,
            content = chatsDetail,
        )
        // Profile bottom-nav is a Settings shortcut in two-pane (pager stays on Settings).
        // MAIN_PAGE_PROFILE is retained only as a defensive fallback.
        TabDetailHost(
            active = selectedTab == MAIN_PAGE_SETTINGS || selectedTab == MAIN_PAGE_PROFILE,
            content = settingsDetail,
        )
        TabDetailHost(
            active = selectedTab == MAIN_PAGE_CONTACTS,
            content = contactsDetail,
        )
    }
}

@Composable
private fun TabDetailHost(
    active: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(if (active) 1f else 0f)
            .graphicsLayer { alpha = if (active) 1f else 0f },
    ) {
        content()
    }
}
