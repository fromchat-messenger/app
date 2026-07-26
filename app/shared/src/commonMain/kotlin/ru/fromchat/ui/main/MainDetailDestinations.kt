package ru.fromchat.ui.main

import androidx.compose.animation.SharedTransitionScope
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import ru.fromchat.Logger
import ru.fromchat.legal.DocumentScreen
import ru.fromchat.legal.DocumentType
import ru.fromchat.ui.chat.panels.dm.DmChatRoute
import ru.fromchat.ui.chat.panels.dm.DmNav
import ru.fromchat.ui.chat.panels.dm.DmProfileRoute
import ru.fromchat.ui.chat.panels.dm.navigateToDmChat
import ru.fromchat.ui.chat.panels.publicchat.PublicChatChatRoute
import ru.fromchat.ui.chat.panels.publicchat.PublicChatNav
import ru.fromchat.ui.chat.panels.publicchat.PublicChatProfileRoute
import ru.fromchat.ui.main.settings.AboutScreen
import ru.fromchat.ui.main.settings.AppearanceScreen
import ru.fromchat.ui.main.settings.DevicesScreen
import ru.fromchat.ui.main.settings.LOG_FILE_OPEN_RESULT_KEY
import ru.fromchat.ui.main.settings.LogFilesScreen
import ru.fromchat.ui.main.settings.LogsScreen
import ru.fromchat.ui.main.settings.NotificationsScreen
import ru.fromchat.ui.main.settings.SettingsRoutes
import ru.fromchat.ui.main.settings.account.AccountScreen
import ru.fromchat.ui.main.settings.account.changepassword.ChangePasswordScreen
import ru.fromchat.ui.main.settings.account.changeyandex.ChangeYandexConfirmScreen
import ru.fromchat.ui.main.settings.account.changeyandex.ChangeYandexDoneScreen
import ru.fromchat.ui.main.settings.account.changeyandex.ChangeYandexOAuthScreen
import ru.fromchat.ui.main.settings.account.delete.DeleteAccountScreen
import ru.fromchat.ui.main.settings.server.ServerConfigScreen
import ru.fromchat.ui.profile.EditProfileFocusField
import ru.fromchat.ui.profile.EditProfileScreen
import ru.fromchat.ui.profile.ProfileRoutes
import ru.fromchat.ui.profile.ProfileScreen

/**
 * Conversation detail destinations (DM / public chat + in-conversation profiles).
 * Used by the root NavHost (compact) and the desktop Chats detail NavHost.
 */
fun NavGraphBuilder.conversationDetailDestinations(
    navController: NavController,
    sharedTransitionScope: SharedTransitionScope,
    scrollToMessageId: Int?,
) {
    composable(PublicChatNav.CHAT_ROUTE) {
        PublicChatChatRoute(
            scrollToMessageId = scrollToMessageId,
            navController = navController,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = this,
        )
    }

    composable(PublicChatNav.PROFILE_ROUTE) {
        PublicChatProfileRoute(
            navController = navController,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = this,
        )
    }

    composable(
        route = DmNav.CHAT_ROUTE,
        arguments = listOf(
            navArgument("otherUserId") { type = NavType.StringType },
            navArgument("sourceMessageId") { type = NavType.IntType; defaultValue = -1 },
        ),
    ) { entry ->
        val otherUserId = entry.savedStateHandle.get<String>("otherUserId")?.toIntOrNull() ?: 0
        val sourceMessageId = entry.savedStateHandle.get<Int>("sourceMessageId") ?: -1
        if (otherUserId <= 0) return@composable
        DmChatRoute(
            otherUserId = otherUserId,
            scrollToMessageId = if (sourceMessageId > 0) sourceMessageId else null,
            navController = navController,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = this,
        )
    }

    composable(
        route = DmNav.PROFILE_ROUTE,
        arguments = listOf(navArgument("otherUserId") { type = NavType.StringType }),
    ) { entry ->
        val otherUserId = entry.savedStateHandle.get<String>("otherUserId")?.toIntOrNull() ?: 0
        if (otherUserId <= 0) return@composable
        DmProfileRoute(
            otherUserId = otherUserId,
            navController = navController,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = this,
        )
    }
}

/**
 * Standalone profile + edit-profile destinations.
 *
 * @param showBackOnProfile When false (desktop settings detail own-profile), hides the back button.
 * @param onOpenChat Opens a DM; on desktop settings host this should target the Chats detail nav.
 */
