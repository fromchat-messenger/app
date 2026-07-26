package ru.fromchat.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ru.fromchat.ui.components.AppPanel
import ru.fromchat.ui.extraStatusBars

/** Minimum width to show chat + profile side-by-side beside the list. */
val ConversationProfileThirdPaneMinWidth = 1100.dp

/**
 * Horizontal inset for chat detail content (messages, top-bar pills, input) in list–detail
 * so progressive chrome blur can breathe at the pane edges while outer panes stay flush.
 */
val ConversationDetailContentPadding = 12.dp

private val PaneCornerRadius = 24.dp

/** Round only the outer (leading) corners — adjacent edge stays square so panes sit flush. */
private val ListPaneShape: Shape = RoundedCornerShape(
    topStart = PaneCornerRadius,
    bottomStart = PaneCornerRadius,
)

/** Round only the outer (trailing) corners for settings / profile detail panels. */
private val DetailPaneShape: Shape = RoundedCornerShape(
    topEnd = PaneCornerRadius,
    bottomEnd = PaneCornerRadius,
)

/**
 * True while the conversation list–detail shell is showing. Chat chrome uses this for
 * pill-shaped top bars and edge-to-edge detail layout.
 */
val LocalConversationListDetailActive = staticCompositionLocalOf { false }

private const val ProfilePaneAnimMs = 280

/**
 * List–detail (optional profile) shell. The list pane always uses [AppPanel].
 * Settings detail uses [AppPanel] too ([detailInPanel]); chat detail is edge-to-edge
 * ([detailEdgeToEdge]) so floating chrome can blur past its inner padding toward the window.
 *
 * Panes sit flush (no gutter) so the window background does not show as a vertical strip.
 *
 * Pads with [WindowInsets.safeDrawing] ∪ [WindowInsets.extraStatusBars] so panes clear the
 * macOS title bar / system bars, then consumes those insets so child top bars do not double-pad
 * — except when [detailEdgeToEdge], where the detail column omits outer padding and keeps
 * insets available for its own chrome.
 */
@Composable
fun ConversationListDetailShell(
    showProfilePane: Boolean,
    listPaneWidth: Dp,
    listPane: @Composable () -> Unit,
    detailPane: @Composable () -> Unit,
    profilePane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    detailInPanel: Boolean = false,
    detailEdgeToEdge: Boolean = false,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val profileVisible = showProfilePane && maxWidth >= ConversationProfileThirdPaneMinWidth
        val paneInsets = WindowInsets.safeDrawing.union(WindowInsets.extraStatusBars)

        CompositionLocalProvider(LocalConversationListDetailActive provides true) {
            if (detailEdgeToEdge) {
                Row(Modifier.fillMaxSize()) {
                    AppPanel(
                        Modifier
                            .windowInsetsPadding(paneInsets)
                            .consumeWindowInsets(paneInsets)
                            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp)
                            .width(listPaneWidth)
                            .fillMaxHeight(),
                        shape = ListPaneShape,
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
                    ProfilePaneSlot(
                        visible = profileVisible,
                        modifier = Modifier
                            .windowInsetsPadding(paneInsets)
                            .consumeWindowInsets(paneInsets)
                            .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                        content = profilePane,
                    )
                }
            } else {
                Row(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(paneInsets)
                        .consumeWindowInsets(paneInsets)
                        .padding(8.dp),
                ) {
                    AppPanel(
                        Modifier
                            .width(listPaneWidth)
                            .fillMaxHeight(),
                        shape = ListPaneShape,
                    ) {
                        listPane()
                    }
                    if (detailInPanel) {
                        AppPanel(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            shape = if (profileVisible) RectangleShape else DetailPaneShape,
                        ) {
                            detailPane()
                        }
                    } else {
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            detailPane()
                        }
                    }
                    ProfilePaneSlot(
                        visible = profileVisible,
                        content = profilePane,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfilePaneSlot(
    visible: Boolean,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fadeAnim = tween<Float>(
        durationMillis = ProfilePaneAnimMs,
        easing = FastOutSlowInEasing,
    )
    val sizeAnim = tween<IntSize>(
        durationMillis = ProfilePaneAnimMs,
        easing = FastOutSlowInEasing,
    )
    AnimatedVisibility(
        visible = visible,
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
            modifier
                .widthIn(min = 320.dp, max = 420.dp)
                .width(380.dp)
                .fillMaxHeight(),
        ) {
            content()
        }
    }
}
