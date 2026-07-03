@file:Suppress("unused", "MemberVisibilityCanBePrivate", "UnusedReceiverParameter", "NOTHING_TO_INLINE")

package com.pr0gramm3r101.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults.cardColors
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.pr0gramm3r101.utils.conditional
import com.pr0gramm3r101.utils.currentWindowSize
import com.pr0gramm3r101.utils.invoke
import com.pr0gramm3r101.utils.left
import com.pr0gramm3r101.utils.link
import com.pr0gramm3r101.utils.plus
import com.pr0gramm3r101.utils.right
import com.pr0gramm3r101.utils.scaleOnPress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tech.annexflow.constraintlayout.compose.ConstraintLayout
import tech.annexflow.constraintlayout.compose.ConstraintLayoutScope
import tech.annexflow.constraintlayout.compose.Dimension
import kotlin.random.Random

val LocalDividerColor = compositionLocalOf<Color?> { null }
val LocalDividerThickness = compositionLocalOf<Dp?> { null }
val LocalBeforeDividerRadius = compositionLocalOf<Dp?> { null }
val LocalContainerColor = compositionLocalOf<Color?> { null }

enum class ListItemPosition {
    START,
    MIDDLE,
    END,
}

object ListItemDefaults {
    val dividerColor @Composable get() = DividerDefaults.color
    val thickness = DividerDefaults.Thickness
    val beforeDividerRadius = 8.dp
    val containerColor = Color.Transparent
}

@Composable
fun listItemClipShape(
    position: ListItemPosition,
    groupItemCount: Int? = null,
    cornerShape: CornerBasedShape = CategoryDefaults.shape,
    beforeDividerRadius: Dp = ListItemDefaults.beforeDividerRadius,
): Shape {
    val useGroupShape = position != ListItemPosition.MIDDLE || groupItemCount != null
    if (!useGroupShape) {
        return RoundedCornerShape(beforeDividerRadius)
    }
    val solo = groupItemCount == 1
    val roundTop = position == ListItemPosition.START || solo
    val roundBottom = position == ListItemPosition.END || solo
    val innerRadius = CornerSize(beforeDividerRadius)
    return RoundedCornerShape(
        topStart = if (roundTop) cornerShape.topStart else innerRadius,
        topEnd = if (roundTop) cornerShape.topEnd else innerRadius,
        bottomStart = if (roundBottom) cornerShape.bottomStart else innerRadius,
        bottomEnd = if (roundBottom) cornerShape.bottomEnd else innerRadius,
    )
}

fun listItemPositionInGroup(index: Int, count: Int): ListItemPosition {
    require(count > 0)
    return when {
        index == 0 -> ListItemPosition.START
        index == count - 1 -> ListItemPosition.END
        else -> ListItemPosition.MIDDLE
    }
}

class ListItemContextMenuScope internal constructor(
    private val dismiss: () -> Unit,
) {
    internal val items = mutableListOf<ListItemContextMenuEntry>()

    fun item(
        icon: ImageVector,
        label: String,
        onClick: () -> Unit,
    ) {
        items.add(ListItemContextMenuEntry(icon, label, onClick))
    }

    fun close() {
        dismiss()
    }
}

