package ru.fromchat.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.svg.SvgDecoder
import ru.fromchat.api.ApiClient
import com.pr0gramm3r101.utils.LocalSystemBarsVisibility
import com.pr0gramm3r101.utils.WindowWidthSizeClass
import com.pr0gramm3r101.utils.currentWindowAdaptiveInfo
import com.pr0gramm3r101.utils.navigateAndWipeBackStack
import com.pr0gramm3r101.utils.rememberSystemBarsController
import com.pr0gramm3r101.utils.widthSizeClass
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
import ru.fromchat.keepWebSocketAliveInBackground
import ru.fromchat.api.DeferredStartupNetwork
import ru.fromchat.api.ProfileUpdateSync
import ru.fromchat.api.PublicChatProfileSync
import ru.fromchat.api.StatusSubscriptionCoordinator
import ru.fromchat.api.UpdateSyncManager
import ru.fromchat.api.calls.CallStore
import ru.fromchat.api.instance.bootstrapSessionInstance
import ru.fromchat.api.instance.bootstrapSessionOnStartup
import ru.fromchat.api.instance.logoutIfInstanceUnsupported
import ru.fromchat.api.instance.scheduleSessionInstanceNetworkRefresh
import ru.fromchat.api.local.WebSocketManager
import ru.fromchat.api.local.cache.CacheContext
import ru.fromchat.api.local.cache.ensureFromChatCacheGeneration
import ru.fromchat.api.local.db.store.ProfileCache
import ru.fromchat.api.local.db.store.UserStatusStore
import ru.fromchat.api.local.messages.DmInboxCoordinator
import ru.fromchat.api.local.send.OutgoingMessageCoordinator
import ru.fromchat.api.schema.websocket.WebSocketMessage
import ru.fromchat.api.schema.websocket.types.WebSocketUpdatesData
import ru.fromchat.config.ServerConfig
import ru.fromchat.legal.DocumentScreen
import ru.fromchat.legal.DocumentType
import ru.fromchat.notifications.NotificationLaunchCoordinator
import ru.fromchat.ui.auth.AuthScreen
import ru.fromchat.ui.auth.captcha.SmartCaptchaNav
import ru.fromchat.ui.auth.captcha.SmartCaptchaScreen
import ru.fromchat.ui.auth.yandex.YandexOAuthNav
import ru.fromchat.ui.auth.yandex.YandexOAuthScreen
import ru.fromchat.ui.calls.CallOverlay
import ru.fromchat.ui.chat.panels.dm.DmChatRoute
import ru.fromchat.ui.chat.panels.dm.DmNav
import ru.fromchat.ui.chat.panels.dm.DmProfileRoute
import ru.fromchat.ui.chat.panels.dm.navigateToDmChat
import ru.fromchat.ui.chat.panels.publicchat.PublicChatChatRoute
import ru.fromchat.ui.chat.panels.publicchat.PublicChatNav
import ru.fromchat.ui.chat.panels.publicchat.PublicChatProfileRoute
import ru.fromchat.ui.chat.panels.publicchat.navigateToPublicChat
import ru.fromchat.ui.main.ConversationListDetailShell
import ru.fromchat.ui.main.DesktopTabDetailHosts
import ru.fromchat.ui.main.EmptyContactsPlaceholder
import ru.fromchat.ui.main.EmptyConversationPlaceholder
import ru.fromchat.ui.main.EmptySettingsPlaceholder
import ru.fromchat.ui.main.LocalDesktopMainTab
import ru.fromchat.ui.main.MAIN_PAGE_CHATS
import ru.fromchat.ui.main.MAIN_PAGE_CONTACTS
import ru.fromchat.ui.main.MAIN_PAGE_SETTINGS
import ru.fromchat.ui.main.MainScreen
import ru.fromchat.ui.main.chats.ChatsSearchScreen
import ru.fromchat.ui.main.navigateReplacingMainDetail
import ru.fromchat.ui.main.settings.LOG_FILE_OPEN_RESULT_KEY
import ru.fromchat.ui.main.settings.LogFilesScreen
import ru.fromchat.ui.main.settings.LogsScreen
import ru.fromchat.ui.main.settings.AboutScreen
import ru.fromchat.ui.main.settings.AppearanceScreen
import ru.fromchat.ui.main.settings.DevicesScreen
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
import ru.fromchat.ui.components.AppPanel
import ru.fromchat.ui.components.LocalPaneHazeState
import ru.fromchat.ui.components.ScreenSurface
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.chrisbanes.haze.rememberHazeState
import ru.fromchat.utils.NetworkConnectivity

