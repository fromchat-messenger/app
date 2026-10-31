package ru.fromchat.ui.components

import android.annotation.SuppressLint
import android.os.Build
import android.os.VibrationEffect
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
            // Two-stage premium switch feel: SwitchPress → light tick, SwitchToggle →
            // heavier commit click. On API 26+ we use the predefined-effect overload
            // (VibrationEffect.EFFECT_TICK / EFFECT_CLICK) which routes to the system
            // without VIBRATE permission and has built-in device fallback. On API 24-25
            // we fall back to the legacy int constants.
            when (ordinal) {
                HapticFeedbackEvent.SwitchPress.ordinal -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        view.performHapticFeedback(0, VibrationEffect.EFFECT_TICK)
                    } else {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                }

                HapticFeedbackEvent.SwitchToggle.ordinal -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        view.performHapticFeedback(0, VibrationEffect.EFFECT_CLICK)
                    } else {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                }

                else -> {
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
    }
}
