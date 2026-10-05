package ru.fromchat.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Word-by-word staggered reveal for the app bar title. Each word fades and
 * rises in with a small per-word delay, giving the deliberate "reflow" feel.
 * Re-runs whenever [text] changes.
 */
@Composable
private fun AnimatedWordTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    val words = remember(text) { text.split(" ") }
    var generation by remember { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        generation++
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        words.forEachIndexed { index, word ->
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(generation) {
                visible = false
                delay((index * 45).milliseconds)
                visible = true
            }
            val alpha by animateFloatAsState(
                targetValue = if (visible) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 240f),
                label = "pTitleWordAlpha$index",
            )
            Box(
                modifier = Modifier.alpha(alpha),
            ) {
                Text(
                    text = word,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (index < words.lastIndex) {
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

/**
 * A Pixel-style compact top app bar: an app "seed" chip on the leading edge
 * (replacing the old back-arrow slot), a word-by-word animated title, and
 * optional trailing actions. Intentionally **not** the stock M3 [TopAppBar] —
 * the title refills via the staggered [AnimatedWordTitle].
 *
 * @param title The plain title text (split into words for the reveal).
 * @param topPadding Top padding to apply above the bar (status-bar insets, etc).
 * @param leadingIcon Icon drawn in the round chip (defaults to a menu icon).
 * @param onLeadingAction Invoked when the leading chip is tapped (optional).
 * @param trailingActions Trailing action slot.
 */
@Composable
fun PixelTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    leadingIcon: ImageVector = Icons.Default.Menu,
    leadingTint: Color = MaterialTheme.colorScheme.surfaceBright,
    leadingSubTint: Color = MaterialTheme.colorScheme.onSurface,
    onLeadingAction: (() -> Unit)? = null,
    trailingActions: @Composable RowScope.() -> Unit = {},
    containerColor: Color = Color.Transparent,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topPadding),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .height(64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onLeadingAction != null) {
                        IconButton(
                            onClick = onLeadingAction,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(leadingTint, shape = CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = leadingIcon,
                                    contentDescription = null,
                                    tint = leadingSubTint,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                        }
                    }
                    AnimatedWordTitle(
                        text = title,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                    )
                    trailingActions()
                }
            }
        },
    )
}