internal data class ListItemContextMenuEntry(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

private val listItemContextMenuShape = RoundedCornerShape(16.dp)
private val listItemContextMenuItemShape = RoundedCornerShape(12.dp)

@Composable
private fun ListItemContextMenuPopup(
    open: Boolean,
    position: IntOffset,
    anchorPositionInRoot: Offset,
    onDismiss: () -> Unit,
    menuContent: ListItemContextMenuScope.() -> Unit,
) {
    var shouldShowPopup by remember { mutableStateOf(open) }
    val animationProgress = remember { mutableFloatStateOf(0f) }
    val latestMenuContent by rememberUpdatedState(menuContent)
    val windowSize = currentWindowSize()
    val screenWidthPx = windowSize.width
    val screenHeightPx = windowSize.height

    LaunchedEffect(open) {
        if (!open) {
            animate(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) { value, _ ->
                animationProgress.floatValue = value
            }
            shouldShowPopup = false
        }
    }

    LaunchedEffect(open) {
        if (open) {
            shouldShowPopup = true
            animationProgress.floatValue = 0f
        }
    }

    if (!shouldShowPopup) return

    val scope = remember(onDismiss) { ListItemContextMenuScope(onDismiss) }
    scope.items.clear()
    latestMenuContent(scope)
    val items = scope.items.toList()
    if (items.isEmpty()) {
        if (open) onDismiss()
        return
    }

    var measuredSize by remember { mutableStateOf(IntSize.Zero) }

    SubcomposeLayout(Modifier.size(0.dp)) { _ ->
        val looseConstraints = Constraints(
            minWidth = 0,
            minHeight = 0,
            maxWidth = screenWidthPx,
            maxHeight = screenHeightPx,
        )
        val placeables = subcompose("measure") {
            ListItemContextMenuContent(
                items = items,
                animated = false,
                withShadow = false,
                modifier = Modifier.graphicsLayer(alpha = 0f),
            )
        }.map { it.measure(looseConstraints) }
        val p = placeables.firstOrNull()
        if (p != null && measuredSize == IntSize.Zero) {
            measuredSize = IntSize(p.width, p.height)
        }
        layout(0, 0) {
            placeables.forEach { it.placeRelative(-10000, -10000) }
        }
    }

    val density = LocalDensity.current
    val paddingPx = with(density) { 16.dp.toPx().toInt() }
    val rightEdge = screenWidthPx - paddingPx
    val bottomEdge = screenHeightPx - paddingPx

    val adjustedOffset = remember(
        measuredSize,
        position,
        anchorPositionInRoot,
        rightEdge,
        bottomEdge,
        paddingPx,
    ) {
        if (measuredSize == IntSize.Zero) {
            position
        } else {
            var screenX = anchorPositionInRoot.x.toInt() + position.x
            var screenY = anchorPositionInRoot.y.toInt() + position.y
            if (screenX + measuredSize.width > rightEdge) screenX = rightEdge - measuredSize.width
            if (screenY + measuredSize.height > bottomEdge) screenY = bottomEdge - measuredSize.height
            if (screenX < paddingPx) screenX = paddingPx
            if (screenY < paddingPx) screenY = paddingPx
            IntOffset(
                screenX - anchorPositionInRoot.x.toInt(),
                screenY - anchorPositionInRoot.y.toInt(),
            )
        }
    }

    val transformOriginX = if (measuredSize.width > 0) {
        ((position.x - adjustedOffset.x).toFloat() / measuredSize.width).coerceIn(0f, 1f)
    } else 0f
    val transformOriginY = if (measuredSize.height > 0) {
        ((position.y - adjustedOffset.y).toFloat() / measuredSize.height).coerceIn(0f, 1f)
    } else 0f

    LaunchedEffect(measuredSize) {
        if (measuredSize != IntSize.Zero) {
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) { value, _ ->
                animationProgress.floatValue = value
            }
        }
    }

    if (measuredSize == IntSize.Zero) return

    val scale = 0.5f + 0.5f * animationProgress.floatValue
    val alpha = animationProgress.floatValue

    Popup(
        onDismissRequest = onDismiss,
        alignment = Alignment.TopStart,
        offset = adjustedOffset,
        properties = PopupProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = false,
        ),
    ) {
        ListItemContextMenuContent(
            items = items,
            animated = true,
            scale = scale,
            alpha = alpha,
            transformOriginX = transformOriginX,
            transformOriginY = transformOriginY,
            onItemClick = { entry ->
                entry.onClick()
                onDismiss()
            },
        )
    }
}

