package ru.fromchat.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.*
import ru.fromchat.ui.ConnectingEllipsis
import ru.fromchat.ui.scaleOnPress

/**
 * Telegram-style floating chrome: iOS-like frosted pill (Haze "thin" material + translucent fill),
 * circular back control, and a full-width progressive blur plate behind the header row.
 * [ChatFloatingHeaderBox] uses a plain [Box] (not a toolbar / TopAppBar) with side rails so the pill stays centered.
 */
@Composable
fun ChatFloatingBackButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    val fill = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
    Box(
        modifier = modifier
            .scaleOnPress(
                scale = 0.88f,
                onClick = onClick,
                indication = null,
                clipShape = shape,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(fill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBackIos,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun ChatFloatingTitleChrome(
    hazeState: HazeState,
    title: String,
    titleAvatar: AvatarInfo?,
    profileUserId: Int?,
    onTitleClick: (() -> Unit)?,
    hideTitleBarAvatar: Boolean,
    onAvatarSlotBounds: ((Rect) -> Unit)?,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    sharedAvatarKey: Any?,
    subtitleKey: String,
    currentTypingUsers: List<TypingUser>,
    statusConnecting: String,
    statusUpdating: String,
    chatGroupLabel: String,
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape(percent = 50)
    val fill = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
    val pillClickable = profileUserId != null && onTitleClick != null
    Box(
        modifier = modifier
            .wrapContentWidth()
            .then(
                if (pillClickable) {
                    Modifier.scaleOnPress(
                        scale = 0.96f,
                        onClick = onTitleClick,
                        clipShape = pillShape,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight()
                .heightIn(min = 44.dp)
                .clip(pillShape)
                .background(fill)
                .hazeEffect(state = hazeState, style = HazeMaterials.thin())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
        when {
            sharedAvatarKey != null && sharedTransitionScope != null && animatedVisibilityScope != null -> {
                val avatar = titleAvatar
                val displayName = avatar?.displayName?.takeIf { it.isNotBlank() }
                    ?: title.takeIf { it.isNotBlank() }
                    ?: ""
                with(sharedTransitionScope) {
                    Avatar(
                        profilePictureUrl = avatar?.profilePictureUrl,
                        displayName = displayName,
                        modifier = Modifier
                            .sharedElement(
                                rememberSharedContentState(key = sharedAvatarKey),
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                            .size(40.dp),
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            !hideTitleBarAvatar -> {
                titleAvatar?.let { avatar ->
                    Avatar(
                        profilePictureUrl = avatar.profilePictureUrl,
                        displayName = avatar.displayName,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
            }
            onAvatarSlotBounds != null -> {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .onGloballyPositioned { coords ->
                            val pos = coords.positionInRoot()
                            val sz = coords.size
                            onAvatarSlotBounds(
                                Rect(
                                    pos.x,
                                    pos.y,
                                    pos.x + sz.width.toFloat(),
                                    pos.y + sz.height.toFloat(),
                                ),
                            )
                        },
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            else -> {
                titleAvatar?.let {
                    Spacer(modifier = Modifier.width(40.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                }
            }
        }

        Column(
            modifier = Modifier
                .wrapContentWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            AnimatedContent(
                targetState = subtitleKey,
                transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn()) togetherWith
                        (slideOutVertically { -it / 2 } + fadeOut())
                },
                label = "chat_subtitle",
            ) { key ->
                when {
                    key == "updating" -> {
                        val st = MaterialTheme.typography.bodySmall
                        val col = MaterialTheme.colorScheme.onSurfaceVariant
                        Row(
                            modifier = Modifier.padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = statusUpdating,
                                style = st,
                                color = col,
                            )
                            ConnectingEllipsis(
                                fontSize = st.fontSize,
                                color = col,
                                baseStyle = st,
                            )
                        }
                    }
                    key == "connecting" -> {
                        val st = MaterialTheme.typography.bodySmall
                        val col = MaterialTheme.colorScheme.onSurfaceVariant
                        Row(
                            modifier = Modifier.padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = statusConnecting,
                                style = st,
                                color = col,
                            )
                            ConnectingEllipsis(
                                fontSize = st.fontSize,
                                color = col,
                                baseStyle = st,
                            )
                        }
                    }
                    key == "typing" -> {
                        TypingIndicator(
                            typingUsers = currentTypingUsers.map { it.username },
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    key.startsWith("presence:") -> {
                        val text = key.removePrefix("presence:")
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    key == "group" -> {
                        Text(
                            text = chatGroupLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    key.startsWith("members:") -> {
                        val n = key.removePrefix("members:").toIntOrNull() ?: 0
                        Text(
                            text = stringResource(Res.string.chat_members_count, n),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun ChatFloatingHeaderBox(
    hazeState: HazeState,
    onBack: () -> Unit,
    backContentDescription: String,
    showCallButton: Boolean,
    onCallClick: () -> Unit,
    callContentDescription: String,
    titleChrome: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sideSlot = 56.dp
    val density = LocalDensity.current
    val statusBarDp = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val blurPlateHeight = statusBarDp + 64.dp + 12.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(blurPlateHeight)
                .align(Alignment.TopCenter)
                .hazeEffect(state = hazeState, style = HazeMaterials.thin()) {
                    progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
                },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .height(IntrinsicSize.Min)
                .zIndex(1f)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 8.dp, end = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(sideSlot)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.fillMaxSize().padding(6.dp)) {
                    ChatFloatingBackButton(
                        contentDescription = backContentDescription,
                        onClick = onBack,
                        modifier = Modifier
                            .fillMaxSize()
                            .aspectRatio(1f, matchHeightConstraintsFirst = true),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .wrapContentHeight(),
                contentAlignment = Alignment.Center,
            ) {
                titleChrome()
            }
            if (showCallButton) {
                val shape = CircleShape
                val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                val fill = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                Box(
                    modifier = Modifier
                        .width(sideSlot)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f, matchHeightConstraintsFirst = true)
                            .clip(shape)
                            .border(Dp.Hairline, outline, shape)
                            .background(fill)
                            .clickable(onClick = onCallClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = callContentDescription,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.width(sideSlot))
            }
        }
    }
}
