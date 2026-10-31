package ru.fromchat.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

private const val SplashHandoffInitialScale = 1.2f

private val handoffSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

@Composable
fun SplashHandoffContainer(
    handoffActive: Boolean,
    content: @Composable () -> Unit,
) {
    val scale = remember { Animatable(SplashHandoffInitialScale) }
    LaunchedEffect(handoffActive) {
        if (!handoffActive) return@LaunchedEffect
        scale.animateTo(
            targetValue = 1f,
            animationSpec = handoffSpring,
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            },
    ) {
        content()
    }
}