@Composable
private fun ListItemContextMenuContent(
    items: List<ListItemContextMenuEntry>,
    animated: Boolean,
    withShadow: Boolean = true,
    scale: Float = 1f,
    alpha: Float = 1f,
    transformOriginX: Float = 0f,
    transformOriginY: Float = 0f,
    modifier: Modifier = Modifier,
    onItemClick: (ListItemContextMenuEntry) -> Unit = { it.onClick() },
) {
    val density = LocalDensity.current
    val shadowElevationPx = if (withShadow) with(density) { 12.dp.toPx() } else 0f
    val menuColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val baseModifier = modifier.width(IntrinsicSize.Max)
    val containerModifier = if (animated) {
        baseModifier.graphicsLayer(
            scaleX = scale,
            scaleY = scale,
            alpha = alpha,
            transformOrigin = TransformOrigin(transformOriginX, transformOriginY),
            shadowElevation = shadowElevationPx,
            shape = listItemContextMenuShape,
            clip = true,
        )
    } else {
        baseModifier.graphicsLayer(
            shadowElevation = shadowElevationPx,
            shape = listItemContextMenuShape,
            clip = true,
        )
    }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Box(modifier = containerModifier) {
            Box(modifier = Modifier.matchParentSize().background(menuColor, listItemContextMenuShape))
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items.forEach { entry ->
                    ListItemContextMenuRow(
                        icon = entry.icon,
                        label = entry.label,
                        onClick = { onItemClick(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ListItemContextMenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp)
            .clip(listItemContextMenuItemShape)
            .scaleOnPress(
                scale = 0.96f,
                onClick = onClick,
                indication = LocalIndication.current,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun ContextMenuPressable(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    openMenuOnTap: Boolean = false,
    pressScale: Float = 0.96f,
    onContextMenuOpen: (() -> Unit)? = null,
    contextMenu: ListItemContextMenuScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(modifier = modifier) { content() }
        return
    }

    var isPressed by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var menuPosition by remember { mutableStateOf(IntOffset.Zero) }
    var anchorPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    val latestContextMenu by rememberUpdatedState(contextMenu)
    val scaleTarget = if (isPressed && !menuOpen) pressScale else 1f
    val scale by animateFloatAsState(
        targetValue = scaleTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        visibilityThreshold = 0.001f,
        label = "contextMenuPressableScale",
    )

    Column(
        modifier = Modifier.onGloballyPositioned { coordinates ->
            anchorPositionInRoot = coordinates.positionInRoot()
        },
    ) {
        Box(
            modifier = modifier
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    transformOrigin = TransformOrigin.Center,
                )
                .pointerInput(latestContextMenu, openMenuOnTap) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            try {
                                awaitRelease()
                            } finally {
                                isPressed = false
                            }
                        },
                        onTap = {
                            if (openMenuOnTap) {
                                onContextMenuOpen?.invoke()
                                menuPosition = IntOffset.Zero
                                menuOpen = true
                            }
                        },
                        onLongPress = { localOffset ->
                            onContextMenuOpen?.invoke()
                            menuPosition = IntOffset(
                                localOffset.x.toInt(),
                                localOffset.y.toInt(),
                            )
                            menuOpen = true
                        },
                    )
                },
        ) {
            content()
        }

        ListItemContextMenuPopup(
            open = menuOpen,
            position = menuPosition,
            anchorPositionInRoot = anchorPositionInRoot,
            onDismiss = { menuOpen = false },
            menuContent = latestContextMenu,
        )
    }
}

@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    bodyModifier: Modifier = Modifier,
    headline: String,
    supportingText: String? = null,
    supportingSlot: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable ConstraintLayoutScope.() -> Unit)? = null,
    containerColor: Color = LocalContainerColor.current ?: ListItemDefaults.containerColor,
    enabled: Boolean = true,
    divider: Boolean = false,
    dividerColor: Color = LocalDividerColor.current ?: ListItemDefaults.dividerColor,
    dividerThickness: Dp = LocalDividerThickness.current ?: ListItemDefaults.thickness,
    dividerAnimated: Boolean = false,
    beforeDividerRadius: Dp = LocalBeforeDividerRadius.current ?: ListItemDefaults.beforeDividerRadius,
    position: ListItemPosition = ListItemPosition.MIDDLE,
    groupItemCount: Int? = null,
    onClick: (() -> Unit)? = null,
    bodyOnClick: (() -> Unit)? = null,
    leadingAndBodyShared: Boolean = false,
    bottomContent: (@Composable () -> Unit)? = null,
    onContextMenuOpen: (() -> Unit)? = null,
    contextMenu: (ListItemContextMenuScope.() -> Unit)? = null,
) {
    val showDivider = divider && position != ListItemPosition.END
    val clipShape = listItemClipShape(
        position = position,
        groupItemCount = groupItemCount,
        beforeDividerRadius = beforeDividerRadius,
    )
    var isPressed by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var menuPosition by remember { mutableStateOf(IntOffset.Zero) }
    var anchorPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    val latestContextMenu by rememberUpdatedState(contextMenu)
    val hasContextMenu = contextMenu != null
    val scaleTarget = if (isPressed && !menuOpen) 0.96f else 1f
    val scale by animateFloatAsState(
        targetValue = scaleTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        visibilityThreshold = 0.001f,
        label = "listItemScale",
    )

    Column(
        modifier = Modifier.onGloballyPositioned { coordinates ->
            anchorPositionInRoot = coordinates.positionInRoot()
        },
    ) {
        @Composable
        fun ProvideStyle(
            content: @Composable BoxScope?.() -> Unit
        ) {
            if (!enabled) {
                CompositionLocalProvider(
                    value = LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                ) {
                    Box(
                        modifier = Modifier.alpha(0.38f),
                        content = content
                    )
                }
            } else {
                content(null)
            }
        }

        ProvideStyle {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        transformOrigin = TransformOrigin.Center,
                    )
                    .conditional(hasContextMenu) {
                        pointerInput(latestContextMenu, onClick) {
                            detectTapGestures(
                                onPress = {
                                    isPressed = true
                                    try {
                                        awaitRelease()
                                    } finally {
                                        isPressed = false
                                    }
                                },
                                onTap = { onClick?.invoke() },
                                onLongPress = { localOffset ->
                                    onContextMenuOpen?.invoke()
                                    menuPosition = IntOffset(
                                        localOffset.x.toInt(),
                                        localOffset.y.toInt(),
                                    )
                                    menuOpen = true
                                },
                            )
                        }
                    },
            ) {
            ConstraintLayout(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(clipShape)
                    .background(containerColor)
                    .conditional(onClick != null && !hasContextMenu) {
                        clickable(onClick = onClick!!)
                    }
                    .then(modifier)
            ) {
                val (leading, listItem, trailing, btm) = createRefs()

                @Composable
                fun leadingBody() {
                    if (leadingContent != null) {
                        Box(
                            modifier = Modifier
                                .constrainAs(leading) {
                                    top link parent.top
                                    bottom link
                                            if (bottomContent != null) btm.top
                                            else parent.bottom
                                    left link parent.left
                                    right link listItem.left
                                }
                                .padding(
                                    start = 16.dp,
                                    top = 8.dp,
                                    bottom = 8.dp
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            leadingContent()
                        }
                    }

                    ListItem(
                        headlineContent = { Text(headline) },
                        supportingContent = {
                            when {
                                supportingSlot != null -> supportingSlot()
                                supportingText != null -> Text(supportingText)
                            }
                        },
                        modifier = Modifier
                            .constrainAs(listItem) {
                                top link parent.top
                                bottom link
                                    if (bottomContent != null) btm.top
                                    else parent.bottom
                                left link
                                    if (leadingContent != null) leading.right
                                    else parent.left
                                right link
                                    if (trailingContent != null) trailing.left
                                    else parent.right

                                width = Dimension.fillToConstraints
                            }
                            .conditional(bodyOnClick != null && !leadingAndBodyShared && !hasContextMenu) {
                                Modifier.clickable(onClick = bodyOnClick!!)
                            }
                            .then(bodyModifier),
                        colors = androidx.compose.material3.ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        )
                    )
                }

                if (leadingAndBodyShared) {
                    Row(
                        modifier = Modifier
                            .constrainAs(listItem) {
                                top link parent.top
                                bottom link
                                    if (bottomContent != null) btm.top
                                    else parent.bottom
                                left link parent.left
                                right link
                                    if (trailingContent != null) trailing.left
                                    else parent.right
                                width = Dimension.fillToConstraints
                            }
                            .conditional(bodyOnClick != null && !hasContextMenu) {
                                Modifier.clickable(onClick = bodyOnClick!!)
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        leadingBody()
                    }
                } else {
                    leadingBody()
                }

                if (trailingContent != null) {
                    ConstraintLayout(
                        modifier = Modifier
                            .constrainAs(trailing) {
                                top link parent.top
                                bottom link
                                        if (bottomContent != null) btm.top
                                        else parent.bottom
                                right link parent.right
                                left link listItem.right
                            }
                            .padding(
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 8.dp
                            ),
                        content = trailingContent
                    )
                }

                if (bottomContent != null) {
                    Box(
                        modifier = Modifier
                            .constrainAs(btm) {
                                bottom link parent.bottom
                                left link parent.left
                                right link parent.right
                                width = Dimension.fillToConstraints
                            }
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    ) {
                        bottomContent()
                    }
                }
            }
            }
        }

        when {
            dividerAnimated && showDivider -> {
                AnimatedVisibility(
                    visible = showDivider,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    HorizontalDivider(
                        color = dividerColor,
                        thickness = dividerThickness
                    )
                }
            }
            showDivider -> {
                HorizontalDivider(
                    color = dividerColor,
                    thickness = dividerThickness
                )
            }
        }

        if (hasContextMenu) {
            ListItemContextMenuPopup(
                open = menuOpen,
                position = menuPosition,
                anchorPositionInRoot = anchorPositionInRoot,
                onDismiss = { menuOpen = false },
                menuContent = { latestContextMenu?.invoke(this) },
            )
        }
    }
}