fun NavGraphBuilder.profileDetailDestinations(
    navController: NavController,
    sharedTransitionScope: SharedTransitionScope,
    showBackOnProfile: Boolean = true,
    onOpenChat: (Int) -> Unit = { navController.navigateToDmChat(it) },
) {
    composable(
        route = "profile/{userId}?fromDeepLink={fromDeepLink}",
        arguments = listOf(
            navArgument("userId") { type = NavType.StringType },
            navArgument("fromDeepLink") {
                type = NavType.BoolType
                defaultValue = false
            },
        ),
    ) { backStackEntry ->
        val args = backStackEntry.savedStateHandle
        val userIdParam = args.get<String>("userId")
        val parsedUserId = userIdParam?.toIntOrNull()
        val userId = if ((parsedUserId ?: 0) > 0) parsedUserId else null
        val profileUsername = if (userId == null) {
            userIdParam?.trim()?.takeIf { it.isNotBlank() }
        } else {
            null
        }

        val fromDeepLink = when (val rawFromDeepLink = args.get<Any?>("fromDeepLink")) {
            is Boolean -> rawFromDeepLink
            is String -> rawFromDeepLink == "true"
            else -> false
        }

        Logger.d(
            "ProfileRoute",
            "profile entry args: rawUserId=$userIdParam parsedUserId=$parsedUserId resolvedUserId=$userId " +
                "resolvedUsername=$profileUsername fromDeepLink=$fromDeepLink " +
                "currentRoute=${backStackEntry.destination.route}",
        )

        ProfileScreen(
            userId = userId,
            username = profileUsername,
            showBackButton = showBackOnProfile,
            onBack = { navController.navigateUp() },
            onChat = onOpenChat,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = this@composable,
            showErrorAsToast = fromDeepLink,
        )
    }

    composable(
        route = ProfileRoutes.Edit,
        arguments = listOf(
            navArgument(ProfileRoutes.ARG_FOCUS) {
                type = NavType.StringType
                defaultValue = ""
            },
        ),
    ) { entry ->
        val focusField = EditProfileFocusField.fromArg(
            entry.savedStateHandle.get<String>(ProfileRoutes.ARG_FOCUS),
        )
        EditProfileScreen(
            onBack = { navController.navigateUp() },
            initialFocusField = focusField,
        )
    }
}

/**
 * Settings hub detail destinations (account, appearance, about, …).
 *
 * @param rootNavController For logout / delete that wipe back to welcome on the root graph.
 */
fun NavGraphBuilder.settingsDetailDestinations(
    navController: NavController,
    rootNavController: NavController = navController,
) {
    composable("about") {
        AboutScreen()
    }

    composable(SettingsRoutes.Logs) {
        LogsScreen()
    }

    composable(SettingsRoutes.LogFiles) {
        LogFilesScreen(
            onOpenFile = { file ->
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(LOG_FILE_OPEN_RESULT_KEY, file.path)
                navController.navigateUp()
            },
        )
    }

    composable(
        route = DocumentType.ROUTE,
        arguments = listOf(
            navArgument(DocumentType.ARG_DOCUMENT_TYPE) { type = NavType.StringType },
        ),
    ) { entry ->
        val type = entry.savedStateHandle
            .get<String>(DocumentType.ARG_DOCUMENT_TYPE)
            ?.let(DocumentType::typeFromArg)
            ?: return@composable
        DocumentScreen(
            type = type,
            onBack = { navController.navigateUp() },
            onOpenLegalDocument = { linkedType ->
                navController.navigate(DocumentType.route(linkedType)) {
                    launchSingleTop = true
                }
            },
        )
    }

    composable(SettingsRoutes.Appearance) {
        AppearanceScreen(onBack = { navController.navigateUp() })
    }

    composable(SettingsRoutes.Notifications) {
        NotificationsScreen(onBack = { navController.navigateUp() })
    }

    composable(SettingsRoutes.Devices) {
        DevicesScreen(onBack = { navController.navigateUp() })
    }

    composable(SettingsRoutes.SecurityPasswordFlow) {
        ChangePasswordScreen(
            onBack = { navController.navigateUp() },
            onDone = { navController.popBackStack() },
        )
    }

    composable(SettingsRoutes.AccountDeleteFlow) {
        DeleteAccountScreen(
            onBack = { navController.navigateUp() },
            onDeleted = {
                rootNavController.navigate("welcome") {
                    popUpTo("chat") { inclusive = true }
                }
            },
        )
    }

    composable(SettingsRoutes.AccountYandexFlow) {
        ChangeYandexConfirmScreen(
            onBack = { navController.navigateUp() },
        )
    }

    composable(SettingsRoutes.AccountYandexOAuth) {
        ChangeYandexOAuthScreen(
            onBack = { navController.navigateUp() },
        )
    }

    composable(SettingsRoutes.AccountYandexDone) {
        ChangeYandexDoneScreen(
            onDone = {
                navController.popBackStack(SettingsRoutes.Account, inclusive = false)
            },
        )
    }

    composable(SettingsRoutes.Account) {
        AccountScreen(
            onBack = { navController.navigateUp() },
            onLogout = {
                rootNavController.navigate("welcome") {
                    popUpTo("chat") { inclusive = true }
                }
            },
            onChangePassword = { navController.navigate(SettingsRoutes.SecurityPasswordFlow) },
            onChangeYandexId = { navController.navigate(SettingsRoutes.AccountYandexFlow) },
            onDeleteAccount = { navController.navigate(SettingsRoutes.AccountDeleteFlow) },
        )
    }

    // Same route string as pre-auth server config on the root graph; fine on a nested graph.
    composable(SettingsRoutes.ServerConfig) {
        ServerConfigScreen()
    }
}
