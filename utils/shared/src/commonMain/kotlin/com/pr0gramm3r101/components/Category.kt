@file:Suppress("UnusedReceiverParameter", "NOTHING_TO_INLINE")

package com.pr0gramm3r101.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults.cardColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pr0gramm3r101.utils.conditional

object CategoryDefaults {
    val margin = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp)
    val containerColor @Composable get() = MaterialTheme.colorScheme.surfaceContainer
    val dividerThickness: Dp = 3.dp
    val shape @Composable get() = MaterialTheme.shapes.extraLarge
}

@Composable
private fun CategoryBase(
    modifier: Modifier = Modifier,
    title: String? = null,
    margin: PaddingValues = CategoryDefaults.margin,
    containerColor: Color = CategoryDefaults.containerColor,
    backgroundColor: Color = Color.Transparent,
    roundedCorners: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    CompositionLocalProvider(
        LocalDividerColor provides Color.Transparent,
        LocalDividerThickness provides CategoryDefaults.dividerThickness,
        LocalContainerColor provides containerColor
    ) {
        if (title != null) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(
                    bottom = 10.dp,
                    start = margin.calculateStartPadding(
                        LocalLayoutDirection.current
                    ) + 16.dp,
                    end = margin.calculateEndPadding(
                        LocalLayoutDirection.current,
                    ) + 16.dp
                )
            )
        }

        Card(
            modifier = modifier
                .padding(margin)
                .fillMaxWidth(),
            shape = if (roundedCorners) CategoryDefaults.shape else RoundedCornerShape(0.dp),
            colors = cardColors(containerColor = backgroundColor),
            content = content
        )
    }
}

@Composable
fun LazyItemScope.Category(
    modifier: Modifier = Modifier,
    title: String? = null,
    margin: PaddingValues = CategoryDefaults.margin,
    containerColor: Color = CategoryDefaults.containerColor,
    backgroundColor: Color = Color.Transparent,
    roundedCorners: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    CategoryBase(
        modifier = modifier,
        title = title,
        margin = margin,
        containerColor = containerColor,
        backgroundColor = backgroundColor,
        roundedCorners = roundedCorners,
        content = content
    )
}

@Composable
fun ColumnScope.Category(
    modifier: Modifier = Modifier,
    title: String? = null,
    margin: PaddingValues = CategoryDefaults.margin,
    containerColor: Color = CategoryDefaults.containerColor,
    backgroundColor: Color = Color.Transparent,
    roundedCorners: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    CategoryBase(
        modifier = modifier,
        title = title,
        margin = margin,
        containerColor = containerColor,
        backgroundColor = backgroundColor,
        roundedCorners = roundedCorners,
        content = content
    )
}

class CategoryScope {
    internal val items = mutableListOf<@Composable () -> Unit>()

    fun item(content: @Composable () -> Unit) {
        items.add(content)
    }
}

fun LazyListScope.Category(
    modifier: Modifier = Modifier,
    title: String? = null,
    margin: PaddingValues = CategoryDefaults.margin,
    containerColor: Color? = null,
    roundedCorners: Boolean = true,
    content: CategoryScope.() -> Unit
) {
    val scope = CategoryScope().apply(content)
    val items = scope.items

    item {
        Spacer(Modifier.height(margin.calculateTopPadding()))
    }

    if (title != null) {
        item {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(
                    bottom = 10.dp,
                    start = margin.calculateStartPadding(LocalLayoutDirection.current) + 16.dp,
                    end = margin.calculateEndPadding(LocalLayoutDirection.current) + 16.dp
                )
            )
        }
    }

    items.forEachIndexed { index, composableItem ->
        item {
            CompositionLocalProvider(
                LocalDividerColor provides Color.Transparent,
                LocalDividerThickness provides CategoryDefaults.dividerThickness,
                LocalContainerColor provides (containerColor ?: CategoryDefaults.containerColor)
            ) {
                Box(
                    modifier = modifier
                        .padding(
                            start = margin.calculateStartPadding(LocalLayoutDirection.current),
                            end = margin.calculateEndPadding(LocalLayoutDirection.current)
                        )
                        .conditional(roundedCorners) {
                            clip(
                                RoundedCornerShape(
                                    topStart =
                                        if (index == 0)
                                            CategoryDefaults.shape.topStart
                                        else CornerSize(0.dp),
                                    topEnd =
                                        if (index == 0)
                                            CategoryDefaults.shape.topEnd
                                        else CornerSize(0.dp),
                                    bottomStart =
                                        if (index == items.lastIndex)
                                            CategoryDefaults.shape.bottomStart
                                        else CornerSize(0.dp),
                                    bottomEnd =
                                        if (index == items.lastIndex)
                                            CategoryDefaults.shape.bottomEnd
                                        else CornerSize(0.dp)
                                )
                            )
                        }
                ) {
                    composableItem()
                }
            }
        }
    }

    item {
        Spacer(Modifier.height(margin.calculateBottomPadding()))
    }
}