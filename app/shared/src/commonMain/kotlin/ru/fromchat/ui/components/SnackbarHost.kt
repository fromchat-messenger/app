package ru.fromchat.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

/**
 * App-wide snackbar styling: elevated surface container instead of inverse surface.
 */
@Composable
fun FromChatSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    snackbarModifier: Modifier = Modifier,
    shape: Shape = SnackbarDefaults.shape,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
    ) { data ->
        Snackbar(
            snackbarData = data,
            modifier = snackbarModifier,
            shape = shape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            actionColor = MaterialTheme.colorScheme.primary,
            actionContentColor = MaterialTheme.colorScheme.primary,
            dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Dismisses any visible snackbar, then shows [message] without queueing behind it. */
suspend fun SnackbarHostState.showReplacingSnackbar(
    message: String,
    actionLabel: String? = null,
    withDismissAction: Boolean = false,
    duration: SnackbarDuration = SnackbarDuration.Short,
): SnackbarResult {
    currentSnackbarData?.dismiss()
    return showSnackbar(
        message = message,
        actionLabel = actionLabel,
        withDismissAction = withDismissAction,
        duration = duration,
    )
}
