package ru.fromchat.ui

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Companion.End
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Companion.Start
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.svg.SvgDecoder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import ru.fromchat.AppForeground
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.fromchat.api.ApiClient
import ru.fromchat.api.UserStatusStore
import ru.fromchat.api.ProfileCache
import ru.fromchat.api.UpdateSyncManager
import ru.fromchat.api.WebSocketManager
import ru.fromchat.api.WebSocketMessage
import ru.fromchat.api.WebSocketUpdatesData
import ru.fromchat.net.NetworkConnectivity
import ru.fromchat.core.config.Config
import ru.fromchat.ui.auth.LoginScreen
import ru.fromchat.ui.auth.RegisterScreen
import ru.fromchat.ui.chat.PublicChatScreen
import ru.fromchat.ui.debug.DebugApiScreen
import ru.fromchat.ui.debug.DebugSearchBarDocsScreen
import ru.fromchat.ui.dm.DmChatRoute
import ru.fromchat.ui.dm.DmNav
import ru.fromchat.ui.dm.DmProfileRoute
import ru.fromchat.ui.main.MainScreen
import ru.fromchat.ui.main.ChatsSearchScreen
import ru.fromchat.ui.profile.ProfileScreen
import ru.fromchat.ui.setup.ServerConfigScreen
import ru.fromchat.ui.LocalSystemBarsVisibility
import ru.fromchat.ui.rememberSystemBarsController
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import ru.fromchat.ui.main.settings.SettingsAccountScreen
import ru.fromchat.ui.main.settings.SettingsAppearanceScreen
import ru.fromchat.ui.main.settings.SettingsDevicesScreen
import ru.fromchat.ui.main.settings.SettingsNotificationsScreen
import ru.fromchat.ui.main.settings.SettingsRoutes
import ru.fromchat.ui.main.settings.SettingsSecurityHubScreen
import ru.fromchat.ui.main.settings.SettingsSecurityPasswordFlowScreen
import ru.fromchat.ui.main.settings.SettingsServerToolsScreen
import kotlinx.serialization.json.*

val LocalNavController = compositionLocalOf<NavController> { error("NavController not provided") }

private fun handlePresenceStatus(data: JsonObject?) {
    val userId = data?.get("userId")?.jsonPrimitive?.content?.toIntOrNull() ?: return
    val online = data["online"]?.jsonPrimitive?.booleanOrNull == true
    val lastSeen = data["lastSeen"]?.jsonPrimitive?.content
    UserStatusStore.update(userId, online, lastSeen)
}

private fun handlePresenceTyping(type: String, data: JsonObject?) {
    val userId = data?.get("userId")?.jsonPrimitive?.content?.toIntOrNull() ?: return
    val username = data["username"]?.jsonPrimitive?.contentOrNull ?: return
    when (type) {
        "dmTyping" -> UserStatusStore.addTyping(userId, username)
        "stopDmTyping" -> UserStatusStore.removeTyping(userId, username)
    }
}

private fun extractReason(data: JsonElement?): String? {
    return data?.jsonObject?.get("reason")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}

private fun handleAccountLifecycleEvent(message: WebSocketMessage) {
    when (message.type) {
        "suspended" -> {
            val reason = extractReason(message.data)
            ApiClient.setSuspended(reason)
        }
        "unsuspended" -> {
            ApiClient.clearSuspensionState()
        }
        "account_deleted" -> {
            MainScope().launch {
                ApiClient.logout()
            }
            WebSocketManager.disconnect()
        }
    }
}

private fun handlePresenceEvent(message: WebSocketMessage) {
    when (message.type) {
        "suspended", "unsuspended", "account_deleted" -> handleAccountLifecycleEvent(message)
        "statusUpdate" -> message.data?.jsonObject?.let(::handlePresenceStatus)
        "dmTyping", "stopDmTyping" -> message.data?.jsonObject?.let { handlePresenceTyping(message.type, it) }
        "updates" -> {
            val data = message.data ?: return
            val updates = ApiClient.json.decodeFromJsonElement<WebSocketUpdatesData>(data)
            updates.updates.forEach { update ->
                when (update.type) {
                    "suspended", "unsuspended", "account_deleted" -> handleAccountLifecycleEvent(update)
                    else -> handlePresenceEvent(update)
                }
            }
        }
    }
}

