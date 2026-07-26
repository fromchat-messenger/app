package ru.fromchat.ui.main

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ru.fromchat.ui.components.AppPanel
import ru.fromchat.ui.extraStatusBars
import com.pr0gramm3r101.utils.settings.Settings as PlatformSettings

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
 * A small gutter separates panes so list-panel round corners stay visible. Drag that gap
 * to resize the list; the remembered width is stored in desktop prefs
 * (`desktop_list_pane_width`) and does not jump when adaptive window size classes change.
 * While the gap is hovered or dragged it stays transparent so right-pane blur can extend
 * into it.
 *
 * Pads with [WindowInsets.safeDrawing] ∪ [WindowInsets.extraStatusBars] so panes clear the
 * macOS title bar / system bars, then consumes those insets so child top bars do not double-pad
 * — except when [detailEdgeToEdge], where the detail column omits outer padding and keeps
 * insets available for its own chrome.
 *
 * Layout structure is stable regardless of [detailEdgeToEdge] / [detailInPanel] so detail
 * [androidx.compose.animation.AnimatedContent] is not remounted when empty ↔ chat.
 *
 * The detail slot fills immediately with [androidx.compose.material3.ColorScheme.background]
 * when the window grows (avoids a white/unpainted flash in the new strip). Detail content
 * width springs toward the slot size so continuous resize eases instead of jumping.
 */
@Composable
fun ConversationListDetailShell(
    listPane: @Composable () -> Unit,
    detailPane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    detailInPanel: Boolean = false,
    detailEdgeToEdge: Boolean = false,
) {
    val density = LocalDensity.current
    var storedListWidth by remember { mutableStateOf(ConversationListPanePrefs.load()) }
    var gapDragging by remember { mutableStateOf(false) }
    val gapInteractionSource = remember { MutableInteractionSource() }
    val gapHovered by gapInteractionSource.collectIsHoveredAsState()
    val gapActive = gapHovered || gapDragging

    val paneInsets = WindowInsets.safeDrawing.union(WindowInsets.extraStatusBars)
    val shellBackground = MaterialTheme.colorScheme.background

    CompositionLocalProvider(LocalConversationListDetailActive provides true) {
        BoxWithConstraints(
            modifier
                .fillMaxSize()
                .background(shellBackground),
        ) {
            // List start padding (8.dp) + gap (8.dp) reserved outside the panel width.
            val maxListWidth =
                (maxWidth - 8.dp - 8.dp - 320.dp).coerceAtLeast(280.dp)
            val listWidth = storedListWidth.coerceIn(280.dp, maxListWidth)

            Row(Modifier.fillMaxSize()) {
                AppPanel(
                    Modifier
                        .windowInsetsPadding(paneInsets)
                        .consumeWindowInsets(paneInsets)
                        .padding(
                            start = 8.dp,
                            top = 8.dp,
                            bottom = 8.dp,
                        )
                        .width(listWidth)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    listPane()
                }
                // Detail fills the remainder and draws under the leading gap so chrome blur
                // can extend into it when the gap strip is transparent.
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(shellBackground)
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
                    val detailWidth by animateDpAsState(
                        targetValue = maxWidth,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        label = "conversationDetailPaneWidth",
                    )
                    Box(
                        Modifier
                            .width(detailWidth.coerceAtMost(maxWidth))
                            .fillMaxHeight()
                            .align(Alignment.CenterStart),
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
                    Box(
                        Modifier
                            .width(8.dp)
                            .fillMaxHeight()
                            .align(Alignment.CenterStart)
                            .zIndex(1f)
                            .background(if (gapActive) Color.Transparent else shellBackground)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .hoverable(gapInteractionSource)
                            .pointerInput(maxListWidth, density) {
                                var dragWidth = storedListWidth
                                var dragged = false
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        gapDragging = true
                                        dragged = false
                                        dragWidth =
                                            storedListWidth.coerceIn(280.dp, maxListWidth)
                                    },
                                    onDragEnd = {
                                        gapDragging = false
                                        if (dragged) {
                                            ConversationListPanePrefs.save(dragWidth)
                                        }
                                    },
                                    onDragCancel = { gapDragging = false },
                                    onHorizontalDrag = { _, dragAmount ->
                                        val delta = with(density) { dragAmount.toDp() }
                                        dragged = true
                                        dragWidth =
                                            (dragWidth + delta).coerceIn(280.dp, maxListWidth)
                                        storedListWidth = dragWidth
                                    },
                                )
                            },
                    )
                }
            }
        }
    }
}

/**
 * Persists list–detail left pane width in the same JVM prefs node as window size
 * (`ru.fromchat.settings` / [PlatformSettings]).
 */
private object ConversationListPanePrefs {
    private const val WIDTH_KEY = "desktop_list_pane_width"

    private val settings = PlatformSettings()

    fun load(): Dp {
        val stored = runBlocking { settings.getFloat(WIDTH_KEY, 0f) }
        return if (stored <= 0f) 360.dp else stored.dp
    }

    fun save(width: Dp) {
        CoroutineScope(Dispatchers.IO).launch {
            settings.putFloat(WIDTH_KEY, width.value)
        }
    }
}
