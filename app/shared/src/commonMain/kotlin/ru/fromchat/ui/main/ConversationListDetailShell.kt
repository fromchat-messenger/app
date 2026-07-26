package ru.fromchat.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.fromchat.ui.components.AppPanel
import ru.fromchat.ui.extraStatusBars

/**
 * Horizontal inset for chat detail content (messages, top-bar pills, input) in list–detail
 * so progressive chrome blur can breathe at the pane edges.
 */
val ConversationDetailContentPadding = 12.dp

/**
 * True while the conversation list–detail shell is showing. Chat chrome uses this for
 * pill-shaped top bars and edge-to-edge detail layout.
 */
val LocalConversationListDetailActive = staticCompositionLocalOf { false }

/**
 * List–detail shell. The list pane always uses [AppPanel] with full rounded corners
 * (including the edge facing the detail pane). Settings detail uses [AppPanel] too
 * ([detailInPanel]); chat detail is edge-to-edge ([detailEdgeToEdge]) so floating chrome
 * can blur past its inner padding toward the window.
 *
 * A small gutter separates panes so list-panel round corners stay visible.
 *
 * Pads with [WindowInsets.safeDrawing] ∪ [WindowInsets.extraStatusBars] so panes clear the
 * macOS title bar / system bars, then consumes those insets so child top bars do not double-pad
 * — except when [detailEdgeToEdge], where the detail column omits outer padding and keeps
 * insets available for its own chrome.
 *
 * Layout structure is stable regardless of [detailEdgeToEdge] / [detailInPanel] so detail
 * [androidx.compose.animation.AnimatedContent] is not remounted when empty ↔ chat.
 */
@Composable
fun ConversationListDetailShell(
    listPaneWidth: Dp,
    listPane: @Composable () -> Unit,
    detailPane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    detailInPanel: Boolean = false,
    detailEdgeToEdge: Boolean = false,
) {
    val paneInsets = WindowInsets.safeDrawing.union(WindowInsets.extraStatusBars)

    CompositionLocalProvider(LocalConversationListDetailActive provides true) {
        Row(modifier.fillMaxSize()) {
            AppPanel(
                Modifier
                    .windowInsetsPadding(paneInsets)
                    .consumeWindowInsets(paneInsets)
                    .padding(
                        start = 8.dp,
                        top = 8.dp,
                        bottom = 8.dp,
                    )
                    .width(listPaneWidth)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(24.dp),
            ) {
                listPane()
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (detailEdgeToEdge) {
                            Modifier
                        } else {
                            Modifier
                                .windowInsetsPadding(paneInsets)
                                .consumeWindowInsets(paneInsets)
                                .padding(
                                    top = 8.dp,
                                    end = 8.dp,
                                    bottom = 8.dp,
                                )
                        },
                    ),
            ) {
                if (detailInPanel) {
                    AppPanel(
                        Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        detailPane()
                    }
                } else {
                    detailPane()
                }
            }
        }
    }
}
