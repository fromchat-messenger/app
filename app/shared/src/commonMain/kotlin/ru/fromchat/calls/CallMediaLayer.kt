package ru.fromchat.calls

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun CallMediaLayer(
    connect: LiveKitConnectSession?,
    showDialingPlaceholder: Boolean,
    /** Android: show mic/camera/share/end over video when in an active LiveKit session. */
    showInCallControls: Boolean = false,
    modifier: Modifier = Modifier,
)
