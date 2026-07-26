package ru.fromchat.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.fromchat.legal.DocumentScreen
import ru.fromchat.legal.DocumentType
import ru.fromchat.ui.FromChatTheme
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.components.ScreenSurface
import ru.fromchat.ui.main.settings.AboutScreen

/**
 * Hosts [AboutScreen] (and linked legal docs) for a dedicated desktop About window.
 * Back from the root About destination closes via [onClose].
 */
@Composable
fun DesktopAboutContent(onClose: () -> Unit) {
    FromChatTheme {
        ScreenSurface {
            val navController = rememberNavController()
            var aboutOpened by remember { mutableStateOf(false) }

            CompositionLocalProvider(LocalNavController provides navController) {
                NavHost(
                    navController = navController,
                    startDestination = ABOUT_GATE_ROUTE,
                ) {
                    composable(ABOUT_GATE_ROUTE) {
                        LaunchedEffect(Unit) {
                            if (!aboutOpened) {
                                aboutOpened = true
                                navController.navigate(ABOUT_ROUTE)
                            } else {
                                onClose()
                            }
                        }
                    }

                    composable(ABOUT_ROUTE) {
                        AboutScreen()
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
                }
            }
        }
    }
}

private const val ABOUT_GATE_ROUTE = "_about_gate"
private const val ABOUT_ROUTE = "about"
