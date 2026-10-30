@file:Suppress("NOTHING_TO_INLINE")

package ru.fromchat.utils

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Indication
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.navigation.NavController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Navigates to the specified route and clears the entire back stack.
 * This ensures the destination becomes the new root of the navigation stack.
 *
 * @param route The destination route to navigate to
 * @param launchSingleTop If true, prevents multiple instances of the same destination
 */
fun NavController.navigateAndWipeBackStack(route: String, launchSingleTop: Boolean = true) {
    // First, pop all previous entries (keeping current screen for now)
    while (previousBackStackEntry != null) {
        if (!popBackStack()) {
            break
        }
    }

    // Get current route to pop it with the navigation
    val currentRoute = currentBackStackEntry?.destination?.route

    // Navigate to the new route, removing the current screen as well
    navigate(route) {
        if (currentRoute != null && currentRoute != route) {
            popUpTo(currentRoute) {
                inclusive = true
            }
        }
        this.launchSingleTop = launchSingleTop
    }
}

inline fun Modifier.conditional(
    condition: Boolean,
    crossinline `else`: @Composable Modifier.(Modifier) -> Modifier = { Modifier },
    crossinline `if`: @Composable Modifier.(Modifier) -> Modifier
) = composed {
    if (condition) {
        this + `if`(this)
    } else {
        this + `else`(this)
    }
}

inline fun Dp.toPx(density: Density) = with(density) { this@toPx.toPx() }

inline fun Modifier.verticalScroll(
    enabled: Boolean = true,
    flingBehavior: FlingBehavior? = null,
    reverseScrolling: Boolean = false
) = composed {
    this@verticalScroll.verticalScroll(
        rememberScrollState(),
        enabled,
        flingBehavior,
        reverseScrolling
    )
}

inline fun Modifier.verticalScroll(
    overscrollEffect: OverscrollEffect?,
    enabled: Boolean = true,
    flingBehavior: FlingBehavior? = null,
    reverseScrolling: Boolean = false
) = composed {
    this@verticalScroll.verticalScroll(
        rememberScrollState(),
        overscrollEffect,
        enabled,
        flingBehavior,
        reverseScrolling
    )
}

inline fun Int.toDp(density: Density) = with(density) { this@toDp.toDp() }

@Suppress("NOTHING_TO_INLINE")
inline fun WindowInsets.exclude(sides: WindowInsetsSides) = exclude(this.only(sides))

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
                        delay(80.milliseconds)
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

/**
 * Android: ties scroll to IME insets for smoother keyboard transitions (see Compose keyboard animations).
 * iOS: interactive UIScrollView dismiss + scroll-down hides IME on scrollables using this modifier.
 * Other platforms: no-op unless noted.
 */
expect fun Modifier.imeScrollWithKeyboard(): Modifier

/** Platform hook for native interactive keyboard dismiss (iOS UIScrollView). */
@Composable
expect fun PlatformInteractiveKeyboardDismissEffect()