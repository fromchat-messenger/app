package ru.fromchat.calls

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.*
import ru.fromchat.api.ApiClient
import ru.fromchat.api.ProfileCache
import ru.fromchat.api.visibleDisplayName
import ru.fromchat.ui.LocalSystemBarsVisibility

@Composable
fun CallOverlay(modifier: Modifier = Modifier) {
    val state by CallStore.ui.collectAsState()
    val systemBars = LocalSystemBarsVisibility.current
    LaunchedEffect(state) {
        when (state) {
            is CallUiState.InCall,
            is CallUiState.Incoming,
            -> systemBars?.invoke(false)
            else -> systemBars?.invoke(true)
        }
    }
    when (val s = state) {
        CallUiState.Idle,
        is CallUiState.Connecting,
        -> return
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
            val enter = remember(s.session.roomName) { Animatable(0.88f) }
            LaunchedEffect(s.session.roomName) {
                enter.snapTo(0.88f)
                enter.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
            }
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .graphicsLayer {
                        scaleX = enter.value
                        scaleY = enter.value
                        transformOrigin = TransformOrigin(0.5f, 0.42f)
                    },
            ) {
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
