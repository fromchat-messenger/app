package ru.fromchat.ui

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Companion.End
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Companion.Start
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.fromchat.api.ApiClient
import ru.fromchat.api.WebSocketManager
import ru.fromchat.core.config.Config
import ru.fromchat.ui.auth.LoginScreen
import ru.fromchat.ui.auth.RegisterScreen
import ru.fromchat.ui.chat.PublicChatScreen
import ru.fromchat.ui.debug.DebugApiScreen
import ru.fromchat.ui.dm.DmScreen
import ru.fromchat.ui.main.MainScreen
import ru.fromchat.ui.profile.ProfileScreen
import ru.fromchat.ui.setup.ServerConfigScreen

val LocalNavController = compositionLocalOf<NavController> { error("NavController not provided") }

@Composable
fun App(scrollToMessageId: Int? = null, startAtPublicChat: Boolean = false) {
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            Config.initialize()
        }

        // Load persisted token and user data
        ApiClient.loadPersistedData()

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
                    // Connect WebSocket when app comes to foreground
                    WebSocketManager.connect()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    // Disconnect WebSocket when app goes to background
                    WebSocketManager.disconnect()
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
            LocalNavController provides navController
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
                            onRegistered = { navController.navigate("login") }
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
                        PublicChatScreen(scrollToMessageId = scrollToMessageId)
                    }

                    composable("debug") {
                        DebugApiScreen()
                    }

                    composable("profile/{userId}") { backStackEntry ->
                        val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull()
                        ProfileScreen(
                            userId = userId,
                            onBack = { navController.navigateUp() },
                            onChat = { navController.navigate("dm/$it") }
                        )
                    }

                    composable("dm/{otherUserId}") { backStackEntry ->
                        val otherUserId = backStackEntry.arguments?.getString("otherUserId")?.toIntOrNull() ?: 0
                        DmScreen(
                            otherUserId = otherUserId
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
