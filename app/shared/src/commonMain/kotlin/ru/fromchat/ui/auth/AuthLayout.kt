package ru.fromchat.ui.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import ru.fromchat.logs_title
import ru.fromchat.more
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.extraStatusBars
import ru.fromchat.ui.main.settings.SettingsRoutes

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
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(Res.string.more),
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.about)) },
                leadingIcon = {
                    Icon(Icons.Default.Info, contentDescription = null)
                },
                onClick = {
                    menuExpanded = false
                    navController.navigate(SettingsRoutes.About)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.logs_title)) },
                leadingIcon = {
                    Icon(Icons.Outlined.BugReport, contentDescription = null)
                },
                onClick = {
                    menuExpanded = false
                    navController.navigate(SettingsRoutes.Logs)
                },
            )
        }
    }
}