val LocalNavController = compositionLocalOf<NavController> { error("NavController not provided") }

private fun isConversationChatRoute(route: String?): Boolean =
    route == PublicChatNav.CHAT_ROUTE ||
        (route != null && route.startsWith("dm/") && "/chat" in route)

private fun isConversationProfileRoute(route: String?): Boolean =
    route == PublicChatNav.PROFILE_ROUTE ||
        (route != null && route.startsWith("dm/") && route.endsWith("/profile"))

private fun isStandaloneProfileRoute(route: String?): Boolean =
    route != null &&
        route.startsWith("profile/") &&
        !route.startsWith(ProfileRoutes.Edit.substringBefore("?"))

/**
 * Nav destination.route is the pattern (`profile/{userId}…`), not the filled path —
 * read the userId from entry args / saved state.
 */
private fun standaloneProfileUserId(entry: NavBackStackEntry?): Int? {
    val route = entry?.destination?.route ?: return null
    if (!isStandaloneProfileRoute(route)) return null
    return entry.savedStateHandle.get<String>("userId")?.toIntOrNull()?.takeIf { it > 0 }
}

private fun isConversationShellRoute(
    route: String?,
    previousRoute: String?,
    ownUserId: Int?,
    profileUserId: Int?,
): Boolean {
    if (route == "chat" || isConversationChatRoute(route) || isConversationProfileRoute(route)) {
        return true
    }
    if (isStandaloneProfileRoute(route)) {
        if (isConversationChatRoute(previousRoute)) return true
        return ownUserId != null && profileUserId == ownUserId
    }
    if (route != null && route.startsWith("settings/")) return true
    if (route == "about") return true
    if (route == SettingsRoutes.ServerConfig) {
        return previousRoute == "chat" ||
            previousRoute == "about" ||
            (previousRoute != null && previousRoute.startsWith("settings/"))
    }
    return false
}

private fun isSettingsDetailRoute(route: String?): Boolean {
    if (route == null) return false
    if (route.startsWith("settings/") || route == "about") return true
    return route == SettingsRoutes.ServerConfig
}

private fun dmUserIdFromRoute(route: String?): Int? {
    if (route == null || !route.startsWith("dm/")) return null
    return route.substringAfter("dm/").substringBefore("/").toIntOrNull()?.takeIf { it > 0 }
}

private val rootNavTween = tween<Float>(durationMillis = 250, easing = FastOutSlowInEasing)

private fun rootNavEnterTransition(): EnterTransition =
    scaleIn(initialScale = 0.9f, animationSpec = rootNavTween) +
        fadeIn(animationSpec = rootNavTween)

private fun rootNavExitTransition(): ExitTransition =
    scaleOut(targetScale = 1.1f, animationSpec = rootNavTween) +
        fadeOut(animationSpec = rootNavTween)

private fun rootNavPopEnterTransition(): EnterTransition =
    scaleIn(initialScale = 1.1f, animationSpec = rootNavTween) +
        fadeIn(animationSpec = rootNavTween)

private fun rootNavPopExitTransition(): ExitTransition =
    scaleOut(targetScale = 0.9f, animationSpec = rootNavTween) +
        fadeOut(animationSpec = rootNavTween)

private val searchScreenFade = tween<Float>(durationMillis = 260)

private fun searchScreenEnterTransition(): EnterTransition =
    fadeIn(animationSpec = searchScreenFade)

