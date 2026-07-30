package ru.fromchat.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
import ru.fromchat.desktop.DesktopMenuCommand
import ru.fromchat.desktop.DesktopMenuCommands
import ru.fromchat.notifications.NotificationLaunchCoordinator
import ru.fromchat.ui.auth.AuthScreen
import ru.fromchat.ui.auth.captcha.SmartCaptchaNav
import ru.fromchat.ui.auth.captcha.SmartCaptchaScreen
import ru.fromchat.ui.auth.yandex.YandexOAuthNav
import ru.fromchat.ui.auth.yandex.YandexOAuthScreen
import ru.fromchat.ui.calls.CallOverlay
import ru.fromchat.ui.chat.panels.dm.navigateToDmChat
import ru.fromchat.ui.chat.panels.publicchat.PublicChatNav
import ru.fromchat.ui.chat.panels.publicchat.navigateToPublicChat
import ru.fromchat.ui.main.ConversationListDetailShell
import ru.fromchat.ui.main.DesktopChatsDetailNavHost
import ru.fromchat.ui.main.DesktopContactsDetailNavHost
import ru.fromchat.ui.main.DesktopSettingsDetailNavHost
import ru.fromchat.ui.main.DesktopTabDetailHosts
import ru.fromchat.ui.main.LocalDesktopChatsNavController
import ru.fromchat.ui.main.LocalDesktopMainTab
import ru.fromchat.ui.main.LocalDesktopSettingsNavController
import ru.fromchat.ui.main.MAIN_PAGE_CHATS
import ru.fromchat.ui.main.MAIN_PAGE_CONTACTS
import ru.fromchat.ui.main.MAIN_PAGE_SETTINGS
import ru.fromchat.ui.main.MainScreen
import ru.fromchat.ui.main.chats.ChatsSearchScreen
import ru.fromchat.ui.main.conversationDetailDestinations
import ru.fromchat.ui.main.navigateReplacingMainDetail
import ru.fromchat.ui.main.profileDetailDestinations
import ru.fromchat.ui.main.settings.SettingsRoutes
import ru.fromchat.ui.main.settingsDetailDestinations
import ru.fromchat.ui.profile.ProfileRoutes
import ru.fromchat.ui.components.LocalPaneHazeState
import ru.fromchat.ui.components.ScreenSurface
import dev.chrisbanes.haze.rememberHazeState
import ru.fromchat.utils.NetworkConnectivity

val LocalNavController = compositionLocalOf<NavController> { error("NavController not provided") }

private fun isStandaloneProfileRoute(route: String?): Boolean =
    route != null &&
        route.startsWith("profile/") &&
        !route.startsWith(ProfileRoutes.Edit.substringBefore("?"))

private fun isEditProfileRoute(route: String?): Boolean =
    route != null && route.startsWith("profile/edit")

/**
 * Nav destination.route is the pattern (`profile/{userId}…`), not the filled path —
 * read the userId from entry args / saved state.
 */
