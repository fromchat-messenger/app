package ru.fromchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.pr0gramm3r101.components.ListItem
import com.pr0gramm3r101.components.ListItemContextMenuScope
import com.pr0gramm3r101.components.ListItemPosition
import tech.annexflow.constraintlayout.compose.ConstraintLayoutScope

/**
 * A [ListItem] with the "expressive" settings-card look: a soft colored icon
 * chip (40dp tile, 24dp corners) behind a 26dp saturated icon, on a
 * `surfaceBright` card. Everything else (press scaling, dividers, group
 * clipping, context menus) is inherited from the base [ListItem].
 *
 * @param icon The icon to draw inside the chip.
 * @param iconTint Background color of the icon chip.
 * @param iconSubTint Color of the icon glyph drawn on the chip.
 */
@Composable
fun ExpressiveListItem(
    icon: ImageVector,
    iconTint: Color = ExpressiveTints.LightBlue,
    iconSubTint: Color = ExpressiveTints.Blue,
    headline: String,
    supportingText: String? = null,
    modifier: Modifier = Modifier,
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
                    .background(
                        iconTint.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(24.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconSubTint,
                    modifier = Modifier.size(26.dp),
                )
            }
        },
        trailingContent = trailingContent,
        containerColor = MaterialTheme.colorScheme.surfaceBright,
        enabled = enabled,
        divider = divider,
        position = position,
        groupItemCount = groupItemCount,
        onClick = onClick,
        onContextMenuOpen = onContextMenuOpen,
        contextMenu = contextMenu,
    )
}