private fun searchScreenExitTransition(): ExitTransition =
    fadeOut(animationSpec = searchScreenFade)

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
        }
    }
}

private fun handlePresenceEvent(message: WebSocketMessage) {
    when (message.type) {
        "suspended", "unsuspended", "account_deleted" -> handleAccountLifecycleEvent(message)
        "statusUpdate" -> message.data?.jsonObject?.let(::handlePresenceStatus)
        "dmTyping", "stopDmTyping" -> message.data?.jsonObject?.let { handlePresenceTyping(message.type, it) }
        "dmNew", "dmDeleted", "dmEdited" -> DmInboxCoordinator.handleMessage(message)
        "call_signaling" -> CallStore.onWebSocketMessage(message)
        "updates" -> {
            val data = message.data ?: return
            val updates = ApiClient.json.decodeFromJsonElement<WebSocketUpdatesData>(data)
            updates.updates.forEach { update ->
                handlePresenceEvent(WebSocketMessage(type = update.type, data = update.data))
            }
        }
    }
}

private fun NavGraphBuilder.settingsComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable(
        route = route,
        arguments = arguments,
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
                add(KtorNetworkFetcherFactory(httpClient = { ApiClient.http }))
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
            Logger.i("App", "FromChat started")
        }

        val hasToken = ApiClient.token?.isNotEmpty() == true

        if (hasToken) {
            runCatching {
                bootstrapSessionOnStartup(
                    hasToken = true,
                    onLogoutRequired = { sessionLogoutRequired = true },
                )
            }
            PublicChatProfileSync.ensureStarted()
            ProfileUpdateSync.ensureStarted()
            StatusSubscriptionCoordinator.ensureStarted()
        }

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
    // Desktop keeps the socket alive while the process runs (tray / hidden window).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        fun syncForeground() {
            if (keepWebSocketAliveInBackground()) {
                AppForeground.setForeground(true)
                return
            }
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
                            OutgoingMessageCoordinator.onTransportReady()
                        }
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    if (!keepWebSocketAliveInBackground()) {
                        AppForeground.setForeground(false)
                    }
                }
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
                    Logger.w("ProfileDeepLink", "showing snackbar for deep-link lookup failure: $message")
                    profileLookupSnackbarHostState.showSnackbar(
                        message = message,
                        withDismissAction = true,
                        duration = SnackbarDuration.Short
                    )
                    onProfileLookupErrorMessageConsumed()
                }
            }

            // Handle startup/deep-link navigation targets (profile links)
            LaunchedEffect(
                startAtProfileUserId,
                startAtProfileUsername,
                startDestination
            ) {
                Logger.d(
                    "ProfileDeepLink",
                    "startup nav check: startDestination=$startDestination, startAtProfileUserId=$startAtProfileUserId, " +
                        "startAtProfileUsername=$startAtProfileUsername"
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
                    }
                }
            }

            LaunchedEffect(startDestination) {
                if (startDestination == null || startDestination == "welcome") {
                    return@LaunchedEffect
                }

                NotificationLaunchCoordinator.pendingLaunches.collect { target ->
                    when {
                        target.dmConversationUserId != null && target.dmConversationUserId > 0 -> {
                            Logger.d(
                                "NotificationLaunch",
                                "navigating to dm user=${target.dmConversationUserId} " +
                                    "messageId=${target.scrollToMessageId} launchId=${target.launchId}"
                            )
                            navController.navigateToDmChat(
                                otherUserId = target.dmConversationUserId,
                                sourceMessageId = target.scrollToMessageId,
                            )
                        }

                        target.startAtPublicChat -> {
                            Logger.d(
                                "NotificationLaunch",
                                "navigating to public chat launchId=${target.launchId}"
                            )
                            navController.navigateToPublicChat()
                        }
                    }
                }
            }

            CompositionLocalProvider(
                LocalNavController provides navController,
                LocalSystemBarsVisibility provides rememberSystemBarsController()
            ) {
                if (startDestination != null) {
                    val currentEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = currentEntry?.destination?.route
                    val previousRoute =
                        navController.previousBackStackEntry?.destination?.route
                    val profileUserId = standaloneProfileUserId(currentEntry)
                    val widthSizeClass = currentWindowAdaptiveInfo().widthSizeClass
                    var pendingMainTab by remember { mutableIntStateOf(MAIN_PAGE_CHATS) }
                    val ownUserId = ApiClient.user?.id
                    val showConversationListDetail =
                        widthSizeClass != WindowWidthSizeClass.COMPACT &&
                            isConversationShellRoute(
                                currentRoute,
                                previousRoute,
                                ownUserId,
                                profileUserId,
                            )

                    ScreenSurface {
                        Box(Modifier.fillMaxSize()) {
                            // Own profile (Profile tab / settings hub) always uses the settings
                            // detail host — even when a chat remains under the back stack via
                            // preserveConversationDetail. Other users' profiles opened from a
                            // conversation stay in the chats detail (see chatsDetailKey).
                            val settingsProfileSplit =
                                showConversationListDetail &&
                                    isStandaloneProfileRoute(currentRoute) &&
                                    profileUserId != null &&
                                    profileUserId == ownUserId
                            val listPaneWidth =
                                if (widthSizeClass == WindowWidthSizeClass.EXPANDED) {
                                    448.dp
                                } else {
                                    360.dp
                                }
                            // Own profile lives in the settings detail host: keep the Settings
                            // list tab active (Profile is only a shortcut button in two-pane).
                            // forceSettingsTab only scrolls when true — clearing it does not
                            // snap back, so switching to Chats while profile stays on the stack
                            // is fine.
                            val forceSettingsListTab = settingsProfileSplit

                            var retainedChatsRoute by remember { mutableStateOf<String?>(null) }
                            LaunchedEffect(currentRoute, pendingMainTab) {
                                when {
                                    isConversationChatRoute(currentRoute) -> {
                                        retainedChatsRoute = currentRoute
                                    }
                                    currentRoute == "chat" &&
                                        pendingMainTab == MAIN_PAGE_CHATS -> {
                                        retainedChatsRoute = null
                                    }
                                }
                            }

                            @Composable
                            fun AppNavHost(modifier: Modifier = Modifier) {
                                NavHost(
                                    navController = navController,
                                    startDestination = startDestination!!,
                                    modifier = modifier,
                                    enterTransition = { rootNavEnterTransition() },
                                    exitTransition = { rootNavExitTransition() },
                                    popEnterTransition = { rootNavPopEnterTransition() },
                                    popExitTransition = { rootNavPopExitTransition() },
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
                                        MainScope().launch {
                                            runCatching {
                                                bootstrapSessionInstance(
                                                    hasToken = true,
                                                    forceNetwork = false,
                                                )
                                            }
                                            PublicChatProfileSync.ensureStarted()
                                            ProfileUpdateSync.ensureStarted()
            StatusSubscriptionCoordinator.ensureStarted()
                                            scheduleSessionInstanceNetworkRefresh()
                                        }
                                        WebSocketManager.connect(forceRestart = true)
                                        navController.navigateAndWipeBackStack("chat")
                                    },
                                    onBackToWelcome = { navController.navigateUp() },
                                )
                            }

                            composable(YandexOAuthNav.ROUTE) {
                                YandexOAuthScreen()
                            }

                            composable(SmartCaptchaNav.ROUTE) {
                                SmartCaptchaScreen()
                            }

                            composable("chat") {
                                if (showConversationListDetail) {
                                    EmptyConversationPlaceholder()
                                } else {
                                    MainScreen(
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = this,
                                        snackbarHostState = profileLookupSnackbarHostState,
                                        initialPage = pendingMainTab,
                                    )
                                }
                            }

                            composable(PublicChatNav.CHAT_ROUTE) {
                                PublicChatChatRoute(
                                    scrollToMessageId = scrollToMessageId,
                                    navController = navController,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this,
                                )
                            }

                            composable(PublicChatNav.PROFILE_ROUTE) {
                                PublicChatProfileRoute(
                                    navController = navController,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this,
                                )
                            }

                            composable(
                                route = "search/conversations",
                                enterTransition = { searchScreenEnterTransition() },
                                exitTransition = { searchScreenExitTransition() },
                                popEnterTransition = { searchScreenEnterTransition() },
                                popExitTransition = { searchScreenExitTransition() },
                            ) {
                                ChatsSearchScreen(
                                    onBack = { navController.popBackStack() },
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this,
                                    onOpenProfile = { userId: Int ->
                                        if (userId != 0) {
                                            navController.navigateReplacingMainDetail("profile/$userId")
                                        }
                                    },
                                    onOpenConversation = { userId: Int ->
                                        if (userId != 0) {
                                            navController.navigateToDmChat(userId)
                                        }
                                    }
                                )
                            }

                            composable(
                                route = "profile/{userId}?fromDeepLink={fromDeepLink}",
                                arguments = listOf(
                                    navArgument("userId") { type = NavType.StringType },
                                    navArgument("fromDeepLink") {
                                        type = NavType.BoolType
                                        defaultValue = false
                                    },
                                )
                            ) { backStackEntry ->
                                val args = backStackEntry.savedStateHandle
                                val userIdParam = args.get<String>("userId")
                                val parsedUserId = userIdParam?.toIntOrNull()
                                val userId = if ((parsedUserId ?: 0) > 0) parsedUserId else null
                                val profileUsername = if (userId == null) {
                                    userIdParam?.trim()?.takeIf { it.isNotBlank() }
                                } else null

                                val fromDeepLink = when (val rawFromDeepLink = args.get<Any?>("fromDeepLink")) {
                                    is Boolean -> rawFromDeepLink
                                    is String -> rawFromDeepLink == "true"
                                    else -> false
                                }

                                Logger.d(
                                    "ProfileRoute",
                                    "profile entry args: rawUserId=$userIdParam parsedUserId=$parsedUserId resolvedUserId=$userId " +
                                        "resolvedUsername=$profileUsername fromDeepLink=$fromDeepLink " +
                                        "currentRoute=${backStackEntry.destination.route}"
                                )

                                ProfileScreen(
                                    userId = userId,
                                    username = profileUsername,
                                    showBackButton = !settingsProfileSplit,
                                    onBack = { navController.navigateUp() },
                                    onChat = { navController.navigateToDmChat(it) },
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@composable,
                                    showErrorAsToast = fromDeepLink
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
                                    sharedTransitionScope = this@SharedTransitionLayout,
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
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this,
                                )
                            }

                            settingsComposable("about") {
                                AboutScreen()
                            }

                            settingsComposable(SettingsRoutes.Logs) {
                                LogsScreen()
                            }

                            settingsComposable(SettingsRoutes.LogFiles) {
                                LogFilesScreen(
                                    onOpenFile = { file ->
                                        navController.previousBackStackEntry
                                            ?.savedStateHandle
                                            ?.set(LOG_FILE_OPEN_RESULT_KEY, file.path)
                                        navController.navigateUp()
                                    },
                                )
                            }

                            settingsComposable(
                                route = DocumentType.ROUTE,
                                arguments = listOf(
                                    navArgument(DocumentType.ARG_DOCUMENT_TYPE) { type = NavType.StringType },
                                ),
                            ) { entry ->
                                val type = entry.savedStateHandle
                                    .get<String>(DocumentType.ARG_DOCUMENT_TYPE)
                                    ?.let(DocumentType::typeFromArg)
                                    ?: return@settingsComposable
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

                            settingsComposable(SettingsRoutes.Appearance) {
                                AppearanceScreen(onBack = { navController.navigateUp() })
                            }

                            settingsComposable(SettingsRoutes.Notifications) {
                                NotificationsScreen(onBack = { navController.navigateUp() })
                            }

                            settingsComposable(SettingsRoutes.Devices) {
                                DevicesScreen(onBack = { navController.navigateUp() })
                            }

                            settingsComposable(SettingsRoutes.SecurityPasswordFlow) {
                                ChangePasswordScreen(
                                    onBack = { navController.navigateUp() },
                                    onDone = { navController.popBackStack() },
                                )
                            }

                            settingsComposable(SettingsRoutes.AccountDeleteFlow) {
                                DeleteAccountScreen(
                                    onBack = { navController.navigateUp() },
                                    onDeleted = {
                                        navController.navigate("welcome") {
                                            popUpTo("chat") { inclusive = true }
                                        }
                                    },
                                )
                            }

                            settingsComposable(SettingsRoutes.AccountYandexFlow) {
                                ChangeYandexConfirmScreen(
                                    onBack = { navController.navigateUp() },
                                )
                            }

                            settingsComposable(SettingsRoutes.AccountYandexOAuth) {
                                ChangeYandexOAuthScreen(
                                    onBack = { navController.navigateUp() },
                                )
                            }

                            settingsComposable(SettingsRoutes.AccountYandexDone) {
                                ChangeYandexDoneScreen(
                                    onDone = {
                                        navController.popBackStack(SettingsRoutes.Account, inclusive = false)
                                    },
                                )
                            }

                            settingsComposable(SettingsRoutes.Account) {
                                AccountScreen(
                                    onBack = { navController.navigateUp() },
                                    onLogout = {
                                        navController.navigate("welcome") {
                                            popUpTo("chat") { inclusive = true }
                                        }
                                    },
                                    onChangePassword = { navController.navigate(SettingsRoutes.SecurityPasswordFlow) },
                                    onChangeYandexId = { navController.navigate(SettingsRoutes.AccountYandexFlow) },
                                    onDeleteAccount = { navController.navigate(SettingsRoutes.AccountDeleteFlow) },
                                )
                            }
                        }

                        DisposableEffect(navController) {
                            ApiClient.onAuthError = {
                                Logger.i("App", "Global auth error handler triggered, navigating to login")
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
                            }

                            if (showConversationListDetail) {
                                val detailPaneAnim = tween<Float>(
                                    durationMillis = 280,
                                    easing = FastOutSlowInEasing,
                                )
                                val chatsDetailKey = when {
                                    isConversationChatRoute(currentRoute) ||
                                        isConversationProfileRoute(currentRoute) ||
                                        (
                                            isStandaloneProfileRoute(currentRoute) &&
                                                !settingsProfileSplit &&
                                                isConversationChatRoute(previousRoute)
                                            ) -> "nav"
                                    retainedChatsRoute != null -> "retained:$retainedChatsRoute"
                                    else -> "empty"
                                }
                                val settingsDetailKey = when {
                                    settingsProfileSplit || isSettingsDetailRoute(currentRoute) -> "nav"
                                    else -> "empty"
                                }
                                // Chats / contacts stay edge-to-edge; settings host wraps its
                                // own AppPanel so shell structure stays stable across tabs.
                                val detailEdgeToEdge =
                                    pendingMainTab == MAIN_PAGE_CHATS ||
                                        pendingMainTab == MAIN_PAGE_CONTACTS
                                val listPaneHazeState = rememberHazeState()
                                CompositionLocalProvider(
                                    LocalPaneHazeState provides listPaneHazeState,
                                    LocalDesktopMainTab provides pendingMainTab,
                                ) {
                                    ConversationListDetailShell(
                                        listPaneWidth = listPaneWidth,
                                        detailInPanel = false,
                                        detailEdgeToEdge = detailEdgeToEdge,
                                        listPane = {
                                            MainScreen(
                                                sharedTransitionScope = this@SharedTransitionLayout,
                                                snackbarHostState = profileLookupSnackbarHostState,
                                                embeddedInListDetail = true,
                                                initialPage = pendingMainTab,
                                                forceSettingsTab = forceSettingsListTab,
                                                onPageChanged = { pendingMainTab = it },
                                            )
                                        },
                                        detailPane = {
                                            DesktopTabDetailHosts(
                                                selectedTab = pendingMainTab,
                                                chatsDetail = {
                                                    // SizeTransform uses Lookahead under SharedTransitionLayout;
                                                    // nesting that with always-composed tab hosts crashes with
                                                    // "Parents state is LookaheadMeasuring". Disable size morph.
                                                    AnimatedContent(
                                                        targetState = chatsDetailKey,
                                                        transitionSpec = {
                                                            val navHostSwap =
                                                                (
                                                                    initialState == "nav" &&
                                                                        targetState.startsWith("retained:")
                                                                    ) ||
                                                                    (
                                                                        initialState.startsWith("retained:") &&
                                                                            targetState == "nav"
                                                                        )
                                                            val transform = if (navHostSwap) {
                                                                EnterTransition.None togetherWith
                                                                    ExitTransition.None
                                                            } else {
                                                                (
                                                                    fadeIn(animationSpec = detailPaneAnim) +
                                                                        scaleIn(
                                                                            initialScale = 0.98f,
                                                                            animationSpec = detailPaneAnim,
                                                                        )
                                                                    ) togetherWith fadeOut(
                                                                    animationSpec = tween(
                                                                        durationMillis = 180,
                                                                        easing = FastOutSlowInEasing,
                                                                    ),
                                                                )
                                                            }
                                                            transform.using(null)
                                                        },
                                                        label = "chatsDetailPane",
                                                        modifier = Modifier.fillMaxSize(),
                                                    ) { state ->
                                                        when {
                                                            state == "nav" -> {
                                                                AppNavHost(Modifier.fillMaxSize())
                                                            }
                                                            state.startsWith("retained:") -> {
                                                                val route =
                                                                    state.removePrefix("retained:")
                                                                val dmUserId = dmUserIdFromRoute(route)
                                                                when {
                                                                    dmUserId != null -> DmChatRoute(
                                                                        otherUserId = dmUserId,
                                                                        scrollToMessageId = null,
                                                                        navController = navController,
                                                                        sharedTransitionScope = null,
                                                                        animatedVisibilityScope = null,
                                                                    )
                                                                    route == PublicChatNav.CHAT_ROUTE -> {
                                                                        PublicChatChatRoute(
                                                                            scrollToMessageId =
                                                                                scrollToMessageId,
                                                                            navController = navController,
                                                                            sharedTransitionScope = null,
                                                                            animatedVisibilityScope = null,
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                            else -> EmptyConversationPlaceholder()
                                                        }
                                                    }
                                                },
                                                settingsDetail = {
                                                    AnimatedContent(
                                                        targetState = settingsDetailKey,
                                                        transitionSpec = {
                                                            (
                                                                (
                                                                    fadeIn(animationSpec = detailPaneAnim) +
                                                                        scaleIn(
                                                                            initialScale = 0.98f,
                                                                            animationSpec = detailPaneAnim,
                                                                        )
                                                                    ) togetherWith fadeOut(
                                                                    animationSpec = tween(
                                                                        durationMillis = 180,
                                                                        easing = FastOutSlowInEasing,
                                                                    ),
                                                                )
                                                                ).using(null)
                                                        },
                                                        label = "settingsDetailPane",
                                                        modifier = Modifier.fillMaxSize(),
                                                    ) { state ->
                                                        if (state == "nav") {
                                                            AppPanel(
                                                                Modifier.fillMaxSize(),
                                                                shape = RoundedCornerShape(24.dp),
                                                            ) {
                                                                AppNavHost(Modifier.fillMaxSize())
                                                            }
                                                        } else {
                                                            EmptySettingsPlaceholder()
                                                        }
                                                    }
                                                },
                                                contactsDetail = {
                                                    EmptyContactsPlaceholder()
                                                },
                                            )
                                        },
                                    )
                                }
                            } else {
                                AppNavHost(Modifier.fillMaxSize())
                            }

                            CallOverlay(Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}