@Composable
inline fun SwitchListItem(
    modifier: Modifier = Modifier,
    headline: String,
    supportingText: String? = null,
    noinline leadingContent: (@Composable () -> Unit)? = null,
    checked: Boolean,
    noinline onCheckedChange: (Boolean) -> Unit,
    crossinline listItemOnClick: () -> Unit = { onCheckedChange(!checked) },
    divider: Boolean = false,
    dividerColor: Color = LocalDividerColor.current ?: DividerDefaults.color,
    dividerThickness: Dp = LocalDividerThickness.current ?: DividerDefaults.Thickness,
    dividerAnimated: Boolean = false,
    enabled: Boolean = true,
    position: ListItemPosition = ListItemPosition.MIDDLE,
    groupItemCount: Int? = null,
    noinline onContextMenuOpen: (() -> Unit)? = null,
    noinline contextMenu: (ListItemContextMenuScope.() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    ListItem(
        modifier = if (enabled) Modifier.clickable(
            interactionSource = interactionSource,
            indication = LocalIndication(),
            onClick = { listItemOnClick() }
        ) + modifier else modifier,
        headline = headline,
        supportingText = supportingText,
        leadingContent = leadingContent,
        trailingContent = {
            val (sw) = createRefs()
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                interactionSource = interactionSource,
                enabled = enabled,
                modifier = Modifier.constrainAs(sw) {
                    top link parent.top
                    bottom link parent.bottom
                    right link parent.right
                }
            )
        },
        divider = divider,
        dividerColor = dividerColor,
        dividerThickness = dividerThickness,
        dividerAnimated = dividerAnimated,
        enabled = enabled,
        position = position,
        groupItemCount = groupItemCount,
        onContextMenuOpen = onContextMenuOpen,
        contextMenu = contextMenu,
    )
}

