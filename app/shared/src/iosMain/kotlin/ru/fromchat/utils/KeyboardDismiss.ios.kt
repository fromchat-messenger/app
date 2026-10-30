package ru.fromchat.utils

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import platform.UIKit.UIScrollView
import platform.UIKit.UIScrollViewKeyboardDismissMode
import platform.UIKit.UIView

private const val SCROLL_VIEW_PATCH_INTERVAL_MS = 150L
private const val SCROLL_VIEW_PATCH_MAX_ITERATIONS = 40

internal fun UIView.applyInteractiveKeyboardDismissRecursively() {
    if (this is UIScrollView) {
        keyboardDismissMode = UIScrollViewKeyboardDismissMode.UIScrollViewKeyboardDismissModeInteractive
    }
    for (sub in subviews) {
        (sub as? UIView)?.applyInteractiveKeyboardDismissRecursively()
    }
}

@Composable
internal fun IosInteractiveKeyboardDismissEffect() {
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    LaunchedEffect(imeBottom) {
        if (imeBottom <= 0) return@LaunchedEffect
        var iterations = 0
        while (isActive && iterations < SCROLL_VIEW_PATCH_MAX_ITERATIONS) {
            iosTopViewController()?.view?.applyInteractiveKeyboardDismissRecursively()
            delay(SCROLL_VIEW_PATCH_INTERVAL_MS)
            iterations++
        }
    }
}
