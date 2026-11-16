package ru.fromchat.ui.main.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut

private val detailNavTween = tween<Float>(durationMillis = 250, easing = FastOutSlowInEasing)

fun detailNavEnterTransition(): EnterTransition = mutedEnterTransition(
    scaleIn(initialScale = 0.9f, animationSpec = detailNavTween) +
        fadeIn(animationSpec = detailNavTween),
)

fun detailNavExitTransition(): ExitTransition = mutedExitTransition(
    scaleOut(targetScale = 1.1f, animationSpec = detailNavTween) +
        fadeOut(animationSpec = detailNavTween),
)

fun detailNavPopEnterTransition(): EnterTransition = mutedEnterTransition(
    scaleIn(initialScale = 1.1f, animationSpec = detailNavTween) +
        fadeIn(animationSpec = detailNavTween),
)

fun detailNavPopExitTransition(): ExitTransition = mutedExitTransition(
    scaleOut(targetScale = 0.9f, animationSpec = detailNavTween) +
        fadeOut(animationSpec = detailNavTween),
)