@Composable
inline fun SeparatedSwitchListItem(
    modifier: Modifier = Modifier,
    bodyModifier: Modifier = Modifier,
    headline: String,
    supportingText: String? = null,
    noinline leadingContent: (@Composable () -> Unit)? = null,
    checked: Boolean,
    noinline onCheckedChange: (Boolean) -> Unit,
    noinline bodyOnClick: () -> Unit,
    divider: Boolean = false,
    dividerColor: Color = LocalDividerColor.current ?: DividerDefaults.color,
    dividerThickness: Dp = LocalDividerThickness.current ?: DividerDefaults.Thickness,
    dividerAnimated: Boolean = false,
    enabled: Boolean = true,
    position: ListItemPosition = ListItemPosition.MIDDLE,
    groupItemCount: Int? = null,
    noinline onContextMenuOpen: (() -> Unit)? = null,
    noinline contextMenu: (ListItemContextMenuScope.() -> Unit)? = null,
) {
    ListItem(
        modifier = modifier,
        bodyModifier = bodyModifier,
        bodyOnClick = bodyOnClick,
        headline = headline,
        supportingText = supportingText,
        leadingContent = leadingContent,
        trailingContent = {
            val (div, switch) = createRefs()
            VerticalDivider(
                modifier = Modifier
                    .padding()
                    .padding(end = 16.dp)
                    .constrainAs(div) {
                        top link parent.top
                        bottom link parent.bottom
                        left link parent.left
                        right link switch.left
                        height = Dimension.fillToConstraints
                    }
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.constrainAs(switch) {
                    top link parent.top
                    bottom link parent.bottom
                    right link parent.right
                    left link div.right
                },
                enabled = enabled
            )
        },
        divider = divider,
        dividerColor = dividerColor,
        dividerThickness = dividerThickness,
        dividerAnimated = dividerAnimated,
        leadingAndBodyShared = true,
        enabled = enabled,
        position = position,
        groupItemCount = groupItemCount,
        onContextMenuOpen = onContextMenuOpen,
        contextMenu = contextMenu,
    )
}

@Composable
fun SwitchCard(
    modifier: Modifier = Modifier,
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    cardOnClick: () -> Unit = { onCheckedChange(!checked) }
) {
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            + modifier,
        colors = cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(25.dp)
    ) {
        Box(
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = LocalIndication(),
                onClick = cardOnClick
            )
        ) {
            ConstraintLayout(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                val (txt, switch) = createRefs()
                Box(
                    modifier = Modifier.constrainAs(txt) {
                        top link parent.top
                        bottom link parent.bottom
                        left link parent.left
                        right link switch.left
                        width = Dimension.fillToConstraints
                        height = Dimension.fillToConstraints
                    },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = text,
                        fontSize = 20.sp
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier.constrainAs(switch) {
                        top link parent.top
                        bottom link parent.bottom
                        right link parent.right
                    },
                    interactionSource = interactionSource
                )
            }
        }
    }
}


