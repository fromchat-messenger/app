package ru.fromchat.ui.main.desktop

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.main.EmptyConversationPlaceholder
import ru.fromchat.ui.main.EmptyContactsPlaceholder
import ru.fromchat.ui.main.EmptySettingsPlaceholder
import ru.fromchat.ui.main.nav.conversationDetailDestinations
import ru.fromchat.ui.main.nav.detailNavEnterTransition
import ru.fromchat.ui.main.nav.detailNavExitTransition
import ru.fromchat.ui.main.nav.detailNavPopEnterTransition
import ru.fromchat.ui.main.nav.detailNavPopExitTransition
import ru.fromchat.ui.main.nav.profileDetailDestinations
import ru.fromchat.ui.main.nav.settingsDetailDestinations

/**
 * Independent Chats-tab detail [NavHost] for desktop list–detail.
 */
@Composable
fun DesktopChatsDetailNavHost(
    navController: NavHostController,
    sharedTransitionScope: SharedTransitionScope,
    scrollToMessageId: Int?,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalNavController provides navController) {
        NavHost(
            navController = navController,
            startDestination = DesktopDetailNav.CHATS_ROOT,
            modifier = modifier.fillMaxSize(),
            enterTransition = { detailNavEnterTransition() },
            exitTransition = { detailNavExitTransition() },
            popEnterTransition = { detailNavPopEnterTransition() },
            popExitTransition = { detailNavPopExitTransition() },
        ) {
            composable(DesktopDetailNav.CHATS_ROOT) {
                EmptyConversationPlaceholder()
            }
            conversationDetailDestinations(
                navController = navController,
                sharedTransitionScope = sharedTransitionScope,
                scrollToMessageId = scrollToMessageId,
            )
            profileDetailDestinations(
                navController = navController,
                sharedTransitionScope = sharedTransitionScope,
            )
        }
    }
}

/**
 * Independent Settings-tab detail [NavHost] for desktop list–detail
 * (settings screens, own profile, and edit profile on the same stack).
 *
 * Settings detail is edge-to-edge on the pane background; empty root stays
 * unpainted so the list–detail shell shows through.
 */
@Composable
fun DesktopSettingsDetailNavHost(
    navController: NavHostController,
    rootNavController: NavHostController,
    sharedTransitionScope: SharedTransitionScope,
    onOpenChatFromProfile: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val detailRoute =
        navController.currentBackStackEntryAsState().value?.destination?.route
    val showPanel =
        detailRoute != null && detailRoute != DesktopDetailNav.SETTINGS_ROOT
    Box(modifier.fillMaxSize()) {
        if (showPanel) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            )
        }
        CompositionLocalProvider(LocalNavController provides navController) {
            NavHost(
                navController = navController,
                startDestination = DesktopDetailNav.SETTINGS_ROOT,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { detailNavEnterTransition() },
                exitTransition = { detailNavExitTransition() },
                popEnterTransition = { detailNavPopEnterTransition() },
                popExitTransition = { detailNavPopExitTransition() },
            ) {
                composable(DesktopDetailNav.SETTINGS_ROOT) {
                    EmptySettingsPlaceholder()
                }
                settingsDetailDestinations(
                    navController = navController,
                    rootNavController = rootNavController,
                )
                profileDetailDestinations(
                    navController = navController,
                    sharedTransitionScope = sharedTransitionScope,
                    onOpenChat = onOpenChatFromProfile,
                )
            }
        }
    }
}

/**
 * Independent Contacts-tab detail [NavHost] (placeholder root only for now).
 */
@Composable
fun DesktopContactsDetailNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalNavController provides navController) {
        NavHost(
            navController = navController,
            startDestination = DesktopDetailNav.CONTACTS_ROOT,
            modifier = modifier.fillMaxSize(),
        ) {
            composable(DesktopDetailNav.CONTACTS_ROOT) {
                EmptyContactsPlaceholder()
            }
        }
    }
}
