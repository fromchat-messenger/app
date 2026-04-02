package ru.fromchat.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.pr0gramm3r101.utils.conditional
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Scale-down touch effect: scales to [scale] on press and back to 1f on release/cancel.
 * When [interactionSource] is null: optionally wraps with [clickable] when [onClick] is non-null.
 * When [interactionSource] is non-null: uses it for scale only (caller adds clickable on child so ripple scales with content).
 * [indication] when non-null is used for the clickable (e.g. ripple); null = no indication.
 * [clipShape] when non-null clips the scaled result.
 * [animationSpec] drives the scale transition (default is a spring).
 */
fun Modifier.scaleOnPress(
    scale: Float = 0.96f,
    onClick: (() -> Unit)? = null,
    indication: Indication? = null,
    clipShape: Shape? = null,
    interactionSource: MutableInteractionSource? = null,
    animationSpec: AnimationSpec<Float> = spring()
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(source) {
        var releaseJob: Job? = null
        source.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    releaseJob?.cancel()
                    releaseJob = null
                    pressed = true
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    releaseJob = scope.launch {
                        delay(80)
                        pressed = false
                    }
                }
            }
        }
    }

    val scaleValue by animateFloatAsState(
        targetValue = if (pressed) scale else 1f,
        animationSpec = animationSpec,
        label = "scaleOnPress"
    )

    Modifier
        .conditional(onClick != null && interactionSource == null) {
            clickable(
                interactionSource = source,
                indication = indication,
                onClick = onClick!!
            )
        }
        .conditional(clipShape != null) { clip(clipShape!!) }
        .graphicsLayer(
            scaleX = scaleValue,
            scaleY = scaleValue,
            transformOrigin = TransformOrigin.Center
        )
}