// TODO fix
/*@Composable
inline fun SecureTextField(
    value: String,
    noinline onValueChange: (String) -> Unit,
    passwordHidden: Boolean,
    noinline visibilityOnClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = LocalTextStyle(),
    noinline label: @Composable (() -> Unit)? = null,
    noinline leadingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = TextFieldDefaults.shape,
    colors: TextFieldColors = TextFieldDefaults.colors()
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        visualTransformation =
        if (!passwordHidden)
            VisualTransformation.None
        else
            PasswordVisualTransformation(),
        label = label,
        trailingIcon = {
            IconButton(
                onClick = visibilityOnClick
            ) {
                val visibilityIcon =
                    if (passwordHidden) Icons.Filled.Visibility else
                        Icons.Filled.VisibilityOff
                // Provide localized description for accessibility services
                val description =
                    if (passwordHidden)
                        stringResource(R.string.show_pw)
                    else
                        stringResource(R.string.hide_pw)
                Icon(imageVector = visibilityIcon, contentDescription = description)
            }
        },
        modifier = modifier,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false
        ),
        maxLines = 1,
        singleLine = true,
        enabled = enabled,
        textStyle = textStyle,
        leadingIcon = leadingIcon,
        isError = isError,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        shape = shape,
        colors = colors
    )
}*/

@Composable
inline fun ToggleIconButton(
    checked: Boolean,
    crossinline onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.filledShape,
    checkedColors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    uncheckedColors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    noinline content: @Composable () -> Unit
) {
    if (checked) {
        FilledIconButton(
            onClick = { onCheckedChange(false) },
            modifier = Modifier.size(60.dp, 60.dp) + modifier,
            enabled = enabled,
            shape = shape,
            colors = checkedColors,
            interactionSource = interactionSource,
            content = content
        )
    } else {
        FilledTonalIconButton(
            onClick = { onCheckedChange(true) },
            modifier = Modifier.size(60.dp, 60.dp) + modifier,
            enabled = enabled,
            shape = shape,
            colors = uncheckedColors,
            interactionSource = interactionSource,
            content = content
        )
    }
}

@Composable
inline fun ElevatedButton(
    noinline onClick: () -> Unit,
    crossinline icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.elevatedShape,
    colors: ButtonColors = ButtonDefaults.elevatedButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.elevatedButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ButtonWithIconContentPadding,
    interactionSource: MutableInteractionSource? = null,
    crossinline content: @Composable RowScope.() -> Unit
) {
    ElevatedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource
    ) {
        Box(
            modifier = Modifier.size(18.dp)
        ) {
            icon()
        }
        Spacer(Modifier.width(8.dp))
        content()
    }
}

@Composable
inline fun Button(
    noinline onClick: () -> Unit,
    crossinline icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ButtonWithIconContentPadding,
    interactionSource: MutableInteractionSource? = null,
    crossinline content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource
    ) {
        Box(
            modifier = Modifier.size(18.dp)
        ) {
            icon()
        }
        Spacer(Modifier.width(8.dp))
        content()
    }
}

@Composable
inline fun FilledTonalButton(
    noinline onClick: () -> Unit,
    crossinline icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.filledTonalShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ButtonWithIconContentPadding,
    interactionSource: MutableInteractionSource? = null,
    crossinline content: @Composable RowScope.() -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource
    ) {
        Box(
            modifier = Modifier.size(18.dp)
        ) {
            icon()
        }
        Spacer(Modifier.width(8.dp))
        content()
    }
}

@Composable
inline fun TextButton(
    noinline onClick: () -> Unit,
    crossinline icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonWithIconContentPadding,
    interactionSource: MutableInteractionSource? = null,
    crossinline content: @Composable RowScope.() -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource
    ) {
        Box(
            modifier = Modifier.size(18.dp)
        ) {
            icon()
        }
        Spacer(Modifier.width(8.dp))
        content()
    }
}

class RadioButtonControllerScope<T> @PublishedApi internal constructor() {
    private val radioButtons = mutableMapOf<T, MutableState<Boolean>>()

    val checkedItemAsState: MutableState<T?> = mutableStateOf(null)

    private fun processCheckedChange(id: T) {
        val toModify = mutableListOf<T>()

        radioButtons.forEach { (i) ->
            if (i != id) {
                toModify.add(i)
            }
        }

        toModify.forEach { i ->
            radioButtons[i]!!.value = false
        }
    }

