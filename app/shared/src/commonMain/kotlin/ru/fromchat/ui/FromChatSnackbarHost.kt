package ru.fromchat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
    val scheme = MaterialTheme.colorScheme
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
    ) { data ->
        Snackbar(
            snackbarData = data,
            modifier = snackbarModifier,
            shape = shape,
            containerColor = scheme.surfaceContainerHigh,
            contentColor = scheme.onSurface,
            actionColor = scheme.primary,
            actionContentColor = scheme.primary,
            dismissActionContentColor = scheme.onSurfaceVariant,
        )
    }
}
