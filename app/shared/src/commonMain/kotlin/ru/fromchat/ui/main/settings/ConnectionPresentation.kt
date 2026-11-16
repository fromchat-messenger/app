package ru.fromchat.ui.main.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import ru.fromchat.ui.theme.extraStatusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import ru.fromchat.api.ApiClient
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.main.nav.NavTransitionMuting
import ru.fromchat.ui.main.nav.connectionDetailDestinations
import ru.fromchat.ui.main.nav.detailNavEnterTransition
import ru.fromchat.ui.main.nav.detailNavExitTransition
import ru.fromchat.ui.main.nav.detailNavPopEnterTransition
import ru.fromchat.ui.main.nav.detailNavPopExitTransition

/** Pre-auth overflow: open Connection settings (dialog on wide, full-screen overlay on compact). */
val LocalOpenConnection = staticCompositionLocalOf<() -> Unit> {
    error("LocalOpenConnection not provided")
}

/** When set, the connection hub shows a close button and dismisses the overlay. */
internal val LocalConnectionDialogRootDismiss = staticCompositionLocalOf<(() -> Unit)?> { null }

fun isConnectionFlowRoute(route: String?): Boolean =
    when (route) {
        SettingsRoutes.Connection,
        SettingsRoutes.ServerConfig,
        SettingsRoutes.Proxy,
        SettingsRoutes.TrustedCertificates,
        -> true

        else -> route != null && (
            route.startsWith("settings/proxy/add/") ||
                route.startsWith("settings/proxy/edit/")
            )
    }

fun NavController.popConnectionFlowFromRoot() {
    var guard = 0
    while (guard < 32 && isConnectionFlowRoute(currentBackStackEntry?.destination?.route)) {
        guard += 1
        if (!popBackStack()) break
    }
}

/** Leaves a single [SettingsRoutes.Connection] entry so the flow can reopen after the overlay is hidden. */
fun NavHostController.resetConnectionFlowToRoot() {
    navigate(SettingsRoutes.Connection) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

@Composable
private fun ConnectionFlowNavHost(
    navController: NavHostController,
    onDismiss: () -> Unit,
    rootNavController: NavController,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(
        LocalNavController provides navController,
        LocalConnectionDialogRootDismiss provides onDismiss,
    ) {
        NavHost(
            navController = navController,
            startDestination = SettingsRoutes.Connection,
            modifier = modifier.fillMaxSize(),
            enterTransition = { detailNavEnterTransition() },
            exitTransition = { detailNavExitTransition() },
            popEnterTransition = { detailNavPopEnterTransition() },
            popExitTransition = { detailNavPopExitTransition() },
        ) {
            connectionDetailDestinations(
                navController = navController,
                rootNavController = rootNavController,
            )
        }

        LaunchedEffect(navController) {
            if (navController.currentBackStackEntry == null) {
                navController.resetConnectionFlowToRoot()
            }
        }
    }
}

/**
 * Pre-auth connection flow: one [NavHostController] for the whole stack, shown in a dialog (wide)
 * or full-screen overlay (compact). Resize only swaps the chrome — back stack and screen state stay.
 */
@Composable
fun ConnectionFlowHost(
    active: Boolean,
    useDialog: Boolean,
    navController: NavHostController,
    onDismiss: () -> Unit,
    rootNavController: NavController,
) {
    if (!active) return

    val movedNavHost = remember(navController, onDismiss, rootNavController) {
        movableContentOf {
            ConnectionFlowNavHost(
                navController = navController,
                onDismiss = onDismiss,
                rootNavController = rootNavController,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    LaunchedEffect(useDialog) {
        NavTransitionMuting.mute()
    }

    if (useDialog) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(max = 440.dp)
                    .heightIn(max = 720.dp),
            ) {
                CompositionLocalProvider(LocalSettingsDetailInDialog provides true) {
                    movedNavHost()
                }
            }
        }
    } else {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.extraStatusBars)),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                CompositionLocalProvider(LocalSettingsDetailInDialog provides false) {
                    movedNavHost()
                }
            }
        }
    }
}