    fun addRadioButton(id: T, checked: MutableState<Boolean>, coroutineScope: CoroutineScope) {
        radioButtons[id] = checked
        coroutineScope.launch {
            snapshotFlow { checked.value }.collect {
                if (it) {
                    processCheckedChange(id)
                    checkedItemAsState.value = checkedItem
                }
            }
        }
    }

    fun addRadioButtons(
        vararg radioButtons: Pair<T, MutableState<Boolean>>,
        coroutineScope: CoroutineScope
    ) =
        radioButtons.forEach { (id, checked) ->
            addRadioButton(id, checked, coroutineScope)
        }

    fun removeRadioButton(id: T) = radioButtons.remove(id)

    fun isChecked(id: T) = radioButtons[id]!!.value
    val checkedItem: T?
        get() = radioButtons.firstNotNullOfOrNull { if (it.value.value) it.key else null }

    fun clearCheckedItems() = radioButtons.values.forEach { it.value = false }

    interface Id<T> {
        val id: T
    }

    class RandomIntId internal constructor(): Id<Int> {
        override val id = Random.nextInt()
    }

    class OrderedIntId internal constructor(override val id: Int): Id<Int>

    abstract class Ids<IdT: Id<T>, T> {
        abstract operator fun component1(): IdT
        abstract operator fun component2(): IdT
        abstract operator fun component3(): IdT
        abstract operator fun component4(): IdT
        abstract operator fun component5(): IdT
        abstract operator fun component6(): IdT
        abstract operator fun component7(): IdT
        abstract operator fun component8(): IdT
        abstract operator fun component9(): IdT
        abstract operator fun component10(): IdT
        abstract operator fun component11(): IdT
        abstract operator fun component12(): IdT
        abstract operator fun component13(): IdT
        abstract operator fun component14(): IdT
        abstract operator fun component15(): IdT
        abstract operator fun component16(): IdT
    }

    class OrderedIntIds internal constructor(): Ids<OrderedIntId, Int>() {
        override fun component1() = OrderedIntId(0)
        override fun component2() = OrderedIntId(1)
        override fun component3() = OrderedIntId(2)
        override fun component4() = OrderedIntId(3)
        override fun component5() = OrderedIntId(4)
        override fun component6() = OrderedIntId(5)
        override fun component7() = OrderedIntId(6)
        override fun component8() = OrderedIntId(7)
        override fun component9() = OrderedIntId(8)
        override fun component10() = OrderedIntId(9)
        override fun component11() = OrderedIntId(10)
        override fun component12() = OrderedIntId(11)
        override fun component13() = OrderedIntId(12)
        override fun component14() = OrderedIntId(13)
        override fun component15() = OrderedIntId(14)
        override fun component16() = OrderedIntId(15)
    }

    @Suppress("SameReturnValue")
    class Ints internal constructor() {
        operator fun component1() = 0
        operator fun component2() = 1
        operator fun component3() = 2
        operator fun component4() = 3
        operator fun component5() = 4
        operator fun component6() = 5
        operator fun component7() = 6
        operator fun component8() = 7
        operator fun component9() = 8
        operator fun component10() = 9
        operator fun component11() = 10
        operator fun component12() = 11
        operator fun component13() = 12
        operator fun component14() = 13
        operator fun component15() = 14
        operator fun component16() = 15
    }

    fun createRandomId() = RandomIntId()
    fun createRandomIds() = object: Ids<Id<Int>, Int>() {
        override operator fun component1() = RandomIntId()
        override operator fun component2() = RandomIntId()
        override operator fun component3() = RandomIntId()
        override operator fun component4() = RandomIntId()
        override operator fun component5() = RandomIntId()
        override operator fun component6() = RandomIntId()
        override operator fun component7() = RandomIntId()
        override operator fun component8() = RandomIntId()
        override operator fun component9() = RandomIntId()
        override operator fun component10() = RandomIntId()
        override operator fun component11() = RandomIntId()
        override operator fun component12() = RandomIntId()
        override operator fun component13() = RandomIntId()
        override operator fun component14() = RandomIntId()
        override operator fun component15() = RandomIntId()
        override operator fun component16() = RandomIntId()
    }

    fun createOrderedId(index: Int) = OrderedIntId(index)
    fun createOrderedIds() = OrderedIntIds()

    fun createIntIds() = Ints()
}

@Composable
inline fun <T> rememberRadioButtonController() = rememberSaveable { RadioButtonControllerScope<T>() }

