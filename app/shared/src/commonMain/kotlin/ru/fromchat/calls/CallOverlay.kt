package ru.fromchat.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.*
import ru.fromchat.api.ApiClient
import ru.fromchat.api.ProfileCache
import ru.fromchat.api.visibleDisplayName

@Composable
fun CallOverlay(modifier: Modifier = Modifier) {
    val state by CallStore.ui.collectAsState()
    when (val s = state) {
        CallUiState.Idle -> return
        is CallUiState.Failed -> {
            AlertDialog(
                onDismissRequest = { CallStore.dismissFailed() },
                title = { Text(stringResource(Res.string.call_failed_title)) },
                text = { Text(s.message) },
                confirmButton = {
                    TextButton(onClick = { CallStore.dismissFailed() }) {
                        Text(stringResource(Res.string.call_dismiss))
                    }
                },
            )
        }
        is CallUiState.Connecting -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(Res.string.call_status_starting),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        is CallUiState.Incoming -> {
            val me = ApiClient.user?.id
            val cached = ProfileCache.get(s.fromUserId)
            val title =
                cached?.visibleDisplayName(me)?.takeIf { it.isNotBlank() }
                    ?: cached?.username?.takeIf { it.isNotBlank() }
                    ?: stringResource(Res.string.user_fallback, s.fromUserId)
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.call_incoming_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { CallStore.declineIncoming() }) {
                            Text(stringResource(Res.string.call_decline))
                        }
                        Button(onClick = { CallStore.acceptIncoming() }) {
                            Text(stringResource(Res.string.call_accept))
                        }
                    }
                }
            }
        }
        is CallUiState.InCall -> {
            Box(modifier = modifier.fillMaxSize()) {
                CallMediaLayer(
                    connect = s.session,
                    showDialingPlaceholder = false,
                    showInCallControls = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