private fun NavGraphBuilder.settingsSlideComposable(
    route: String,
    animationSpec: FiniteAnimationSpec<IntOffset>,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable(
        route = route,
        enterTransition = {
            slideInHorizontally(animationSpec = animationSpec) { it }
        },
        exitTransition = {
            slideOutHorizontally(animationSpec = animationSpec) { -it }
        },
        popEnterTransition = {
            slideInHorizontally(animationSpec = animationSpec) { -it }
        },
        popExitTransition = {
            slideOutHorizontally(animationSpec = animationSpec) { it }
        },
        content = content,
    )
}

@Composable
fun App(
    scrollToMessageId: Int? = null,
    startAtPublicChat: Boolean = false,
    startAtDmConversationUserId: Int? = null
) {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }

    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            Config.initialize()
        }

        runCatching { NetworkConnectivity.ensureStarted() }

        // Load persisted token and user data
        ApiClient.loadPersistedData()

        runCatching { ProfileCache.hydrateFromDisk() }

        val hasTokenInitially = ApiClient.token?.isNotEmpty() == true
        if (hasTokenInitially) {
            runCatching {
                val ownProfile = ApiClient.getOwnProfile()
                ApiClient.syncSuspensionStateFromProfile(ownProfile)
            }
        }

        // Initialize update sync state for the current user (if any)
        runCatching {
            UpdateSyncManager.initializeFromStorage(ApiClient.user?.id)
        }

        // Now determine start destination based on loaded token
        val hasToken = ApiClient.token?.isNotEmpty() == true
        startDestination = when {
            hasToken && startAtDmConversationUserId != null -> "chat"
            hasToken && startAtPublicChat -> "chats/publicChat"
            hasToken && !startAtPublicChat -> "chat"
            else -> "login"
        }
    }

    // Foreground → WebSocket reconnect; background → pause reconnect attempts (see [WebSocketManager]).
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        fun syncForeground() {
            AppForeground.setForeground(
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            )
        }
        syncForeground()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    AppForeground.setForeground(true)
                    WebSocketManager.connect()
                }
                Lifecycle.Event.ON_STOP -> AppForeground.setForeground(false)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        val handler: (WebSocketMessage) -> Unit = { message ->
            runCatching { handlePresenceEvent(message) }
        }
        WebSocketManager.addGlobalMessageHandler(handler)
        onDispose {
            WebSocketManager.removeGlobalMessageHandler(handler)
        }
    }

    FromChatTheme {
        SharedTransitionLayout {
            val navController = rememberNavController()

            // Handle navigation to the target chat when launched from notification
            LaunchedEffect(startAtDmConversationUserId, startAtPublicChat, startDestination) {
                if (startDestination == null || startDestination == "login") {
                    return@LaunchedEffect
                }

                if (startAtDmConversationUserId != null && startAtDmConversationUserId > 0) {
                    navController.navigate(
                        DmNav.chatRoute(
                            otherUserId = startAtDmConversationUserId,
                            sourceMessageId = scrollToMessageId
                        )
                    ) {
                        launchSingleTop = true
                    }
                } else if (startAtPublicChat && navController.currentDestination?.route != "chats/publicChat") {
                    navController.navigate("chats/publicChat") {
                        launchSingleTop = true
                    }
                }
            }

            // Set up global auth error handler
            LaunchedEffect(navController) {
                ApiClient.onAuthError = {
                    ru.fromchat.core.Logger.d("App", "Global auth error handler triggered, navigating to login")
                    navController.navigate("login") {
                        popUpTo("chat") { inclusive = true }
                    }
                }
            }

            CompositionLocalProvider(
                LocalNavController provides navController,
                LocalSystemBarsVisibility provides rememberSystemBarsController()
            ) {
                if (startDestination != null) {
                    val animationSpec = tween<IntOffset>(400)

                    NavHost(
                        navController = navController,
                        startDestination = startDestination!!,
                        enterTransition = {
                            slideIntoContainer(
                                Start,
                                animationSpec = animationSpec
                            )
                        },
                        exitTransition = {
                            slideOutOfContainer(
                                Start,
                                animationSpec = animationSpec
                            )
                        },
                        popEnterTransition = {
                            slideIntoContainer(
                                End,
                                animationSpec = animationSpec
                            )
                        },
                        popExitTransition = {
                            slideOutOfContainer(
                                End,
                                animationSpec = animationSpec
                            )
                        }
                    ) {
                    composable("serverConfig") {
                        ServerConfigScreen()
                    }

                    composable("login") {
                        LoginScreen(
                            onLoginSuccess = {
                                WebSocketManager.connect(forceRestart = true)
                                navController.navigate("chat") {
                                    popUpTo("login") { inclusive = true }
                                }
                            },
                            onNavigateToRegister = { navController.navigate("register") }
                        )
                    }

                    composable("register") {
                        RegisterScreen(
                            onRegistered = {
                                navController.navigate("chat") {
                                    popUpTo("login") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable("chat") {
                        MainScreen(
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this
                        )
                    }

                    composable("chats/publicChat") {
                        PublicChatScreen(
                            scrollToMessageId = scrollToMessageId,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedContentScope = this@composable
                        )
                    }

                    val searchScreenFade = tween<Float>(260)
                    composable(
                        route = "search/conversations",
                        enterTransition = { fadeIn(animationSpec = searchScreenFade) },
                        exitTransition = { fadeOut(animationSpec = searchScreenFade) },
                        popEnterTransition = { fadeIn(animationSpec = searchScreenFade) },
                        popExitTransition = { fadeOut(animationSpec = searchScreenFade) }
                    ) {
                        ChatsSearchScreen(
                            onBack = { navController.popBackStack() },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this,
                            onOpenProfile = { userId: Int ->
                                if (userId != 0) {
                                    navController.navigate("profile/$userId")
                                }
                            },
                            onOpenConversation = { userId: Int ->
                                if (userId != 0) {
                                    navController.navigate(DmNav.chatRoute(userId))
                                }
                            }
                        )
                    }

                    composable("debug") {
                        DebugApiScreen()
                    }
                    composable("debug/searchbar-docs") {
                        DebugSearchBarDocsScreen()
                    }

                    composable(
                        route = "profile/{userId}?useSharedElement={useSharedElement}&sourceMessageId={sourceMessageId}",
                        arguments = listOf(
                            navArgument("userId") { type = NavType.StringType },
                            navArgument("useSharedElement") {
                                type = NavType.StringType
                                defaultValue = "false"
                            },
                            navArgument("sourceMessageId") {
                                type = NavType.StringType
                                defaultValue = "-1"
                            }
                        )
                    ) { backStackEntry ->
                        val handle = backStackEntry.savedStateHandle
                        val userId = handle.get<String>("userId")?.toIntOrNull()
                        val useSharedElement = handle.get<String>("useSharedElement") == "true"
                        val sourceMessageId = handle.get<String>("sourceMessageId")?.toIntOrNull() ?: -1
                        ProfileScreen(
                            userId = userId,
                            onBack = { navController.navigateUp() },
                            onChat = { navController.navigate(DmNav.chatRoute(it)) },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@composable,
                            useSharedElementFromNavigation = useSharedElement,
                            sharedSourceMessageId = sourceMessageId
                        )
                    }

                    val dmChatProfileFade = tween<Float>(durationMillis = 280)

                    composable(
                        route = DmNav.CHAT_ROUTE,
                        arguments = listOf(
                            navArgument("otherUserId") { type = NavType.StringType },
                            navArgument("sourceMessageId") { type = NavType.IntType; defaultValue = -1 },
                        ),
                        enterTransition = {
                            when (initialState.destination.route) {
                                DmNav.PROFILE_ROUTE -> fadeIn(animationSpec = dmChatProfileFade)
                                else -> slideIntoContainer(Start, animationSpec = animationSpec)
                            }
                        },
                        exitTransition = {
                            when (targetState.destination.route) {
                                DmNav.PROFILE_ROUTE -> fadeOut(animationSpec = dmChatProfileFade)
                                else -> slideOutOfContainer(Start, animationSpec = animationSpec)
                            }
                        },
                        popEnterTransition = {
                            when (initialState.destination.route) {
                                DmNav.PROFILE_ROUTE -> fadeIn(animationSpec = dmChatProfileFade)
                                else -> slideIntoContainer(End, animationSpec = animationSpec)
                            }
                        },
                        popExitTransition = {
                            when (targetState.destination.route) {
                                DmNav.PROFILE_ROUTE -> fadeOut(animationSpec = dmChatProfileFade)
                                else -> slideOutOfContainer(End, animationSpec = animationSpec)
                            }
                        },
                    ) { entry ->
                        val otherUserId = entry.savedStateHandle.get<String>("otherUserId")?.toIntOrNull() ?: 0
                        val sourceMessageId = entry.savedStateHandle.get<Int>("sourceMessageId") ?: -1
                        if (otherUserId <= 0) return@composable
                        DmChatRoute(
                            otherUserId = otherUserId,
                            scrollToMessageId = if (sourceMessageId > 0) sourceMessageId else null,
                            navController = navController,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this,
                        )
                    }

                    composable(
                        route = DmNav.PROFILE_ROUTE,
                        arguments = listOf(navArgument("otherUserId") { type = NavType.StringType }),
                        enterTransition = { fadeIn(animationSpec = dmChatProfileFade) },
                        exitTransition = { fadeOut(animationSpec = dmChatProfileFade) },
                        popEnterTransition = { fadeIn(animationSpec = dmChatProfileFade) },
                        popExitTransition = { fadeOut(animationSpec = dmChatProfileFade) },
                    ) { entry ->
                        val otherUserId = entry.savedStateHandle.get<String>("otherUserId")?.toIntOrNull() ?: 0
                        if (otherUserId <= 0) return@composable
                        DmProfileRoute(
                            otherUserId = otherUserId,
                            navController = navController,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this,
                        )
                    }

                    settingsSlideComposable("about", animationSpec) {
                        AboutScreen()
                    }

                    val navigateToLoginClearingChat = {
                        navController.navigate("login") {
                            popUpTo("chat") { inclusive = true }
                        }
                    }

                    settingsSlideComposable(SettingsRoutes.Appearance, animationSpec) {
                        SettingsAppearanceScreen(onBack = { navController.navigateUp() })
                    }
                    settingsSlideComposable(SettingsRoutes.ServerTools, animationSpec) {
                        SettingsServerToolsScreen(
                            onBack = { navController.navigateUp() },
                            outerNav = navController
                        )
                    }
                    settingsSlideComposable(SettingsRoutes.Notifications, animationSpec) {
                        SettingsNotificationsScreen(onBack = { navController.navigateUp() })
                    }
                    settingsSlideComposable(SettingsRoutes.Devices, animationSpec) {
                        SettingsDevicesScreen(onBack = { navController.navigateUp() })
                    }
                    settingsSlideComposable(SettingsRoutes.Security, animationSpec) {
                        SettingsSecurityHubScreen(
                            onBack = { navController.navigateUp() },
                            onChangePassword = { navController.navigate(SettingsRoutes.SecurityPasswordFlow) }
                        )
                    }
                    settingsSlideComposable(SettingsRoutes.SecurityPasswordFlow, animationSpec) {
                        SettingsSecurityPasswordFlowScreen(
                            onBack = { navController.navigateUp() },
                            onDonePopToHub = {
                                navController.popBackStack()
                            }
                        )
                    }
                    settingsSlideComposable(SettingsRoutes.Account, animationSpec) {
                        SettingsAccountScreen(
                            onBack = { navController.navigateUp() },
                            onLogout = navigateToLoginClearingChat,
                            onChangePassword = { navController.navigate(SettingsRoutes.SecurityPasswordFlow) }
                        )
                    }
                    }
                }
            }
        }
    }
}
