package ru.fromchat.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * Full-screen backdrop blur used while a floating row context menu is open (chats + settings lists).
 * Must live in the composition tree (not a [androidx.compose.ui.window.Popup]) so it blurs content
 * behind the overlay replica instead of blurring the menu popup itself.
 */
@Composable
fun ContextMenuBlurLayer(
    hazeState: HazeState,
    blurProgress: Float,
    modifier: Modifier = Modifier,
) {
    val progress = blurProgress.coerceIn(0f, 1f)
    val blurRadius = 12.dp * progress
    if (blurRadius <= 0.dp) return

    val surface = MaterialTheme.colorScheme.surfaceContainerLowest
    val tint = HazeColorEffect.tint(surface.copy(alpha = 0.4f * progress))
    Box(
        modifier = modifier
            .fillMaxSize()
            .hazeBlur(
                input = HazeInput.Backdrop(hazeState),
                style = HazeBlurStyle {
                    blurRadius(blurRadius)
                    backgroundColor(surface)
                    colorEffects(listOf(tint))
                    noiseFactor(0f)
                    fallbackColorEffect(tint)
                },
            ),
    )
}
