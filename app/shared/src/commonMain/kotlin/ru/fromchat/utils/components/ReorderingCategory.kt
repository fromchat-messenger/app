@file:Suppress("UnusedReceiverParameter", "NOTHING_TO_INLINE")

package ru.fromchat.utils.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import ru.fromchat.utils.conditional

@Composable
private fun ProvideReorderingCategoryListItemTheme(
    containerColor: Color = CategoryDefaults.containerColor,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalDividerColor provides Color.Transparent,
        LocalDividerThickness provides CategoryDefaults.dividerThickness,
        LocalContainerColor provides containerColor,
        content = content,
    )
}

private fun reorderingCategoryListItemPosition(index: Int, itemCount: Int): ListItemPosition =
    when {
        itemCount == 1 -> ListItemPosition.START
        index == 0 -> ListItemPosition.START
        index == itemCount - 1 -> ListItemPosition.END
        else -> ListItemPosition.MIDDLE
    }

@Composable
private fun reorderingCategoryGroupedItemShape(
    index: Int,
    itemCount: Int,
    roundedCorners: Boolean,
): Shape {
    if (!roundedCorners) {
        return RoundedCornerShape(0.dp)
    }
    val groupShape = CategoryDefaults.shape
    return when {
        itemCount == 1 -> groupShape
        index == 0 -> RoundedCornerShape(
            topStart = groupShape.topStart,
            topEnd = groupShape.topEnd,
            bottomStart = CornerSize(0.dp),
            bottomEnd = CornerSize(0.dp),
        )
        index == itemCount - 1 -> RoundedCornerShape(
            topStart = CornerSize(0.dp),
            topEnd = CornerSize(0.dp),
            bottomStart = groupShape.bottomStart,
            bottomEnd = groupShape.bottomEnd,
        )
        else -> RoundedCornerShape(0.dp)
    }
}

/**
 * Same list-item theme contract as [Category] (divider color/thickness, container color), but each
 * row is a separate lazy item with [Modifier.animateItem] for reorder. No group [Card] — only rows
 * paint a surface; outer [Box] clips like [LazyListScope.Category].
 */
@ExperimentalFoundationApi
fun <T> LazyListScope.ReorderingCategory(
    items: List<T>,
    key: (T) -> Any,
    keyPrefix: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    margin: PaddingValues = CategoryDefaults.margin,
    containerColor: Color? = null,
    roundedCorners: Boolean = true,
    itemContent: @Composable LazyItemScope.(
        index: Int,
        item: T,
        position: ListItemPosition,
        itemCount: Int,
    ) -> Unit,
) {
    if (items.isEmpty()) return

    item(key = "$keyPrefix-top-spacer") {
        Spacer(Modifier.height(margin.calculateTopPadding()))
    }

    if (title != null) {
        item(key = "$keyPrefix-title") {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(
                    bottom = 10.dp,
                    start = margin.calculateStartPadding(LocalLayoutDirection.current) + 16.dp,
                    end = margin.calculateEndPadding(LocalLayoutDirection.current) + 16.dp,
                ),
            )
        }
    }

    val resolvedContainerColor = containerColor
    itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
        ProvideReorderingCategoryListItemTheme(
            containerColor = resolvedContainerColor ?: CategoryDefaults.containerColor,
        ) {
            Box(
                modifier = modifier
                    .animateItem()
                    .padding(
                        start = margin.calculateStartPadding(LocalLayoutDirection.current),
                        end = margin.calculateEndPadding(LocalLayoutDirection.current),
                        top = if (index == 0 && margin.calculateTopPadding() == 0.dp) {
                            8.dp
                        } else {
                            0.dp
                        },
                    )
                    .conditional(roundedCorners) {
                        clip(reorderingCategoryGroupedItemShape(index, items.size, roundedCorners = true))
                    },
            ) {
                itemContent(
                    index,
                    item,
                    reorderingCategoryListItemPosition(index, items.size),
                    items.size,
                )
            }
        }
    }

    item(key = "$keyPrefix-bottom-spacer") {
        Spacer(Modifier.height(margin.calculateBottomPadding()))
    }
}