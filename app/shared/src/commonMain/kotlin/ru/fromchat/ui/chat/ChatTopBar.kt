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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pr0gramm3r101.utils.conditional
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.chat_members_count
import ru.fromchat.ui.chat.utils.TypingUser
import ru.fromchat.ui.components.ConnectingEllipsis
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.profile.StatusBadge
import ru.fromchat.ui.profile.peerIsDeleted
import ru.fromchat.ui.profile.resolveVerificationStatus
import ru.fromchat.api.local.db.store.ProfileCache
import ru.fromchat.api.local.db.store.visibleDisplayName
import ru.fromchat.api.ApiClient
import com.pr0gramm3r101.utils.scaleOnPress
import kotlin.math.PI
import kotlin.math.tan

val ChatFloatingHeaderBottomArcRadius: Dp = 35.dp

@Composable
fun ChatTopBarInner(
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
    typingShowsUsernames: Boolean = true,
    statusConnecting: String,
    statusUpdating: String,
    chatGroupLabel: String,
    modifier: Modifier = Modifier,
) {
    val isDeletedPeer = profileUserId?.let { userId ->
        peerIsDeleted(
            userId = userId,
            currentUserId = ApiClient.user?.id,
            username = ProfileCache.get(userId)?.username,
        )
    } == true
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier.conditional(
            onTitleClick != null
        ) {
            Modifier.scaleOnPress(
                scale = 0.96f,
                onClick = onTitleClick
            )
        },
    ) {
        when {
            sharedAvatarKey != null && sharedTransitionScope != null && animatedVisibilityScope != null -> {
                val displayName = titleAvatar?.displayName?.takeIf { it.isNotBlank() }
                    ?: title.takeIf { it.isNotBlank() }
                    ?: ""

                with(sharedTransitionScope) {
                    Avatar(
                        profilePictureUrl = titleAvatar?.profilePictureUrl,
                        displayName = displayName,
                        modifier = Modifier
                            .sharedElement(
                                rememberSharedContentState(key = sharedAvatarKey),
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                            .size(40.dp),
                        isDeletedUser = isDeletedPeer,
                        userId = profileUserId,
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
                        isDeletedUser = isDeletedPeer,
                        userId = profileUserId,
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
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                profileUserId?.let { userId ->
                    val profileCacheRevision by ProfileCache.revision.collectAsState()
                    val status = remember(userId, profileCacheRevision) {
                        resolveVerificationStatus(userId)
                    }
                    if (status != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        StatusBadge(
                            verificationStatus = status,
                            size = 18.dp,
                        )
                    }
                }
            }

            AnimatedContent(
                modifier = Modifier.fillMaxWidth(),
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
                        val me = ApiClient.user?.id
                        TypingIndicator(
                            typingUsers = currentTypingUsers.map { user ->
                                ProfileCache.get(user.userId)?.visibleDisplayName(me)
                                    ?: user.username
                            },
                            showUsernames = typingShowsUsernames,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    hazeState: HazeState,
    onBack: () -> Unit,
    backContentDescription: String,
    showCallButton: Boolean,
    onCallClick: () -> Unit,
    callContentDescription: String,
    titleChrome: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bottomCornerRadius = ChatFloatingHeaderBottomArcRadius
    // [BottomInsetTopBarShape] draws the arc in the strip from y = (content height) .. (content + r);
    // stack measured bar height + that depth so the scallop is never overlapped by TopAppBar children.
    val arcExtentPx = with(LocalDensity.current) { bottomCornerRadius.roundToPx() }

    SubcomposeLayout(modifier = modifier.fillMaxWidth()) { constraints ->
        val heightUnbounded = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val topBarPlaceable = subcompose("topBar") {
            TopAppBar(
                modifier = Modifier.fillMaxWidth(),
                windowInsets = WindowInsets.statusBars,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = backContentDescription
                        )
                    }
                },
                title = { titleChrome() },
                actions = {
                    if (showCallButton) {
                        IconButton(onClick = onCallClick) {
                            Icon(
                                imageVector = Icons.Rounded.Call,
                                contentDescription = callContentDescription,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                },
            )
        }.first().measure(heightUnbounded)

        val layoutWidth = topBarPlaceable.width.coerceIn(constraints.minWidth, constraints.maxWidth)
        val layoutHeight = topBarPlaceable.height + arcExtentPx

        val bgPlaceable = subcompose("background") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(
                        remember(bottomCornerRadius) {
                            BottomInsetTopBarShape(bottomCornerRadius)
                        }
                    )
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.91f))
                    .hazeEffect(state = hazeState, style = rememberChatSurfaceContainerHazeStyle()),
            )
        }.first().measure(Constraints.fixed(layoutWidth, layoutHeight))

        layout(layoutWidth, layoutHeight) {
            bgPlaceable.place(0, 0)
            topBarPlaceable.place(0, 0)
        }
    }
}

/** lol, i dont even know what this means because this file was vibecoded
 *
 * Cubic-fit constant for a quarter circle: `4/3 * tan(pi/8)` (~0.5522847498).
 * */
private val CircularCornerBezierKappa = (4/3 * tan(PI/8)).toFloat()

/**
 * Bottom edge: inset horizontal segment (`y = height − r`), then quarter-circle corners with
 * circle centers **`(r, height)` / `(width − r, height)`** — same as the SVG (`M r y … C …`).
 *
 * Compose's [Path.arcTo] uses an ellipse inscribed in `Rect` whose **center is rect center**, so it
 * cannot express these corners reliably; cubic segments match the arcs instead.
 */
private class BottomInsetTopBarShape(private val cornerRadius: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val w = size.width
        val h = size.height
        val r = with(density) { cornerRadius.toPx() }.coerceIn(0f, minOf(w / 2f, h))
        val straightY = (h - r).coerceAtLeast(0f)
        val k = CircularCornerBezierKappa * r

        return Outline.Generic(
            Path().apply {
                moveTo(r, straightY)
                lineTo(w - r, straightY)
                // Bottom-right: center (w - r, h), arc from (w - r, straightY) to (w, h).
                cubicTo(
                    x1 = w - r + k,
                    y1 = straightY,
                    x2 = w,
                    y2 = h - k,
                    x3 = w,
                    y3 = h,
                )
                lineTo(w, 0f)
                lineTo(0f, 0f)
                lineTo(0f, h)
                // Bottom-left: center (r, h), arc from (0, h) to (r, straightY).
                cubicTo(
                    x1 = 0f,
                    y1 = h - k,
                    x2 = r - k,
                    y2 = straightY,
                    x3 = r,
                    y3 = straightY,
                )
                close()
            }
        )
    }
}

/**
 * [HazeStyle] for chat chrome using only [androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer]
 * (24dp blur + luminance-scaled tint, slightly denser than old “thin” defaults), without [dev.chrisbanes.haze.materials.HazeMaterials].
 */
@Composable
fun rememberChatSurfaceContainerHazeStyle(): HazeStyle {
    val surface = MaterialTheme.colorScheme.surfaceContainer

    return remember(surface) {
        HazeStyle(
            blurRadius = 24.dp,
            backgroundColor = surface,
            tint = HazeTint(surface.copy(alpha = if (surface.luminance() >= 0.5f) 0.74f else 0.79f)),
        )
    }
}

/**
 * Single-flight gate for chat title/avatar navigate vs back during shared-element transitions.
 * Ignores taps while a transition is running or the current destination is not resumed.
 */
@Composable
fun rememberChatNavigationGate(
    navController: NavController,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
): (() -> Unit) -> Unit {
    var locked by remember { mutableStateOf(false) }
    val entry = navController.currentBackStackEntry
    LaunchedEffect(entry) {
        locked = false
    }
    return { action ->
        val transitionRunning = animatedVisibilityScope?.transition?.isRunning == true
        val lifecycleOk = navController.currentBackStackEntry?.lifecycle?.currentState
            ?.let { it == Lifecycle.State.RESUMED } != false
        if (!locked && !transitionRunning && lifecycleOk) {
            locked = true
            action()
        }
    }
}
