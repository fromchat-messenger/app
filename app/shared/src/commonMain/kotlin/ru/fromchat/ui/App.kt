package ru.fromchat.ui

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Companion.End
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Companion.Start
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.svg.SvgDecoder
import com.pr0gramm3r101.utils.LocalSystemBarsVisibility
import com.pr0gramm3r101.utils.navigateAndWipeBackStack
import com.pr0gramm3r101.utils.rememberSystemBarsController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.fromchat.AppForeground
import ru.fromchat.Logger
import ru.fromchat.api.ApiClient
import ru.fromchat.api.DeferredStartupNetwork
import ru.fromchat.api.UpdateSyncManager
import ru.fromchat.api.calls.CallStore
import ru.fromchat.api.instance.bootstrapSessionOnStartup
import ru.fromchat.api.instance.logoutIfInstanceUnsupported
import ru.fromchat.api.local.WebSocketManager
import ru.fromchat.api.local.cache.CacheContext
import ru.fromchat.api.local.cache.ensureFromChatCacheGeneration
import ru.fromchat.api.local.db.store.ProfileCache
import ru.fromchat.api.local.db.store.UserStatusStore
import ru.fromchat.api.local.send.OutgoingMessageCoordinator
import ru.fromchat.api.local.send.scheduleOutboxProcessing
import ru.fromchat.api.schema.websocket.WebSocketMessage
import ru.fromchat.api.schema.websocket.types.WebSocketUpdatesData
import ru.fromchat.config.ServerConfig
import ru.fromchat.ui.auth.AuthScreen
import ru.fromchat.ui.calls.CallOverlay
import ru.fromchat.ui.chat.panels.dm.DmChatRoute
import ru.fromchat.ui.chat.panels.dm.DmNav
import ru.fromchat.ui.chat.panels.dm.DmProfileRoute
import ru.fromchat.ui.chat.panels.publicchat.PublicChatScreen
import ru.fromchat.ui.main.MainScreen
import ru.fromchat.ui.main.chats.ChatsSearchScreen
import ru.fromchat.ui.main.settings.AboutScreen
import ru.fromchat.ui.main.settings.AppearanceScreen
import ru.fromchat.ui.main.settings.DevicesScreen
import ru.fromchat.ui.main.settings.NotificationsScreen
import ru.fromchat.ui.main.settings.SettingsRoutes
import ru.fromchat.ui.main.settings.account.AccountScreen
import ru.fromchat.ui.main.settings.account.delete.DeleteAccountScreen
import ru.fromchat.ui.main.settings.account.changepassword.ChangePasswordScreen
import ru.fromchat.ui.main.settings.server.ServerConfigScreen
import ru.fromchat.ui.profile.ProfileScreen
import ru.fromchat.utils.NetworkConnectivity

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
        "call_signaling" -> CallStore.onWebSocketMessage(message)
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
    startAtDmConversationUserId: Int? = null,
    startAtProfileUserId: Int? = null,
    startAtProfileUsername: String? = null,
    profileLookupErrorMessage: String? = null,
    onProfileLookupErrorMessageConsumed: () -> Unit = {}
) {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }

    var startDestination by remember { mutableStateOf<String?>(null) }
    var sessionLogoutRequired by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(Dispatchers.Default) {
            runCatching { ServerConfig.initialize() }
            runCatching { ensureFromChatCacheGeneration() }
            runCatching { NetworkConnectivity.ensureStarted() }
            runCatching { ApiClient.loadPersistedData() }
        }

        val hasToken = ApiClient.token?.isNotEmpty() == true
        startDestination = when {
            hasToken && startAtDmConversationUserId != null -> "chat"
            hasToken && startAtPublicChat -> "chats/publicChat"
            hasToken && !startAtPublicChat -> "chat"
            else -> "welcome"
        }

        runCatching {
            UpdateSyncManager.initializeFromStorage(ApiClient.user?.id)
        }

        DeferredStartupNetwork.scheduleAfterUiVisible()

        if (!hasToken) return@LaunchedEffect

        launch(Dispatchers.Default) {
            runCatching {
                bootstrapSessionOnStartup(
                    hasToken = true,
                    onLogoutRequired = { sessionLogoutRequired = true },
                )
            }
        }

        launch(Dispatchers.Default) {
            runCatching { ProfileCache.hydrateFromDisk() }
        }
    }

    LaunchedEffect(sessionLogoutRequired) {
        if (!sessionLogoutRequired) return@LaunchedEffect
        logoutIfInstanceUnsupported()
        startDestination = "welcome"
        sessionLogoutRequired = false
    }

    // Foreground → WebSocket reconnect; background → pause reconnect attempts (see [WebSocketManager]).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
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
                    MainScope().launch {
                        val instanceId = CacheContext.activeInstanceId.value.trim()
                        if (instanceId.isNotEmpty()) {
                            scheduleOutboxProcessing(instanceId)
                            kotlinx.coroutines.withContext(Dispatchers.Default) {
                                OutgoingMessageCoordinator.drainOutboxForInstance(instanceId)
                            }
                        }
                    }
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
            val profileLookupSnackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(profileLookupErrorMessage) {
                profileLookupErrorMessage?.let { message ->
                    Logger.d("ProfileDeepLink", "showing snackbar for deep-link lookup failure: $message")
                    profileLookupSnackbarHostState.showSnackbar(
                        message = message,
                        withDismissAction = true,
                        duration = SnackbarDuration.Short
                    )
                    onProfileLookupErrorMessageConsumed()
                }
            }

            // Handle startup/deep-link navigation targets (notification chat/profile)
            LaunchedEffect(
                startAtDmConversationUserId,
                startAtPublicChat,
                startAtProfileUserId,
                startAtProfileUsername,
                startDestination
            ) {
                Logger.d(
                    "ProfileDeepLink",
                    "startup nav check: startDestination=$startDestination, startAtProfileUserId=$startAtProfileUserId, " +
                        "startAtProfileUsername=$startAtProfileUsername, startAtDmConversationUserId=$startAtDmConversationUserId, " +
                        "startAtPublicChat=$startAtPublicChat, scrollToMessageId=$scrollToMessageId"
                )
                if (startDestination == null || startDestination == "welcome") {
                    return@LaunchedEffect
                }

                if (startAtProfileUserId != null && startAtProfileUserId > 0) {
                    Logger.d(
                        "ProfileDeepLink",
                        "navigating by deep link userId=$startAtProfileUserId"
                    )
                    navController.navigate("profile/$startAtProfileUserId?fromDeepLink=true")
                } else {
                    val trimmedUsername = startAtProfileUsername?.trim()
                    if (!trimmedUsername.isNullOrBlank()) {
                        Logger.d(
                            "ProfileDeepLink",
                            "navigating by deep link username=$trimmedUsername"
                        )
                        navController.navigate("profile/$trimmedUsername?fromDeepLink=true")
                    } else if (startAtDmConversationUserId != null && startAtDmConversationUserId > 0) {
                        Logger.d(
                            "ProfileDeepLink",
                            "navigating by notification chat route user=$startAtDmConversationUserId messageId=$scrollToMessageId"
                        )
                        navController.navigate(
                            DmNav.chatRoute(
                                otherUserId = startAtDmConversationUserId,
                                sourceMessageId = scrollToMessageId
                            )
                        ) {
                            launchSingleTop = true
                        }
                    } else if (startAtPublicChat && navController.currentDestination?.route != "chats/publicChat") {
                        Logger.d("ProfileDeepLink", "navigating to public chat route")
                        navController.navigate("chats/publicChat") {
                            launchSingleTop = true
                        }
                    }
                }
            }

            CompositionLocalProvider(
                LocalNavController provides navController,
                LocalSystemBarsVisibility provides rememberSystemBarsController()
            ) {
                if (startDestination != null) {
                    val rootNavMotion = spring<IntOffset>(dampingRatio = 0.88f, stiffness = 420f)
                    Box(Modifier.fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = startDestination!!,
                            enterTransition = {
                                slideIntoContainer(
                                    Start,
                                    animationSpec = rootNavMotion
                                )
                            },
                            exitTransition = {
                                slideOutOfContainer(
                                    Start,
                                    animationSpec = rootNavMotion
                                )
                            },
                            popEnterTransition = {
                                slideIntoContainer(
                                    End,
                                    animationSpec = rootNavMotion
                                )
                            },
                            popExitTransition = {
                                slideOutOfContainer(
                                    End,
                                    animationSpec = rootNavMotion
                                )
                            }
                        ) {
                            composable("serverConfig") {
                                ServerConfigScreen()
                            }

                            composable("welcome") {
                                WelcomeScreen(
                                    onGetStarted = {
                                        navController.navigate("auth") {
                                            popUpTo("auth") { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    onAlreadyLoggedIn = {
                                        WebSocketManager.connect(forceRestart = true)
                                        navController.navigateAndWipeBackStack("chat")
                                    },
                                )
                            }

                            composable("auth") {
                                AuthScreen(
                                    onAuthSuccess = {
                                        WebSocketManager.connect(forceRestart = true)
                                        navController.navigateAndWipeBackStack("chat")
                                    },
                                    onBackToWelcome = { navController.navigateUp() },
                                )
                            }

                            composable("chat") {
                                MainScreen(
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this,
                                    snackbarHostState = profileLookupSnackbarHostState
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

                            composable(
                                route = "profile/{userId}?fromDeepLink={fromDeepLink}&useSharedElement={useSharedElement}&sourceMessageId={sourceMessageId}",
                                arguments = listOf(
                                    navArgument("userId") { type = NavType.StringType },
                                    navArgument("fromDeepLink") {
                                        type = NavType.BoolType
                                        defaultValue = false
                                    },
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
                                val args = backStackEntry.savedStateHandle
                                val userIdParam = args.get<String>("userId")
                                val parsedUserId = userIdParam?.toIntOrNull()
                                val userId = if ((parsedUserId ?: 0) > 0) parsedUserId else null
                                val profileUsername = if (userId == null) {
                                    userIdParam?.trim()?.takeIf { it.isNotBlank() }
                                } else {
                                    null
                                }
                                val useSharedElement = when (val rawUseSharedElement = args.get<Any?>("useSharedElement")) {
                                    is Boolean -> rawUseSharedElement
                                    is String -> rawUseSharedElement == "true"
                                    else -> false
                                }
                                val sourceMessageId = when (val rawSourceMessageId = args.get<Any?>("sourceMessageId")) {
                                    is Int -> rawSourceMessageId
                                    is String -> rawSourceMessageId.toIntOrNull() ?: -1
                                    is Long -> rawSourceMessageId.toInt()
                                    else -> -1
                                }
                                val fromDeepLink = when (val rawFromDeepLink = args.get<Any?>("fromDeepLink")) {
                                    is Boolean -> rawFromDeepLink
                                    is String -> rawFromDeepLink == "true"
                                    else -> false
                                }

                                Logger.d(
                                    "ProfileRoute",
                                    "profile entry args: rawUserId=$userIdParam parsedUserId=$parsedUserId resolvedUserId=$userId " +
                                        "resolvedUsername=$profileUsername sourceMessageId=$sourceMessageId fromDeepLink=$fromDeepLink " +
                                        "useSharedElement=$useSharedElement currentRoute=${backStackEntry.destination.route}"
                                )

                                ProfileScreen(
                                    userId = userId,
                                    username = profileUsername,
                                    onBack = { navController.navigateUp() },
                                    onChat = { navController.navigate(DmNav.chatRoute(it)) },
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@composable,
                                    useSharedElementFromNavigation = useSharedElement,
                                    sharedSourceMessageId = sourceMessageId,
                                    showErrorAsToast = fromDeepLink
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
                                        else -> slideIntoContainer(Start, animationSpec = rootNavMotion)
                                    }
                                },
                                exitTransition = {
                                    when (targetState.destination.route) {
                                        DmNav.PROFILE_ROUTE -> fadeOut(animationSpec = dmChatProfileFade)
                                        else -> slideOutOfContainer(Start, animationSpec = rootNavMotion)
                                    }
                                },
                                popEnterTransition = {
                                    when (initialState.destination.route) {
                                        DmNav.PROFILE_ROUTE -> fadeIn(animationSpec = dmChatProfileFade)
                                        else -> slideIntoContainer(End, animationSpec = rootNavMotion)
                                    }
                                },
                                popExitTransition = {
                                    when (targetState.destination.route) {
                                        DmNav.PROFILE_ROUTE -> fadeOut(animationSpec = dmChatProfileFade)
                                        else -> slideOutOfContainer(End, animationSpec = rootNavMotion)
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

                            settingsSlideComposable("about", rootNavMotion) {
                                AboutScreen()
                            }

                            settingsSlideComposable(SettingsRoutes.Appearance, rootNavMotion) {
                                AppearanceScreen(onBack = { navController.navigateUp() })
                            }

                            settingsSlideComposable(SettingsRoutes.Notifications, rootNavMotion) {
                                NotificationsScreen(onBack = { navController.navigateUp() })
                            }

                            settingsSlideComposable(SettingsRoutes.Devices, rootNavMotion) {
                                DevicesScreen(onBack = { navController.navigateUp() })
                            }

                            settingsSlideComposable(SettingsRoutes.SecurityPasswordFlow, rootNavMotion) {
                                ChangePasswordScreen(
                                    onBack = { navController.navigateUp() },
                                    onDone = { navController.popBackStack() },
                                )
                            }

                            settingsSlideComposable(SettingsRoutes.AccountDeleteFlow, rootNavMotion) {
                                DeleteAccountScreen(
                                    onBack = { navController.navigateUp() },
                                    onDeleted = {
                                        navController.navigate("welcome") {
                                            popUpTo("chat") { inclusive = true }
                                        }
                                    },
                                )
                            }

                            settingsSlideComposable(SettingsRoutes.Account, rootNavMotion) {
                                AccountScreen(
                                    onBack = { navController.navigateUp() },
                                    onLogout = {
                                        navController.navigate("welcome") {
                                            popUpTo("chat") { inclusive = true }
                                        }
                                    },
                                    onChangePassword = { navController.navigate(SettingsRoutes.SecurityPasswordFlow) },
                                    onDeleteAccount = { navController.navigate(SettingsRoutes.AccountDeleteFlow) },
                                )
                            }
                        }

                        DisposableEffect(navController) {
                            ApiClient.onAuthError = {
                                Logger.d("App", "Global auth error handler triggered, navigating to login")
                                runCatching {
                                    navController.navigateAndWipeBackStack("welcome")
                                }.onFailure { e ->
                                    Logger.w("App", "Auth navigation failed: ${e.message}", e)
                                }
                            }
                            onDispose {
                                ApiClient.onAuthError = null
                            }
                        }

                        CallOverlay(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
