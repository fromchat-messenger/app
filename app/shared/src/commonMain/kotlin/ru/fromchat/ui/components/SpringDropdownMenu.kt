package ru.fromchat.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import ru.fromchat.ui.components.BackHandler
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ru.fromchat.utils.currentWindowSize
import ru.fromchat.utils.scaleOnPress

internal val springDropdownMenuShape = RoundedCornerShape(16.dp)
private val springDropdownItemShape = RoundedCornerShape(12.dp)
private val springDropdownEnterSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow,
)
private val menuChromePadding = 4.dp

/** ⋮ overflow anchored in chrome; menu morphs on [SpringOverflowMenuOverlayHost] (top-end spring scale). */
@Composable
fun SpringExpandingOverflowMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    buttonContainerColor: Color? = null,
    buttonContentColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val chromeColors = chromeFilledIconButtonColors()
    val resolvedButtonContainer = buttonContainerColor ?: chromeColors.containerColor
    val resolvedButtonContent = buttonContentColor ?: chromeColors.contentColor
    val density = LocalDensity.current
    val progress = remember { Animatable(0f) }
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    var triggerSize by remember { mutableStateOf(IntSize.Zero) }
    var anchorTopRightInRoot by remember { mutableStateOf(Offset.Zero) }
    val overlay = LocalSpringOverflowMenuOverlayController.current
    val maxMenuWidthPx = with(density) {
        val screenW = currentWindowSize().width
        minOf(screenW - 32.dp.roundToPx(), 320.dp.roundToPx())
    }

    SubcomposeLayout(Modifier.size(0.dp)) { _ ->
        val triggerPlaceable = subcompose("trigger") {
            ChromeOverflowIconButton(onClick = {})
        }.firstOrNull()?.measure(Constraints())
        if (triggerPlaceable != null && triggerPlaceable.width > 0 && triggerPlaceable.height > 0) {
            val trigger = IntSize(triggerPlaceable.width, triggerPlaceable.height)
            if (trigger != triggerSize) triggerSize = trigger
        }

        val menuPlaceable = subcompose("measure") {
            SpringOverflowMenuPanel(
                content = content,
                fixedSize = menuSize.takeIf { it != IntSize.Zero },
            )
        }.firstOrNull()
        val measured = menuPlaceable?.measure(
            if (menuSize != IntSize.Zero) {
                Constraints.fixed(menuSize.width, menuSize.height)
            } else {
                Constraints(
                    minWidth = 0,
                    maxWidth = maxMenuWidthPx,
                    minHeight = 0,
                    maxHeight = Constraints.Infinity,
                )
            },
        )
        if (measured != null && measured.width > 0 && measured.height > 0) {
            val size = IntSize(measured.width, measured.height)
            if (size != menuSize) menuSize = size
        }
        layout(0, 0) {}
    }

    LaunchedEffect(expanded) {
        if (menuSize == IntSize.Zero) return@LaunchedEffect
        when {
            expanded -> {
                progress.snapTo(0f)
                progress.animateTo(1f, springDropdownEnterSpring)
            }
            progress.value > 0f -> progress.animateTo(0f, springDropdownEnterSpring)
        }
    }

    val p = progress.value
    val chipAlpha = (1f - p * 1.35f).coerceIn(0f, 1f)
    val morphing = expanded || p > 0.001f
    val fallbackCollapsePx = with(density) { 40.dp.roundToPx() }
    val collapseW = when {
        triggerSize.width > 1 -> triggerSize.width
        else -> fallbackCollapsePx
    }
    val collapseH = when {
        triggerSize.height > 1 -> triggerSize.height
        else -> fallbackCollapsePx
    }
    val startW = collapseW.coerceAtLeast(1)
    val startH = collapseH.coerceAtLeast(1)

    BackHandler(enabled = expanded) { onExpandedChange(false) }

    Box(
        modifier = modifier
            .wrapContentSize(Alignment.TopEnd)
            .onGloballyPositioned { coordinates ->
                val root = coordinates.positionInRoot()
                anchorTopRightInRoot = Offset(
                    x = root.x + coordinates.size.width,
                    y = root.y,
                )
                val size = coordinates.size
                if (size.width > 0 && size.height > 0) {
                    triggerSize = size
                }
            },
        contentAlignment = Alignment.TopEnd,
    ) {
        ChromeOverflowIconButton(
            onClick = { if (!expanded) onExpandedChange(true) },
            enabled = !expanded,
            modifier = Modifier.graphicsLayer {
                alpha = if (expanded) chipAlpha else 1f
            },
            colors = chromeFilledIconButtonColors(
                containerColor = resolvedButtonContainer,
                contentColor = resolvedButtonContent,
            ),
        )
    }

    DisposableEffect(overlay) {
        onDispose { overlay?.clear() }
    }

    if (overlay != null) {
        SideEffect {
            if (morphing && menuSize != IntSize.Zero) {
                overlay.publish(
                    SpringOverflowMenuOverlaySlot(
                        anchorTopRightInRoot = anchorTopRightInRoot,
                        onDismiss = { onExpandedChange(false) },
                        menuSize = menuSize,
                        startW = startW,
                        startH = startH,
                        progress = p,
                        containerColor = containerColor,
                        content = content,
                    ),
                )
            } else {
                overlay.clear()
            }
        }
    }
}

@Composable
internal fun SpringOverflowMenuPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
    fixedSize: IntSize? = null,
) {
    val widthMod = if (fixedSize != null && fixedSize.width > 0) {
        Modifier.size(
            width = with(LocalDensity.current) { fixedSize.width.toDp() },
            height = with(LocalDensity.current) { fixedSize.height.toDp() },
        )
    } else {
        Modifier.width(IntrinsicSize.Max)
    }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            modifier = modifier.then(widthMod).padding(menuChromePadding),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

@Composable
fun SpringDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    contentColor: Color = LocalContentColor.current,
) {
    val itemAlpha = if (enabled) 1f else 0.38f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                clip = true
                shape = springDropdownItemShape
                alpha = itemAlpha
            }
            .scaleOnPress(
                scale = 0.96f,
                onClick = if (enabled) onClick else null,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leadingIcon?.invoke()
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    text()
                }
                trailingIcon?.invoke()
            }
        }
    }
}