private fun standaloneProfileUserId(entry: NavBackStackEntry?): Int? {
    val route = entry?.destination?.route ?: return null
    if (!isStandaloneProfileRoute(route)) return null
    return entry.savedStateHandle.get<String>("userId")?.toIntOrNull()?.takeIf { it > 0 }
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

@Composable
fun App(
    scrollToMessageId: Int? = null,
    startAtPublicChat: Boolean = false,
    startAtDmConversationUserId: Int? = null,
    startAtProfileUserId: Int? = null,
    startAtProfileUsername: String? = null,
    profileLookupErrorMessage: String? = null,
    onProfileLookupErrorMessageConsumed: () -> Unit = {},
    /** Desktop: fired once [startDestination] is known so the window can show after bootstrap. */
    onContentReady: () -> Unit = {},
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
            hasToken -> "chat"
            else -> "welcome"
        }
        onContentReady()

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
            val chatsDetailNavController = rememberNavController()
            val settingsDetailNavController = rememberNavController()
            val contactsDetailNavController = rememberNavController()
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

            val widthSizeClass = currentWindowAdaptiveInfo().widthSizeClass
            val isDesktopListDetail = widthSizeClass != WindowWidthSizeClass.COMPACT
            var pendingMainTab by remember { mutableIntStateOf(MAIN_PAGE_CHATS) }

            // Handle startup/deep-link navigation targets (profile links)
            LaunchedEffect(
                startAtProfileUserId,
                startAtProfileUsername,
                startDestination,
                isDesktopListDetail,
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
                    val route = "profile/$startAtProfileUserId?fromDeepLink=true"
                    val ownId = ApiClient.user?.id
                    if (isDesktopListDetail) {
                        if (ownId != null && startAtProfileUserId == ownId) {
                            pendingMainTab = MAIN_PAGE_SETTINGS
                            settingsDetailNavController.navigateReplacingMainDetail(route)
                        } else {
                            pendingMainTab = MAIN_PAGE_CHATS
                            chatsDetailNavController.navigateReplacingMainDetail(route)
                        }
                    } else {
                        navController.navigate(route)
                    }
                } else {
                    val trimmedUsername = startAtProfileUsername?.trim()
                    if (!trimmedUsername.isNullOrBlank()) {
                        Logger.d(
                            "ProfileDeepLink",
                            "navigating by deep link username=$trimmedUsername"
                        )
                        val route = "profile/$trimmedUsername?fromDeepLink=true"
                        if (isDesktopListDetail) {
                            pendingMainTab = MAIN_PAGE_CHATS
                            chatsDetailNavController.navigateReplacingMainDetail(route)
                        } else {
                            navController.navigate(route)
                        }
                    }
                }
            }

            LaunchedEffect(startDestination) {
                if (startDestination == null || startDestination == "welcome") {
                    return@LaunchedEffect
                }
                if (!startAtPublicChat) return@LaunchedEffect
                if (isDesktopListDetail) {
                    pendingMainTab = MAIN_PAGE_CHATS
                    chatsDetailNavController.navigateToPublicChat()
                } else {
                    navController.navigateToPublicChat()
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
                            if (isDesktopListDetail) {
                                pendingMainTab = MAIN_PAGE_CHATS
                                if (navController.currentBackStackEntry?.destination?.route != "chat") {
                                    navController.popBackStack("chat", inclusive = false)
                                }
                                chatsDetailNavController.navigateToDmChat(
                                    otherUserId = target.dmConversationUserId,
                                    sourceMessageId = target.scrollToMessageId,
                                )
                            } else {
                                navController.navigateToDmChat(
                                    otherUserId = target.dmConversationUserId,
                                    sourceMessageId = target.scrollToMessageId,
                                )
                            }
                        }

                        target.startAtPublicChat -> {
                            Logger.d(
                                "NotificationLaunch",
                                "navigating to public chat launchId=${target.launchId}"
                            )
                            if (isDesktopListDetail) {
                                pendingMainTab = MAIN_PAGE_CHATS
                                if (navController.currentBackStackEntry?.destination?.route != "chat") {
                                    navController.popBackStack("chat", inclusive = false)
                                }
                                chatsDetailNavController.navigateToPublicChat()
                            } else {
                                navController.navigateToPublicChat()
                            }
                        }
                    }
                }
            }

            CompositionLocalProvider(
                LocalNavController provides navController,
                LocalDesktopChatsNavController provides
                    if (isDesktopListDetail) chatsDetailNavController else null,
                LocalDesktopSettingsNavController provides
                    if (isDesktopListDetail) settingsDetailNavController else null,
                LocalSystemBarsVisibility provides rememberSystemBarsController()
            ) {
                if (startDestination != null) {
                    val currentEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = currentEntry?.destination?.route
                    val settingsEntry by settingsDetailNavController.currentBackStackEntryAsState()
                    val settingsRoute = settingsEntry?.destination?.route
                    val settingsProfileUserId = standaloneProfileUserId(settingsEntry)
                    val ownUserId = ApiClient.user?.id
                    val showConversationListDetail =
                        isDesktopListDetail && currentRoute == "chat"
                    val forceSettingsListTab =
                        showConversationListDetail && (
                            isEditProfileRoute(settingsRoute) ||
                                (
                                    isStandaloneProfileRoute(settingsRoute) &&
                                        settingsProfileUserId != null &&
                                        settingsProfileUserId == ownUserId
                                    )
                            )

                    LaunchedEffect(Unit) {
                        DesktopMenuCommands.commands.collect { command ->
                            if (command != DesktopMenuCommand.OpenAbout) return@collect
                            // Logged-out: same root About as Welcome/auth overflow — never mount
                            // the empty chat list–detail shell.
                            if (ApiClient.token.isNullOrBlank()) {
                                navController.navigate(SettingsRoutes.About) {
                                    launchSingleTop = true
                                }
                                return@collect
                            }
                            pendingMainTab = MAIN_PAGE_SETTINGS
                            if (isDesktopListDetail) {
                                settingsDetailNavController.navigateReplacingMainDetail(
                                    route = SettingsRoutes.About,
                                )
                            } else {
                                navController.navigateReplacingMainDetail(
                                    route = SettingsRoutes.About,
                                )
                            }
                        }
                    }

                    ScreenSurface {
                        Box(Modifier.fillMaxSize()) {
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
                                        // List–detail keeps the root on `chat` under the shell;
                                        // empty placeholders live in Desktop*DetailNavHost only.
                                        // Drawing EmptyConversationPlaceholder here ghosts a
                                        // second copy through the transparent chats detail pane.
                                        if (!showConversationListDetail) {
                                            MainScreen(
                                                sharedTransitionScope = this@SharedTransitionLayout,
                                                animatedVisibilityScope = this,
                                                snackbarHostState = profileLookupSnackbarHostState,
                                                initialPage = pendingMainTab,
                                            )
                                        }
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
                                                if (userId == 0) return@ChatsSearchScreen
                                                if (isDesktopListDetail) {
                                                    navController.popBackStack("chat", inclusive = false)
                                                    pendingMainTab = MAIN_PAGE_CHATS
                                                    chatsDetailNavController.navigateReplacingMainDetail(
                                                        "profile/$userId",
                                                    )
                                                } else {
                                                    navController.navigateReplacingMainDetail(
                                                        "profile/$userId",
                                                    )
                                                }
                                            },
                                            onOpenConversation = { userId: Int ->
                                                if (userId == 0) return@ChatsSearchScreen
                                                if (isDesktopListDetail) {
                                                    navController.popBackStack("chat", inclusive = false)
                                                    pendingMainTab = MAIN_PAGE_CHATS
                                                    chatsDetailNavController.navigateToDmChat(userId)
                                                } else {
                                                    navController.navigateToDmChat(userId)
                                                }
                                            }
                                        )
                                    }

                                    // Root graph keeps all detail routes for compact + welcome.
                                    // Desktop list–detail navigates per-tab NavHosts instead and
                                    // leaves the root on `chat` while the shell is visible.
                                    conversationDetailDestinations(
                                        navController = navController,
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        scrollToMessageId = scrollToMessageId,
                                    )
                                    profileDetailDestinations(
                                        navController = navController,
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                    )
                                    settingsDetailDestinations(
                                        navController = navController,
                                        rootNavController = navController,
                                    )
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

                            AppNavHost(Modifier.fillMaxSize())

                            if (showConversationListDetail) {
                                val detailEdgeToEdge =
                                    pendingMainTab == MAIN_PAGE_CHATS ||
                                        pendingMainTab == MAIN_PAGE_CONTACTS
                                val listPaneHazeState = rememberHazeState()
                                CompositionLocalProvider(
                                    LocalPaneHazeState provides listPaneHazeState,
                                    LocalDesktopMainTab provides pendingMainTab,
                                ) {
                                    ConversationListDetailShell(
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
                                                    DesktopChatsDetailNavHost(
                                                        navController = chatsDetailNavController,
                                                        sharedTransitionScope =
                                                            this@SharedTransitionLayout,
                                                        scrollToMessageId = scrollToMessageId,
                                                    )
                                                },
                                                settingsDetail = {
                                                    DesktopSettingsDetailNavHost(
                                                        navController = settingsDetailNavController,
                                                        rootNavController = navController,
                                                        sharedTransitionScope =
                                                            this@SharedTransitionLayout,
                                                        onOpenChatFromProfile = { userId ->
                                                            if (userId > 0) {
                                                                pendingMainTab = MAIN_PAGE_CHATS
                                                                chatsDetailNavController.navigateToDmChat(
                                                                    userId,
                                                                )
                                                            }
                                                        },
                                                    )
                                                },
                                                contactsDetail = {
                                                    DesktopContactsDetailNavHost(
                                                        navController = contactsDetailNavController,
                                                    )
                                                },
                                            )
                                        },
                                    )
                                }
                            }

                            CallOverlay(Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}
