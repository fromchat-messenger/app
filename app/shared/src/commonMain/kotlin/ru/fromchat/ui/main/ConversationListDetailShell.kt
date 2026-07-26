package ru.fromchat.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ru.fromchat.ui.components.AppPanel
import ru.fromchat.ui.extraStatusBars

/** Minimum width to show chat + profile side-by-side beside the list. */
val ConversationProfileThirdPaneMinWidth = 1100.dp

private const val ProfilePaneAnimMs = 280

/**
 * List–detail (optional profile) shell. The list pane uses [AppPanel]; detail and profile
 * panes stay plain (no rounded surface wrapper). Spacing alone separates panes.
 *
 * Pads with [WindowInsets.safeDrawing] ∪ [WindowInsets.extraStatusBars] so panes clear the
 * macOS title bar / system bars, then consumes those insets so child top bars do not double-pad.
 * Window background stays edge-to-edge behind this layer.
 */
@Composable
fun ConversationListDetailShell(
    showProfilePane: Boolean,
    listPaneWidth: Dp,
    listPane: @Composable () -> Unit,
    detailPane: @Composable () -> Unit,
    profilePane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val profileVisible = showProfilePane && maxWidth >= ConversationProfileThirdPaneMinWidth
        val fadeAnim = tween<Float>(
            durationMillis = ProfilePaneAnimMs,
            easing = FastOutSlowInEasing,
        )
        val sizeAnim = tween<IntSize>(
            durationMillis = ProfilePaneAnimMs,
            easing = FastOutSlowInEasing,
        )
        val paneInsets = WindowInsets.safeDrawing.union(WindowInsets.extraStatusBars)

        Row(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(paneInsets)
                .consumeWindowInsets(paneInsets)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppPanel(
                Modifier
                    .width(listPaneWidth)
                    .fillMaxHeight(),
            ) {
                listPane()
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                detailPane()
            }
            AnimatedVisibility(
                visible = profileVisible,
                enter = fadeIn(animationSpec = fadeAnim) + expandHorizontally(
                    animationSpec = sizeAnim,
                    expandFrom = Alignment.End,
                    clip = false,
                ),
                exit = fadeOut(
                    animationSpec = tween(
                        durationMillis = 220,
                        easing = FastOutSlowInEasing,
                    ),
                ) + shrinkHorizontally(
                    animationSpec = sizeAnim,
                    shrinkTowards = Alignment.End,
                    clip = false,
                ),
            ) {
                Box(
                    Modifier
                        .widthIn(min = 320.dp, max = 420.dp)
                        .width(380.dp)
                        .fillMaxHeight(),
                ) {
                    profilePane()
                }
            }
        }
    }
}
