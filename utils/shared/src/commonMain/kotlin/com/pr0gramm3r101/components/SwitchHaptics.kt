package com.pr0gramm3r101.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal that carries the haptic-firing lambda for `SwitchListItem`.
 * Defaults to a no-op so it works on any platform / without a provider.
 *
 * The app root (in `:app:shared`) calls `provideSwitchHaptics()`, which stores the
 * `rememberHapticFeedback()` callback here.
 *
 * @see SwitchListItem
 */
val LocalSwitchHaptic = compositionLocalOf<(Int) -> Unit> { { _ -> } }

// Ordinals mirror `ru.fromchat.utils.haptic.HapticFeedbackEvent` (see enum).
internal const val HAPTIC_OR_SWITCH_PRESS: Int = 5
internal const val HAPTIC_OR_SWITCH_TOGGLE: Int = 6

/**
 * Internal accessor used by the inline [SwitchListItem].
 *
 * Returns:
 * - first element  → press haptic (light tick at touch-down)
 * - second element → commit haptic (heavier click at state flip)
 *
 * We pass a plain `Int` so `:utils:shared` has no compile-time dependency on the
 * `ru.fromchat.utils.haptic.HapticFeedbackEvent` enum (which lives in `:app:shared`).
 */
@Composable
fun switchHapticCallbacks(): Pair<() -> Unit, () -> Unit> {
    val fire = LocalSwitchHaptic.current
    return ({ fire(HAPTIC_OR_SWITCH_PRESS) }) to ({ fire(HAPTIC_OR_SWITCH_TOGGLE) })
}
