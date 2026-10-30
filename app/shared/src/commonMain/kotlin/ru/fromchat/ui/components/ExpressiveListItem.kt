package ru.fromchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ru.fromchat.utils.components.ListItem
import ru.fromchat.utils.components.ListItemContextMenuScope
import ru.fromchat.utils.components.ListItemPosition
import tech.annexflow.constraintlayout.compose.ConstraintLayoutScope

/**
 * A [ListItem] with the "expressive" settings-card look: a soft colored icon
 * chip (40dp tile, full-round corners) behind a 26dp glyph, on a
 * `surfaceBright` card. Everything else (press scaling, dividers, group
 * clipping, context menus) is inherited from the base [ListItem].
 *
 * @param icon The icon to draw inside the chip.
 * @param iconPreset The background + foreground (glyph) colors for the chip.
 */
@Composable
fun ExpressiveListItem(
    icon: ImageVector,
    headline: String,
    modifier: Modifier = Modifier,
    iconPreset: IconPreset = ExpressiveTints.Purple,
    supportingText: String? = null,
    trailingContent: (@Composable ConstraintLayoutScope.() -> Unit)? = null,
    enabled: Boolean = true,
    divider: Boolean = false,
    position: ListItemPosition = ListItemPosition.MIDDLE,
    groupItemCount: Int? = null,
    onClick: (() -> Unit)? = null,
    onContextMenuOpen: (() -> Unit)? = null,
    contextMenu: (ListItemContextMenuScope.() -> Unit)? = null,
) {
    ListItem(
        modifier = modifier,
        headline = headline,
        supportingText = supportingText,
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconPreset.background, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconPreset.foreground,
                    modifier = Modifier.size(26.dp),
                )
            }
        },
        trailingContent = trailingContent,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        enabled = enabled,
        divider = divider,
        position = position,
        groupItemCount = groupItemCount,
        onClick = onClick,
        onContextMenuOpen = onContextMenuOpen,
        contextMenu = contextMenu,
    )
}
