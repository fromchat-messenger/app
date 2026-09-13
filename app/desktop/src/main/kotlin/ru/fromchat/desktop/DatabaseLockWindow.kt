package ru.fromchat.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.desktop_database_locked_kill
import ru.fromchat.desktop_database_locked_message
import ru.fromchat.desktop_database_locked_pid
import ru.fromchat.desktop_database_locked_title
import ru.fromchat.desktop_database_locked_unknown_hint
import ru.fromchat.desktop_database_locked_waiting
import ru.fromchat.desktop_quit
import ru.fromchat.ui.components.ActionButton
import ru.fromchat.ui.components.ExpressiveIconFrame
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.components.TextCta
import java.awt.image.BufferedImage

@Composable
fun DatabaseLockWindow(
    appName: String,
    windowIcon: Painter,
    dockIconImage: BufferedImage?,
    processes: List<LockingProcessInfo>,
    onKillProcess: (Long) -> Unit,
    onQuit: () -> Unit,
) {
    val windows = remember { isWindowsOs() }
    val windowState = rememberWindowState(width = 420.dp, height = 480.dp)
    Window(
        onCloseRequest = onQuit,
        title = appName,
        state = windowState,
        icon = windowIcon,
        undecorated = windows,
        resizable = false,
    ) {
        DesktopRootSurface(
            appName = appName,
            windowIcon = windowIcon,
            dockIconImage = dockIconImage,
            windows = windows,
            windowState = windowState,
            onCloseRequest = onQuit,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = if (windows) WindowsTitleBarHeight + 24.dp else 24.dp,
                        start = 32.dp,
                        end = 32.dp,
                        bottom = 24.dp,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                DatabaseLockContent(
                    processes = processes,
                    onKillProcess = onKillProcess,
                    onQuit = onQuit,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DatabaseLockContent(
    processes: List<LockingProcessInfo>,
    onKillProcess: (Long) -> Unit,
    onQuit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .widthIn(max = 360.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ExpressiveIconFrame(
            icon = Icons.Default.Warning,
            containerSize = 56.dp,
            iconSize = 28.dp,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            materialPolygon = MaterialShapes.SoftBurst,
        )
        Text(
            text = stringResource(Res.string.desktop_database_locked_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.desktop_database_locked_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (processes.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                processes.forEach { process ->
                    LockingProcessRow(
                        process = process,
                        onKill = { onKillProcess(process.pid) },
                    )
                }
            }
        } else {
            Text(
                text = stringResource(Res.string.desktop_database_locked_unknown_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(Res.string.desktop_database_locked_waiting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TextCta(
            onClick = onQuit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(Res.string.desktop_quit),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LockingProcessRow(
    process: LockingProcessInfo,
    onKill: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (process.icon != null) {
            Image(
                bitmap = process.icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = process.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            process.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (process.pid > 0L) {
                Text(
                    text = stringResource(Res.string.desktop_database_locked_pid, process.pid.toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (process.pid > 0L) {
            Spacer(modifier = Modifier.width(8.dp))
            ActionButton(onClick = onKill) {
                Text(stringResource(Res.string.desktop_database_locked_kill))
            }
        }
    }
}
