package ru.fromchat.ui.chat.message

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val BUBBLE_RADIUS_LARGE = 20.dp
internal val BUBBLE_RADIUS_SMALL = 4.dp

internal data class AnimatedBubbleRadii(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomStart: Dp,
    val bottomEnd: Dp,
)

@Composable
internal fun rememberAnimatedBubbleRadii(
    isAuthor: Boolean,
    group: MessageGroupInfo,
): AnimatedBubbleRadii {
    val large = BUBBLE_RADIUS_LARGE
    val small = BUBBLE_RADIUS_SMALL

    // Spec (outgoing / bubble-local): start always large; end depends on neighbors.
    // Incoming: mirror so the tail sits on the screen-edge (start) side.
    val topStartTarget: Dp
    val topEndTarget: Dp
    val bottomStartTarget: Dp
    val bottomEndTarget: Dp
    if (isAuthor) {
        topStartTarget = large
        bottomStartTarget = large
        topEndTarget = if (group.hasSameAuthorAbove) small else large
        bottomEndTarget = if (group.hasSameAuthorBelow) small else large
    } else {
        topEndTarget = large
        bottomEndTarget = large
        topStartTarget = if (group.hasSameAuthorAbove) small else large
        bottomStartTarget = if (group.hasSameAuthorBelow) small else large
    }

    val springSpec = spring<Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )
    val topStart by animateDpAsState(topStartTarget, springSpec, label = "bubbleTopStart")
    val topEnd by animateDpAsState(topEndTarget, springSpec, label = "bubbleTopEnd")
    val bottomStart by animateDpAsState(bottomStartTarget, springSpec, label = "bubbleBottomStart")
    val bottomEnd by animateDpAsState(bottomEndTarget, springSpec, label = "bubbleBottomEnd")

    return AnimatedBubbleRadii(
        topStart = topStart,
        topEnd = topEnd,
        bottomStart = bottomStart,
        bottomEnd = bottomEnd,
    )
}

@Composable
internal fun rememberAnimatedBubbleShape(
    isAuthor: Boolean,
    group: MessageGroupInfo,
): RoundedCornerShape {
    val radii = rememberAnimatedBubbleRadii(isAuthor, group)
    return RoundedCornerShape(
        topStart = radii.topStart,
        topEnd = radii.topEnd,
        bottomStart = radii.bottomStart,
        bottomEnd = radii.bottomEnd,
    )
}

internal fun bubbleTopRadii(
    isAuthor: Boolean,
    group: MessageGroupInfo,
): Pair<Dp, Dp> {
    val large = BUBBLE_RADIUS_LARGE
    val small = BUBBLE_RADIUS_SMALL
    return if (isAuthor) {
        large to if (group.hasSameAuthorAbove) small else large
    } else {
        (if (group.hasSameAuthorAbove) small else large) to large
    }
}

internal fun bubbleBottomRadii(
    isAuthor: Boolean,
    group: MessageGroupInfo,
): Pair<Dp, Dp> {
    val large = BUBBLE_RADIUS_LARGE
    val small = BUBBLE_RADIUS_SMALL
    return if (isAuthor) {
        large to if (group.hasSameAuthorBelow) small else large
    } else {
        (if (group.hasSameAuthorBelow) small else large) to large
    }
}
