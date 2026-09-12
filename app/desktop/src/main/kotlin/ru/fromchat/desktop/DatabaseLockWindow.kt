package ru.fromchat.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.desktop_database_locked_kill
import ru.fromchat.desktop_database_locked_pid
import ru.fromchat.desktop_database_locked_message
import ru.fromchat.desktop_database_locked_title
import ru.fromchat.desktop_database_locked_unknown_process
import ru.fromchat.desktop_database_locked_waiting
import ru.fromchat.desktop_quit
import ru.fromchat.ui.FromChatTheme

@Composable
fun DatabaseLockWindow(
    processes: List<LockingProcessInfo>,
    onKillProcess: (Long) -> Unit,
    onQuit: () -> Unit,
) {
    val title = stringResource(Res.string.desktop_database_locked_title)
    val windowState = rememberWindowState(width = 520.dp, height = 420.dp)
    Window(
        onCloseRequest = onQuit,
        title = title,
        state = windowState,
        resizable = false,
        alwaysOnTop = true,
    ) {
        FromChatTheme(darkTheme = desktopAppDarkTheme()) {
            Surface(color = MaterialTheme.colorScheme.background) {
                DatabaseLockContent(
                    processes = processes,
                    onKillProcess = onKillProcess,
                    onQuit = onQuit,
                )
            }
        }
    }
}

@Composable
private fun DatabaseLockContent(
    processes: List<LockingProcessInfo>,
    onKillProcess: (Long) -> Unit,
    onQuit: () -> Unit,
) {
    val unknownProcess = stringResource(Res.string.desktop_database_locked_unknown_process)
    Column(
        modifier = Modifier
            .widthIn(min = 480.dp, max = 520.dp)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = stringResource(Res.string.desktop_database_locked_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Text(
            text = stringResource(Res.string.desktop_database_locked_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (processes.isEmpty()) {
            LockingProcessRow(
                process = LockingProcessInfo(
                    pid = -1L,
                    name = unknownProcess,
                    description = null,
                    executablePath = null,
                    icon = null,
                ),
                killEnabled = false,
                onKill = {},
            )
        } else {
            processes.forEach { process ->
                LockingProcessRow(
                    process = process,
                    killEnabled = process.pid > 0L,
                    onKill = { onKillProcess(process.pid) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(
                text = stringResource(Res.string.desktop_database_locked_waiting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            OutlinedButton(onClick = onQuit) {
                Text(stringResource(Res.string.desktop_quit))
            }
        }
    }
}

@Composable
private fun LockingProcessRow(
    process: LockingProcessInfo,
    killEnabled: Boolean,
    onKill: () -> Unit,
) {
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (process.icon != null) {
                Image(
                    bitmap = process.icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = process.name,
                    style = MaterialTheme.typography.titleMedium,
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
            if (killEnabled) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onKill) {
                    Text(stringResource(Res.string.desktop_database_locked_kill))
                }
            }
        }
    }
}
