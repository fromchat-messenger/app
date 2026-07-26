package ru.fromchat.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * When non-null, [AppPanel] is a haze source so overlays (chat context-menu blur) include
 * panel chrome / rounded outlines. Provide above the panel (list–detail); null in compact.
 */
val LocalPaneHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Rounded lowest-surface container for list–detail panes and other full-area content.
 * Size with [modifier]; content fills the panel.
 */
@Composable
fun AppPanel(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val paneHazeState = LocalPaneHazeState.current
    Surface(
        modifier = modifier.then(
            if (paneHazeState != null) {
                Modifier.hazeSource(paneHazeState)
            } else {
                Modifier
            },
        ),
        shape = shape,
        color = color,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            content = content,
        )
    }
}
