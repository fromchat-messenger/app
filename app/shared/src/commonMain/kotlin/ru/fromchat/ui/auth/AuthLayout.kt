package ru.fromchat.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import ru.fromchat.ui.components.SpringExpandingOverflowMenu
import ru.fromchat.ui.components.SpringDropdownMenuItem
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.components.TextCta
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.about
import ru.fromchat.auth_incognito_login_action
import ru.fromchat.auth_standard_login_action
import ru.fromchat.change_server
import ru.fromchat.logs_title
import androidx.compose.material.icons.rounded.Storage
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.theme.extraStatusBars
import ru.fromchat.ui.main.settings.about.LocalOpenAbout
import ru.fromchat.ui.main.settings.SettingsRoutes
import ru.fromchat.ui.main.settings.SettingsStepHorizontalPadding

internal const val AUTH_ROUTE = "auth"
internal const val AUTH_INCOGNITO_ROUTE = "auth/incognito"

/** Margin between the window edge and the wide [AuthContentFrame] panel. */
val AuthWidePanelOuterPadding = 32.dp

/**
 * Centers auth content on wide layouts (max 600×800.dp, [AuthWidePanelOuterPadding] margin).
 * Narrow layouts fill the window edge-to-edge ([widePanel] = false) so top-bar haze can
 * draw under the status bar; wide layouts clear system bars outside the panel.
 */
@Composable
fun AuthContentFrame(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(widePanel: Boolean) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val widePanel = maxWidth > 600.dp
        if (widePanel) {
            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.extraStatusBars)),
            ) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .padding(AuthWidePanelOuterPadding)
                        .widthIn(max = 600.dp)
                        .heightIn(max = 800.dp),
                ) {
                    content(true)
                }
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                content(false)
            }
        }
    }
}

/** Overflow menu shared by welcome and auth steps (About, Logs). */
@Composable
fun PreAuthOverflowMenu() {
    val navController = LocalNavController.current
    val openAbout = LocalOpenAbout.current
    var menuExpanded by remember { mutableStateOf(false) }

    SpringExpandingOverflowMenu(
        expanded = menuExpanded,
        onExpandedChange = { menuExpanded = it },
    ) {
        SpringDropdownMenuItem(
            text = { Text(stringResource(Res.string.about)) },
            leadingIcon = {
                Icon(Icons.Rounded.Info, contentDescription = null)
            },
            onClick = {
                menuExpanded = false
                openAbout()
            },
        )
        SpringDropdownMenuItem(
            text = { Text(stringResource(Res.string.logs_title)) },
            leadingIcon = {
                Icon(Icons.Rounded.BugReport, contentDescription = null)
            },
            onClick = {
                menuExpanded = false
                navController.navigate(SettingsRoutes.Logs)
            },
        )
    }
}

/** Footer row below auth steps: server picker plus standard ↔ incognito sign-in routes. */
@Composable
internal fun AuthStepListFooter(
    incognitoFlow: Boolean,
    hideAuthRouteSwitch: Boolean = false,
) {
    val navController = LocalNavController.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = SettingsStepHorizontalPadding,
                end = SettingsStepHorizontalPadding,
                top = 6.dp,
                bottom = 6.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextCta(
            onClick = { navController.navigate("serverConfig") },
            modifier = if (hideAuthRouteSwitch) Modifier.fillMaxWidth() else Modifier.weight(1f),
            leadingIcon = Icons.Rounded.Storage,
            compact = true,
        ) {
            Text(stringResource(Res.string.change_server))
        }
        if (hideAuthRouteSwitch) return@Row
        if (incognitoFlow) {
            TextCta(
                onClick = {
                    navController.navigate(AUTH_ROUTE) {
                        popUpTo(AUTH_INCOGNITO_ROUTE) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                modifier = Modifier.weight(1f),
                leadingIcon = Icons.Rounded.Login,
                compact = true,
            ) {
                Text(stringResource(Res.string.auth_standard_login_action))
            }
        } else {
            TextCta(
                onClick = {
                    navController.navigate(AUTH_INCOGNITO_ROUTE) {
                        popUpTo(AUTH_ROUTE) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                modifier = Modifier.weight(1f),
                leadingIcon = Icons.Rounded.VisibilityOff,
                compact = true,
            ) {
                Text(stringResource(Res.string.auth_incognito_login_action))
            }
        }
    }
}