@Composable
inline fun <T> RadioButtonController(
    content: @Composable RadioButtonControllerScope<T>.() -> Unit
) {
    content(RadioButtonControllerScope())
}

@Composable
inline fun CheckboxWithText(
    checked: Boolean,
    crossinline onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: CheckboxColors = CheckboxDefaults.colors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(contentPadding)
            +
            if (enabled)
                Modifier.toggleable(
                    value = checked,
                    onValueChange = { onCheckedChange(!checked) },
                    role = Role.Checkbox
                )
            else Modifier
            + modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = colors,
        )
        Spacer(modifier = Modifier.width(8.dp))
        content()
    }
}

@Composable
inline fun SwitchWithText(
    checked: Boolean,
    crossinline onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(contentPadding)
            +
            if (enabled)
                Modifier.toggleable(
                    value = checked,
                    onValueChange = { onCheckedChange(!checked) },
                    role = Role.Switch
                )
            else Modifier
            + modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
        Spacer(modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = colors,
        )
    }
}


@Composable
inline fun RadioButtonWithText(
    modifier: Modifier = Modifier,
    selected: Boolean,
    crossinline onSelectedChange: () -> Unit,
    enabled: Boolean = true,
    crossinline content: @Composable () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .selectable(
                selected = selected,
                onClick = {
                    if (enabled) onSelectedChange()
                },
                role = Role.RadioButton
            )
            .padding(horizontal = 16.dp)
            + modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null
        )
        Spacer(modifier = Modifier.width(16.dp))
        ProvideTextStyle(value = MaterialTheme.typography.bodyLarge) {
            content()
        }
    }
}

@Composable
inline fun SimpleAlertDialog(
    open: Boolean,
    crossinline onDismissRequest: () -> Unit,
    crossinline onPositiveButtonClick: (() -> Unit) = {},
    crossinline onNegativeButtonClick: (() -> Unit) = {},
    positiveButtonText: String? = null,
    negativeButtonText: String? = null,
    title: String,
    body: String? = null,
    dsaString: String? = null,
    crossinline onDsa: (() -> Unit) = {},
    noinline icon: @Composable (() -> Unit) = {},
) {
    if (open) {
        var dsaChecked by remember { mutableStateOf(false) }

        AlertDialog(
            icon = icon,
            title = {
                Text(text = title)
            },
            text =
            if (body != null || dsaString != null) {
                {
                    if (body != null) {
                        Text(text = body)
                    }
                    if (dsaString != null) {
                        CheckboxWithText(
                            checked = dsaChecked,
                            onCheckedChange = { dsaChecked = it },
                            contentPadding = PaddingValues(top = 10.dp)
                        ) {
                            Text(text = dsaString)
                        }
                    }
                }
            } else null,
            onDismissRequest = { onDismissRequest() },
            confirmButton = {
                if (!positiveButtonText.isNullOrBlank()) {
                    TextButton(
                        onClick = {
                            onPositiveButtonClick()
                            if (dsaChecked) onDsa()
                            onDismissRequest()
                        }
                    ) {
                        Text(positiveButtonText)
                    }
                }
            },
            dismissButton = if (!negativeButtonText.isNullOrBlank()) {
                {
                    TextButton(
                        onClick = {
                            onNegativeButtonClick()
                            if (dsaChecked) onDsa()
                            onDismissRequest()
                        }
                    ) {
                        Text(negativeButtonText)
                    }
                }
            } else null
        )
    }
}

@Composable
inline fun RadioGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.selectableGroup() + modifier) {
        content()
    }
}

@Composable
inline fun SwipeToDismissBackground(
    dismissState: SwipeToDismissBoxState,
    startToEndColor: Color = Color(0xFFFF1744),
    startToEndIcon: @Composable () -> Unit = {
        Icon(
            Icons.Default.Delete,
            contentDescription = "Delete"
        )
    },
    endToStartColor: Color = Color(0xFF1DE9B6),
    endToStartIcon: @Composable () -> Unit = {
        Icon(
            Icons.Filled.Archive,
            contentDescription = "Archive"
        )
    }
) {
    val color = when (dismissState.dismissDirection) {
        SwipeToDismissBoxValue.StartToEnd -> startToEndColor
        SwipeToDismissBoxValue.EndToStart -> endToStartColor
        SwipeToDismissBoxValue.Settled -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(color)
            .padding(12.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        startToEndIcon()
        Spacer(modifier = Modifier)
        endToStartIcon()
    }
}

