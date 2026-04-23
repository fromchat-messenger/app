package ru.fromchat.calls

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PresentToAll
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import io.livekit.android.compose.local.RoomScope
import io.livekit.android.compose.state.rememberParticipantTrackReferences
import io.livekit.android.compose.state.rememberParticipants
import io.livekit.android.compose.types.TrackReference
import io.livekit.android.compose.ui.ScaleType
import io.livekit.android.compose.ui.VideoTrackView
import io.livekit.android.room.Room
import io.livekit.android.room.participant.LocalParticipant
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.screencapture.ScreenCaptureParams
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.*
import ru.fromchat.api.ApiClient
import ru.fromchat.api.ProfileCache
import ru.fromchat.core.Logger
import ru.fromchat.ui.chat.Avatar

private const val TAG = "CallMediaLayer"
private const val SCREEN_SHARE_NOTIFICATION_ID = 99102
private const val SCREEN_SHARE_CHANNEL_ID = "fromchat_call_screenshare"

private val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.CAMERA,
)

private enum class VideoSlot {
    RemoteScreen,
    RemoteCam,
    LocalScreen,
    LocalCam,
}

private tailrec fun findActivity(ctx: Context?): Activity? = when (ctx) {
    is Activity -> ctx
    is ContextWrapper -> findActivity(ctx.baseContext)
    else -> null
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
actual fun CallMediaLayer(
    connect: LiveKitConnectSession?,
    showDialingPlaceholder: Boolean,
    showInCallControls: Boolean,
    modifier: Modifier,
) {
    val context = LocalContext.current
    var permissionsGranted by remember {
        mutableStateOf(
            REQUIRED_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            },
        )
    }
    var askedForPermissions by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        permissionsGranted = result.values.all { it }
    }

    LaunchedEffect(connect, showDialingPlaceholder) {
        val needMedia = connect != null || showDialingPlaceholder
        if (needMedia && !permissionsGranted && !askedForPermissions) {
            askedForPermissions = true
            launcher.launch(REQUIRED_PERMISSIONS)
        }
    }

    when {
        connect == null && showDialingPlaceholder -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        connect != null && permissionsGranted -> {
            RoomScope(
                url = connect.serverUrl,
                token = connect.token,
                audio = true,
                video = true,
                onConnected = { room ->
                    launch {
                        delay(200)
                        runCatching {
                            room.localParticipant.setMicrophoneEnabled(true)
                            room.localParticipant.setCameraEnabled(true)
                        }.onFailure { Logger.e(TAG, "onConnected media enable failed", it) }
                    }
                },
            ) { room ->
                CallRoomContent(
                    room = room,
                    session = connect,
                    showInCallControls = showInCallControls,
                    modifier = modifier,
                )
            }
        }
        connect != null && !permissionsGranted -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        else -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun CallRoomContent(
    room: Room,
    session: LiveKitConnectSession,
    showInCallControls: Boolean,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val ongoingChannelName = stringResource(Res.string.notif_call_channel_name)
    val ongoingTitle = stringResource(Res.string.notif_call_ongoing_title)
    val ongoingText = stringResource(Res.string.notif_call_ongoing_text)
    val callStartWallMs = remember(session.roomName) { System.currentTimeMillis() }
    DisposableEffect(Unit) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val previous = am.mode
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        onDispose {
            am.mode = previous
        }
    }

    DisposableEffect(ongoingChannelName, session.peerDisplayName, ongoingTitle, ongoingText, callStartWallMs) {
        CallForegroundService.start(
            context.applicationContext,
            ongoingChannelName,
            session.peerDisplayName,
            ongoingTitle,
            ongoingText,
            callStartWallMs,
        )
        onDispose {
            CallForegroundService.stop(context.applicationContext)
        }
    }

    val hazeState = rememberHazeState(blurEnabled = showInCallControls)
    val navBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
        ) {
            CallParticipantVideos(
                room = room,
                session = session,
                showInCallControls = showInCallControls,
            )
        }
        if (showInCallControls) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(168.dp + navBottomDp)
                    .zIndex(0.5f)
                    .hazeEffect(state = hazeState, style = HazeMaterials.thin()) {
                        progressive = HazeProgressive.verticalGradient(
                            startIntensity = 0f,
                            endIntensity = 1f,
                        )
                    },
            )
            CallInlineControlBar(
                room = room,
                hazeState = hazeState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun CallParticipantVideos(
    room: Room,
    session: LiveKitConnectSession,
    showInCallControls: Boolean,
) {
    val participants = rememberParticipants(room).value
    val local = room.localParticipant
    val remote: Participant? = participants.firstOrNull { it != local }
    if (remote == null) {
        SoloCallParticipantVideos(room, local, session, showInCallControls)
    } else {
        DuoCallParticipantVideos(
            room = room,
            remote = remote,
            local = local,
            session = session,
            showInCallControls = showInCallControls,
        )
    }
}

@Composable
private fun SoloCallParticipantVideos(
    room: Room,
    local: LocalParticipant,
    session: LiveKitConnectSession,
    showInCallControls: Boolean,
) {
    var localCamOn by remember { mutableStateOf(true) }
    var localLevel by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(local) {
        while (isActive) {
            localCamOn = local.isCameraEnabled
            localLevel = localLevel * 0.82f + local.audioLevel * 0.18f
            delay(90)
        }
    }
    val localCamRefs = rememberParticipantTrackReferences(
        sources = listOf(Track.Source.CAMERA),
        passedParticipant = local,
        onlySubscribed = true,
    ).value
    val lcRef = localCamRefs.firstOrNull()
    val self = ApiClient.user
    val selfPic = self?.id?.let { ProfileCache.get(it)?.profilePicture } ?: self?.profile_picture
    val selfName = self?.displayName?.takeIf { !it.isNullOrBlank() } ?: self?.username.orEmpty()
    val peerPic = ProfileCache.get(session.peerUserId)?.profilePicture
    Box(Modifier.fillMaxSize()) {
        when {
            // Never full-screen your own screen share; show camera or placeholders instead.
            lcRef != null && localCamOn -> {
                VideoTrackView(
                    trackReference = lcRef,
                    modifier = Modifier.fillMaxSize(),
                    room = room,
                    mirror = true,
                    scaleType = ScaleType.Fill,
                )
            }
            lcRef != null && !localCamOn -> {
                RemoteVideoOffPlaceholder(
                    displayName = selfName,
                    profilePictureUrl = selfPic,
                    label = selfName,
                    audioLevel = localLevel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                RemoteVideoOffPlaceholder(
                    displayName = session.peerDisplayName,
                    profilePictureUrl = peerPic,
                    label = session.peerDisplayName,
                    audioLevel = 0f,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        CallVolumeAvatars(
            remoteName = session.peerDisplayName,
            remotePic = peerPic,
            remoteLevel = 0f,
            selfName = selfName,
            selfPic = selfPic,
            localLevel = localLevel,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(12.dp),
        )
    }
}

@Composable
private fun DuoCallParticipantVideos(
    room: Room,
    remote: Participant,
    local: LocalParticipant,
    session: LiveKitConnectSession,
    showInCallControls: Boolean,
) {
    var remoteCamOn by remember { mutableStateOf(true) }
    var localCamOn by remember { mutableStateOf(true) }
    var remoteLevel by remember { mutableFloatStateOf(0f) }
    var localLevel by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(remote, local) {
        while (isActive) {
            remoteCamOn = remote.isCameraEnabled
            remoteLevel = remoteLevel * 0.82f + remote.audioLevel * 0.18f
            localCamOn = local.isCameraEnabled
            localLevel = localLevel * 0.82f + local.audioLevel * 0.18f
            delay(90)
        }
    }

    val remoteScreenRefs = rememberParticipantTrackReferences(
        sources = listOf(Track.Source.SCREEN_SHARE),
        passedParticipant = remote,
        onlySubscribed = true,
    ).value
    val remoteCamRefs = rememberParticipantTrackReferences(
        sources = listOf(Track.Source.CAMERA),
        passedParticipant = remote,
        onlySubscribed = true,
    ).value
    val localScreenRefs = rememberParticipantTrackReferences(
        sources = listOf(Track.Source.SCREEN_SHARE),
        passedParticipant = local,
        onlySubscribed = true,
    ).value
    val localCamRefs = rememberParticipantTrackReferences(
        sources = listOf(Track.Source.CAMERA),
        passedParticipant = local,
        onlySubscribed = true,
    ).value

    val rsRef = remoteScreenRefs.firstOrNull()
    val rcRef = remoteCamRefs.firstOrNull()
    val lsRef = localScreenRefs.firstOrNull()
    val lcRef = localCamRefs.firstOrNull()

    var userMain by remember { mutableStateOf<VideoSlot?>(null) }

    fun defaultMainSlot(): VideoSlot? = when {
        rsRef != null -> VideoSlot.RemoteScreen
        rcRef != null && remoteCamOn -> VideoSlot.RemoteCam
        lcRef != null && localCamOn -> VideoSlot.LocalCam
        rcRef != null -> VideoSlot.RemoteCam
        lcRef != null -> VideoSlot.LocalCam
        else -> null
    }

    val effectiveMain = userMain ?: defaultMainSlot()

    LaunchedEffect(userMain, rsRef, rcRef, lsRef, lcRef, remoteCamOn, localCamOn) {
        val u = userMain ?: return@LaunchedEffect
        val ok = when (u) {
            VideoSlot.RemoteScreen -> rsRef != null
            VideoSlot.RemoteCam -> rcRef != null
            VideoSlot.LocalScreen -> false
            VideoSlot.LocalCam -> lcRef != null
        }
        if (!ok) userMain = null
    }

    fun refFor(slot: VideoSlot): TrackReference? = when (slot) {
        VideoSlot.RemoteScreen -> rsRef
        VideoSlot.RemoteCam -> rcRef
        VideoSlot.LocalScreen -> lsRef
        VideoSlot.LocalCam -> lcRef
    }

    fun isScreen(slot: VideoSlot): Boolean =
        slot == VideoSlot.RemoteScreen || slot == VideoSlot.LocalScreen

    fun camEnabled(slot: VideoSlot): Boolean = when (slot) {
        VideoSlot.RemoteCam -> remoteCamOn
        VideoSlot.LocalCam -> localCamOn
        else -> true
    }

    val orderedSlots = listOf(
        VideoSlot.RemoteScreen,
        VideoSlot.RemoteCam,
        VideoSlot.LocalCam,
    ).filter { slot ->
        val r = refFor(slot)
        when {
            r == null -> false
            isScreen(slot) -> true
            else -> true
        }
    }

    val previewSlots = orderedSlots.filter { it != effectiveMain }.take(3)

    val peerPic = ProfileCache.get(session.peerUserId)?.profilePicture
    val self = ApiClient.user
    val selfId = self?.id
    val selfPic = selfId?.let { ProfileCache.get(it)?.profilePicture }
        ?: self?.profile_picture
    val selfName = self?.displayName?.takeIf { !it.isNullOrBlank() } ?: self?.username.orEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        val mainSlot = effectiveMain
        val mainRef = mainSlot?.let { refFor(it) }
        val swapMain: () -> Unit = {
            if (previewSlots.isNotEmpty()) userMain = previewSlots.first()
        }
        Box(modifier = Modifier.fillMaxSize()) {
            if (mainSlot != null && mainRef != null && (isScreen(mainSlot) || camEnabled(mainSlot))) {
                VideoTrackView(
                    trackReference = mainRef,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = swapMain,
                        ),
                    room = room,
                    mirror = mainSlot == VideoSlot.LocalCam || mainSlot == VideoSlot.LocalScreen,
                    scaleType = ScaleType.Fill,
                )
            } else if (mainSlot != null && mainRef != null && !isScreen(mainSlot) && !camEnabled(mainSlot)) {
                RemoteVideoOffPlaceholder(
                    displayName = session.peerDisplayName,
                    profilePictureUrl = if (mainSlot == VideoSlot.RemoteCam) peerPic else selfPic,
                    label = if (mainSlot == VideoSlot.RemoteCam) session.peerDisplayName else selfName,
                    audioLevel = if (mainSlot == VideoSlot.RemoteCam) remoteLevel else localLevel,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = swapMain,
                        ),
                )
            } else {
                RemoteVideoOffPlaceholder(
                    displayName = session.peerDisplayName,
                    profilePictureUrl = peerPic,
                    label = session.peerDisplayName,
                    audioLevel = remoteLevel,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = swapMain,
                        ),
                )
            }
        }

        CallVolumeAvatars(
            remoteName = session.peerDisplayName,
            remotePic = peerPic,
            remoteLevel = remoteLevel,
            selfName = selfName,
            selfPic = selfPic,
            localLevel = localLevel,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(12.dp),
        )

        CallPreviewCluster(
            room = room,
            previewSlots = previewSlots,
            refFor = ::refFor,
            isScreen = ::isScreen,
            camEnabled = ::camEnabled,
            showInCallControls = showInCallControls,
            onSelectMain = { slot -> userMain = slot },
        )
    }
}

@Composable
private fun RemoteVideoOffPlaceholder(
    displayName: String,
    profilePictureUrl: String?,
    label: String,
    audioLevel: Float,
    modifier: Modifier,
) {
    val t = rememberInfiniteTransition(label = "callGrad").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(14_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "t",
    )
    val c0 = MaterialTheme.colorScheme.primaryContainer
    val c1 = MaterialTheme.colorScheme.tertiaryContainer
    val c2 = MaterialTheme.colorScheme.secondaryContainer
    val shift = t.value * 400f
    val cap = 0.55f
    val boost = (audioLevel.coerceIn(0f, cap) / cap).coerceIn(0f, 1f)
    val scale = 1f + boost * 0.14f
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(c0, c1, c2, c0),
                start = Offset(shift, 0f),
                end = Offset(shift + 900f, 900f),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(
                profilePictureUrl = profilePictureUrl,
                displayName = displayName,
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            )
            Spacer(Modifier.size(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

@Composable
private fun CallVolumeAvatars(
    remoteName: String,
    remotePic: String?,
    remoteLevel: Float,
    selfName: String,
    selfPic: String?,
    localLevel: Float,
    modifier: Modifier,
) {
    val cap = 0.55f
    fun scaleFor(level: Float): Float {
        val boost = (level.coerceIn(0f, cap) / cap).coerceIn(0f, 1f)
        return 1f + boost * 0.12f
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .graphicsLayer {
                    scaleX = scaleFor(remoteLevel)
                    scaleY = scaleFor(remoteLevel)
                },
            contentAlignment = Alignment.Center,
        ) {
            Avatar(
                profilePictureUrl = remotePic,
                displayName = remoteName,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .graphicsLayer {
                    scaleX = scaleFor(localLevel)
                    scaleY = scaleFor(localLevel)
                },
            contentAlignment = Alignment.Center,
        ) {
            Avatar(
                profilePictureUrl = selfPic,
                displayName = selfName,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
            )
        }
    }
}

@Composable
private fun CallPreviewCluster(
    room: Room,
    previewSlots: List<VideoSlot>,
    refFor: (VideoSlot) -> TrackReference?,
    isScreen: (VideoSlot) -> Boolean,
    camEnabled: (VideoSlot) -> Boolean,
    showInCallControls: Boolean,
    onSelectMain: (VideoSlot) -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val topPx = with(density) { safe.calculateTopPadding().toPx() }
    val bottomPx = with(density) { safe.calculateBottomPadding().toPx() }
    val startPx = with(density) { safe.calculateLeftPadding(layoutDirection).toPx() }
    val endPx = with(density) { safe.calculateRightPadding(layoutDirection).toPx() }

    val gap = 8.dp
    val controlsReserve = if (showInCallControls) with(density) { 108.dp.toPx() } else with(density) { 16.dp.toPx() }
    val handleH = 10.dp
    val handlePx = with(density) { handleH.toPx() }

    val posX = remember { Animatable(0f) }
    val posY = remember { Animatable(0f) }
    var layoutReady by remember { mutableStateOf(false) }
    var stripHorizontal by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val marginPx = with(density) { 12.dp.toPx() }
        val pipWpx = with(density) { 104.dp.toPx() }
        val pipHpx = with(density) { 138.dp.toPx() }
        val gapPx = with(density) { gap.toPx() }
        val n = previewSlots.size.coerceAtLeast(1)
        val clusterHCol = n * pipHpx + (n - 1).coerceAtLeast(0) * gapPx + handlePx + with(density) { 4.dp.toPx() }
        val clusterWRow = n * pipWpx + (n - 1).coerceAtLeast(0) * gapPx

        val maxXPxCol = with(density) { maxWidth.toPx() } - endPx - pipWpx
        val maxYPxCol = with(density) { maxHeight.toPx() } - bottomPx - controlsReserve - clusterHCol
        val maxXPxRow = with(density) { maxWidth.toPx() } - endPx - clusterWRow
        val topRowY = topPx + marginPx

        LaunchedEffect(maxWidth, maxHeight, bottomPx, controlsReserve, endPx, topPx) {
            if (!layoutReady) {
                posX.snapTo(maxXPxCol.coerceAtLeast(marginPx + startPx))
                posY.snapTo((marginPx + topPx).coerceAtMost(maxYPxCol))
                layoutReady = true
            }
        }

        fun clampCol(o: Offset): Offset = Offset(
            x = o.x.coerceIn(marginPx + startPx, maxXPxCol),
            y = o.y.coerceIn(marginPx + topPx, maxYPxCol),
        )

        fun clampRow(o: Offset): Offset = Offset(
            x = o.x.coerceIn(marginPx + startPx, maxXPxRow),
            y = o.y.coerceIn(topRowY, topRowY),
        )

        fun nearestCornerCol(o: Offset): Offset {
            val xs = listOf(marginPx + startPx, maxXPxCol)
            val ys = listOf(marginPx + topPx, maxYPxCol)
            var best = Offset(xs.first(), ys.first())
            var bestD = Float.MAX_VALUE
            for (x in xs) {
                for (y in ys) {
                    val dx = (o.x - x).toDouble()
                    val dy = (o.y - y).toDouble()
                    val d = sqrt(dx * dx + dy * dy).toFloat()
                    if (d < bestD) {
                        bestD = d
                        best = Offset(x, y)
                    }
                }
            }
            return best
        }

        val springSnap = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )

        if (previewSlots.isEmpty()) return@BoxWithConstraints

        fun moveCluster(amount: Offset) {
            val raw = Offset(posX.value + amount.x, posY.value + amount.y)
            val next = if (stripHorizontal) clampRow(raw) else clampCol(raw)
            val wantStrip = !stripHorizontal && raw.y < topPx + with(density) { 88.dp.toPx() }
            if (wantStrip && previewSlots.isNotEmpty()) {
                stripHorizontal = true
                scope.launch {
                    posX.snapTo(clampRow(Offset(posX.value, topRowY)).x)
                    posY.snapTo(topRowY)
                }
                return
            }
            scope.launch {
                posX.snapTo(next.x)
                posY.snapTo(next.y)
            }
        }

        val dragModifier = Modifier.pointerInput(layoutReady, stripHorizontal, maxXPxCol, maxYPxCol, maxXPxRow, topRowY, marginPx, startPx, topPx) {
            if (!layoutReady) return@pointerInput
            detectDragGestures(
                onDrag = { change, amount ->
                    change.consume()
                    moveCluster(amount)
                },
                onDragEnd = {
                    scope.launch {
                        val t = if (stripHorizontal) {
                            Offset(
                                posX.value.coerceIn(marginPx + startPx, maxXPxRow),
                                topRowY,
                            )
                        } else {
                            nearestCornerCol(Offset(posX.value, posY.value))
                        }
                        coroutineScope {
                            awaitAll(
                                async { posX.animateTo(t.x, springSnap) },
                                async { posY.animateTo(t.y, springSnap) },
                            )
                        }
                    }
                },
                onDragCancel = {
                    scope.launch {
                        val t = if (stripHorizontal) {
                            Offset(posX.value.coerceIn(marginPx + startPx, maxXPxRow), topRowY)
                        } else {
                            nearestCornerCol(Offset(posX.value, posY.value))
                        }
                        coroutineScope {
                            awaitAll(
                                async { posX.animateTo(t.x, springSnap) },
                                async { posY.animateTo(t.y, springSnap) },
                            )
                        }
                    }
                },
            )
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(posX.value.roundToInt(), posY.value.roundToInt()) },
        ) {
            if (!stripHorizontal) {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    for (slot in previewSlots) {
                        PreviewTile(
                            room = room,
                            ref = refFor(slot),
                            mirror = slot == VideoSlot.LocalCam || slot == VideoSlot.LocalScreen,
                            showVideo = refFor(slot) != null && (isScreen(slot) || camEnabled(slot)),
                            onTap = { onSelectMain(slot) },
                        )
                    }
                    Spacer(Modifier.size(4.dp))
                    Box(
                        modifier = Modifier
                            .size(104.dp, handleH)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .then(dragModifier),
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp, 138.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .then(dragModifier),
                    )
                    for (slot in previewSlots) {
                        PreviewTile(
                            room = room,
                            ref = refFor(slot),
                            mirror = slot == VideoSlot.LocalCam || slot == VideoSlot.LocalScreen,
                            showVideo = refFor(slot) != null && (isScreen(slot) || camEnabled(slot)),
                            onTap = { onSelectMain(slot) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewTile(
    room: Room,
    ref: TrackReference?,
    mirror: Boolean,
    showVideo: Boolean,
    onTap: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(104.dp, 138.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            ),
    ) {
        if (ref != null && showVideo) {
            VideoTrackView(
                trackReference = ref,
                modifier = Modifier.fillMaxSize(),
                room = room,
                mirror = mirror,
                scaleType = ScaleType.Fill,
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.VideocamOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun CallInlineControlBar(
    room: Room,
    hazeState: dev.chrisbanes.haze.HazeState,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val localParticipant = room.localParticipant
    var micOn by remember { mutableStateOf(true) }
    var camOn by remember { mutableStateOf(true) }
    var shareOn by remember { mutableStateOf(false) }

    LaunchedEffect(localParticipant) {
        while (isActive) {
            micOn = localParticipant.isMicrophoneEnabled
            camOn = localParticipant.isCameraEnabled
            shareOn = localParticipant.isScreenShareEnabled
            delay(400)
        }
    }

    val context = LocalContext.current
    val shareNotifTitle = stringResource(Res.string.notif_screenshare_title)
    val shareNotifText = stringResource(Res.string.notif_screenshare_text)
    val updatedTitle by rememberUpdatedState(shareNotifTitle)
    val updatedText by rememberUpdatedState(shareNotifText)

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    fun buildShareNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                SCREEN_SHARE_CHANNEL_ID,
                updatedTitle,
                NotificationManager.IMPORTANCE_LOW,
            )
            nm.createNotificationChannel(ch)
        }
        val smallIcon = try {
            context.packageManager.getApplicationInfo(context.packageName, 0).icon
        } catch (_: Exception) {
            android.R.drawable.stat_sys_upload
        }
        return NotificationCompat.Builder(context, SCREEN_SHARE_CHANNEL_ID)
            .setContentTitle(updatedTitle)
            .setContentText(updatedText)
            .setSmallIcon(smallIcon)
            .setOngoing(true)
            .build()
    }

    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            scope.launch {
                val notif = buildShareNotification()
                val ok = runCatching {
                    localParticipant.setScreenShareEnabled(
                        true,
                        ScreenCaptureParams(
                            mediaProjectionPermissionResultData = result.data!!,
                            notificationId = SCREEN_SHARE_NOTIFICATION_ID,
                            notification = notif,
                        ),
                    )
                }.onFailure { Logger.e(TAG, "setScreenShareEnabled failed", it) }
                if (ok.isSuccess) shareOn = true
            }
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(28.dp))
                .hazeEffect(state = hazeState, style = HazeMaterials.thick())
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.34f)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = {
                    scope.launch {
                        val next = !micOn
                        if (runCatching { localParticipant.setMicrophoneEnabled(next) }.isSuccess) {
                            micOn = next
                        }
                    }
                },
                modifier = Modifier.size(52.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (micOn) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    contentColor = if (micOn) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                ),
            ) {
                Icon(
                    imageVector = if (micOn) Icons.Filled.Mic else Icons.Filled.MicOff,
                    contentDescription = stringResource(Res.string.cd_call_mic),
                )
            }
            Spacer(Modifier.size(10.dp))
            FilledTonalIconButton(
                onClick = {
                    scope.launch {
                        val next = !camOn
                        if (runCatching { localParticipant.setCameraEnabled(next) }.isSuccess) {
                            camOn = next
                        }
                    }
                },
                modifier = Modifier.size(52.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (camOn) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    contentColor = if (camOn) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                ),
            ) {
                Icon(
                    imageVector = if (camOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                    contentDescription = stringResource(Res.string.cd_call_camera),
                )
            }
            Spacer(Modifier.size(10.dp))
            FilledTonalIconButton(
                onClick = {
                    if (shareOn) {
                        scope.launch {
                            if (runCatching { localParticipant.setScreenShareEnabled(false) }.isSuccess) {
                                shareOn = false
                            }
                        }
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!granted) {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                return@FilledTonalIconButton
                            }
                        }
                        val act = findActivity(context) ?: return@FilledTonalIconButton
                        val m = act.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        shareLauncher.launch(m.createScreenCaptureIntent())
                    }
                },
                modifier = Modifier.size(52.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (shareOn) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = if (shareOn) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.PresentToAll,
                    contentDescription = stringResource(Res.string.cd_call_screenshare),
                )
            }
            Spacer(Modifier.size(18.dp))
            FilledIconButton(
                onClick = { CallStore.endCall() },
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.CallEnd,
                    contentDescription = stringResource(Res.string.cd_call_end),
                )
            }
        }
    }
}