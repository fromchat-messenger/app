package ru.fromchat.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials

private const val DEFAULT_TINT_ALPHA = 0.06f

/**
 * Applies [HazeMaterials.thin] with a subtle primary-color [tint] on top,
 * producing the "colorful blur" look from the Pixel-styled redesign. Use on
 * the nav bar and any panel/chrome surface where we want the blur to pick up
 * a little color. Search bars and text fields should **not** use it.
 *
 * @param hazeState The [HazeState] to blur against (must have matching
 *   `.hazeSource(hazeState)` ancestors).
 * @param tint The color to tint the blurred backdrop with.
 * @param alpha How visible the tint is, 0.0 = no tint, 1.0 = full tint.
 */
@Composable
fun Modifier.tintedHaze(
    hazeState: HazeState,
    tint: Color = MaterialTheme.colorScheme.primary,
    alpha: Float = DEFAULT_TINT_ALPHA,
): Modifier = hazeBlur(
    input = HazeInput.Backdrop(hazeState),
    style = HazeMaterials.thin().then {
        colorEffects(listOf(HazeColorEffect.tint(tint.copy(alpha = alpha))))
    },
)
