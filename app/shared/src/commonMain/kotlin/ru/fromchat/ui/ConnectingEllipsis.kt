package ru.fromchat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Animated "..." that grows to three dots, clears, and repeats. Uses [LocalTextStyle] when
 * [fontSize] / [color] are left default so it matches surrounding typography.
 */
@Composable
fun ConnectingEllipsis(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = LocalTextStyle.current.fontSize,
    color: Color = LocalTextStyle.current.color,
    baseStyle: TextStyle = LocalTextStyle.current,
    stepMs: Long = 440,
) {
    val merged = remember(fontSize, color, baseStyle) {
        baseStyle.merge(TextStyle(fontSize = fontSize, color = color))
    }
    var visibleDots by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (isActive) {
            for (n in 1..3) {
                visibleDots = n
                delay(stepMs)
            }
            visibleDots = 0
            delay(stepMs / 2)
        }
    }
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        repeat(3) { i ->
            AnimatedVisibility(
                visible = i < visibleDots,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(text = ".", style = merged)
            }
        }
    }
}
