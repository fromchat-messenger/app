package ru.fromchat.ui.components

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import ru.fromchat.utils.haptic.HapticFeedbackEvent
import androidx.activity.compose.BackHandler as AndroidBackHandler

@Composable
actual fun PredictiveBackHandler(
    enabled: Boolean,
    onProgress: (Float) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
) {
    PredictiveBackHandler(enabled = enabled) { progressFlow: Flow<BackEventCompat> ->
        try {
            progressFlow.collect { backEvent ->
                onProgress(backEvent.progress.coerceIn(0f, 1f))
            }
            onCommit()
        } catch (e: CancellationException) {
            onCancel()
            throw e
        }
    }
}


@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    AndroidBackHandler(enabled = enabled, onBack = onBack)
}

@SuppressLint("InlinedApi")
@Composable
actual fun rememberHapticFeedbackInternal(): (Int) -> Unit {
    val view = LocalView.current
    return remember(view) {
        { ordinal ->
            val constant = when (ordinal) {
                HapticFeedbackEvent.ProfileOpened.ordinal -> HapticFeedbackConstants.CLOCK_TICK
                HapticFeedbackEvent.ProfileClosed.ordinal -> HapticFeedbackConstants.CLOCK_TICK
                HapticFeedbackEvent.MessageSent.ordinal -> HapticFeedbackConstants.CONFIRM
                HapticFeedbackEvent.ContextMenuOpened.ordinal -> HapticFeedbackConstants.CONFIRM
                HapticFeedbackEvent.SelectionModeEntered.ordinal -> HapticFeedbackConstants.CONFIRM
                else -> HapticFeedbackConstants.CLOCK_TICK
            }
            view.performHapticFeedback(constant)
        }
    }
}
