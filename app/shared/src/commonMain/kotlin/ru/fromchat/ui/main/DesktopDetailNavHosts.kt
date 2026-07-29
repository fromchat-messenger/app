package ru.fromchat.ui.main

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.components.AppPanel

private val detailNavTween = tween<Float>(durationMillis = 250, easing = FastOutSlowInEasing)

private fun detailNavEnter(): EnterTransition =
    scaleIn(initialScale = 0.9f, animationSpec = detailNavTween) +
        fadeIn(animationSpec = detailNavTween)

private fun detailNavExit(): ExitTransition =
    scaleOut(targetScale = 1.1f, animationSpec = detailNavTween) +
        fadeOut(animationSpec = detailNavTween)

private fun detailNavPopEnter(): EnterTransition =
    scaleIn(initialScale = 1.1f, animationSpec = detailNavTween) +
        fadeIn(animationSpec = detailNavTween)

private fun detailNavPopExit(): ExitTransition =
    scaleOut(targetScale = 0.9f, animationSpec = detailNavTween) +
        fadeOut(animationSpec = detailNavTween)

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
            enterTransition = { detailNavEnter() },
            exitTransition = { detailNavExit() },
            popEnterTransition = { detailNavPopEnter() },
            popExitTransition = { detailNavPopExit() },
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
 * [AppPanel] is drawn only behind non-empty destinations so the empty root stays
 * bare (no surface fill), matching the prior AnimatedContent empty vs panel split.
 * Panel is a sibling under the [NavHost] so the host is not remounted empty ↔ content.
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
            // Match Profile / ConversationListDetailShell (`background`), not the
            // default AppPanel `surfaceContainerLowest` (visible mismatch in two-pane).
            AppPanel(
                Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(24.dp),
            ) {}
        }
        CompositionLocalProvider(LocalNavController provides navController) {
            NavHost(
                navController = navController,
                startDestination = DesktopDetailNav.SETTINGS_ROOT,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { detailNavEnter() },
                exitTransition = { detailNavExit() },
                popEnterTransition = { detailNavPopEnter() },
                popExitTransition = { detailNavPopExit() },
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
