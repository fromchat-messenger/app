package ru.fromchat.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.pr0gramm3r101.components.LocalSwitchHaptic
import ru.fromchat.utils.haptic.HapticFeedbackEvent
import ru.fromchat.utils.haptic.rememberHapticFeedback

/**
 * Provides the real haptic-firing lambda to every `SwitchListItem` in the subtree.
 * Call once at the app root (e.g., inside `@Preview`/`App`/main activity content).
 *
 * - `SwitchPress` (light tick on touch-down) → `VibrationEffect.EFFECT_TICK` (API 26+)
 *   or `HapticFeedbackConstants.CLOCK_TICK` (API 24–25) on Android; `UIImpactFeedbackStyleLight`
 *   on iOS; no-op on Desktop.
 * - `SwitchToggle` (heavier commit click when state flips) → `VibrationEffect.EFFECT_CLICK`
 *   / `HapticFeedbackConstants.CONFIRM` on Android; `UIImpactFeedbackStyleHeavy` on iOS.
 *
 * The default [LocalSwitchHaptic] is a no-op, so a screen that forgets to call this
 * provider still renders correctly — it just doesn't vibrate.
 */
@Composable
fun provideSwitchHaptics(content: @Composable () -> Unit) {
    val fire = rememberHapticFeedback()
    CompositionLocalProvider(
        LocalSwitchHaptic provides { ordinal: Int ->
            // Map integer ordinal → enum by position. The enum ordinals are stable
            // because we only append at the end (see HapticFeedbackEvent.kt).
            val event = HapticFeedbackEvent.entries.getOrNull(ordinal)
            if (event != null) fire(event)
        },
    ) {
        content()
    }
}
