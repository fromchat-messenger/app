package ru.fromchat.ui

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Companion.End
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Companion.Start
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
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
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.fromchat.api.ApiClient
import ru.fromchat.api.ProfileCache
import ru.fromchat.api.UpdateSyncManager
import ru.fromchat.api.WebSocketManager
import ru.fromchat.net.NetworkConnectivity
import ru.fromchat.core.config.Config
import ru.fromchat.ui.auth.LoginScreen
import ru.fromchat.ui.auth.RegisterScreen
import ru.fromchat.ui.chat.PublicChatScreen
import ru.fromchat.ui.debug.DebugApiScreen
import ru.fromchat.ui.dm.DmContainerScreen
import ru.fromchat.ui.main.MainScreen
import ru.fromchat.ui.profile.ProfileScreen
import ru.fromchat.ui.setup.ServerConfigScreen
import ru.fromchat.ui.LocalSystemBarsVisibility
import ru.fromchat.ui.rememberSystemBarsController

val LocalNavController = compositionLocalOf<NavController> { error("NavController not provided") }

@Composable
fun App(scrollToMessageId: Int? = null, startAtPublicChat: Boolean = false) {
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            Config.initialize()
        }

        runCatching { NetworkConnectivity.ensureStarted() }

        // Load persisted token and user data
        ApiClient.loadPersistedData()

        runCatching { ProfileCache.hydrateFromDisk() }

        // Initialize update sync state for the current user (if any)
        runCatching {
            UpdateSyncManager.initializeFromStorage(ApiClient.user?.id)
        }

        // Now determine start destination based on loaded token
        val hasToken = ApiClient.token?.isNotEmpty() == true
        startDestination = when {
            hasToken && startAtPublicChat -> "chats/publicChat"
            hasToken && !startAtPublicChat -> "chat"
            else -> "login"
        }
    }

    // Observe lifecycle events to manage WebSocket connection
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // Ensure WebSocket connection loop is running when app comes to foreground
                    WebSocketManager.connect()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    // No-op for connection lifecycle: WebSocketManager keeps trying to reconnect
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    FromChatTheme {
        SharedTransitionLayout {
            val navController = rememberNavController()

            // Handle navigation to public chat when requested (e.g., from notification)
            LaunchedEffect(startAtPublicChat) {
                if (startAtPublicChat && navController.currentDestination?.route != "chats/publicChat") {
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
                            onLogout = {
                                navController.navigate("login") {
                                    popUpTo("chat") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable("chats/publicChat") {
                        PublicChatScreen(
                            scrollToMessageId = scrollToMessageId,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedContentScope = this@composable
                        )
                    }

                    composable("debug") {
                        DebugApiScreen()
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
                            onChat = { navController.navigate("dm/$it") },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@composable,
                            useSharedElementFromNavigation = useSharedElement,
                            sharedSourceMessageId = sourceMessageId
                        )
                    }

                    composable("dm/{otherUserId}") { backStackEntry ->
                        val otherUserId = backStackEntry.savedStateHandle.get<String>("otherUserId")?.toIntOrNull() ?: 0
                        DmContainerScreen(
                            otherUserId = otherUserId,
                            onBack = { navController.navigateUp() }
                        )
                    }

                    composable("about") {
                        AboutScreen()
                    }
                    }
                }
            }
        }
    }
}
