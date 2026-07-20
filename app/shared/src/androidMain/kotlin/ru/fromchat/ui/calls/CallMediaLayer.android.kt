package ru.fromchat.ui.calls

import android.Manifest
import android.R
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
import android.os.PowerManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import ru.fromchat.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import io.livekit.android.RoomOptions
import io.livekit.android.compose.local.RoomScope
import io.livekit.android.compose.state.rememberParticipantTrackReferences
import io.livekit.android.compose.state.rememberParticipants
import io.livekit.android.compose.types.TrackReference
import io.livekit.android.compose.ui.ScaleType
import io.livekit.android.compose.ui.VideoTrackView
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.LocalParticipant
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.LocalAudioTrackOptions
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.screencapture.ScreenCaptureParams
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.ApiClient
import ru.fromchat.api.calls.CallStore
import ru.fromchat.api.calls.LiveKitConnectSession
import ru.fromchat.api.local.db.store.ProfileCache
import ru.fromchat.notifications.NotificationSmallIcon
import ru.fromchat.call_status_connecting
import ru.fromchat.call_status_reconnecting
import ru.fromchat.call_status_reconnecting_with_detail
import ru.fromchat.cd_call_camera
import ru.fromchat.cd_call_end
import ru.fromchat.cd_call_mic
import ru.fromchat.cd_call_screenshare
import ru.fromchat.Logger
import ru.fromchat.message_sender_you
import ru.fromchat.notif_call_channel_name
import ru.fromchat.notif_call_ongoing_text
import ru.fromchat.notif_call_ongoing_title
import ru.fromchat.notif_screenshare_text
import ru.fromchat.notif_screenshare_title
import ru.fromchat.ui.chat.Avatar
import ru.fromchat.ui.profile.avatarLabelForInitials
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TAG = "CallMediaLayer"
private const val SCREEN_SHARE_NOTIFICATION_ID = 99102
private const val SCREEN_SHARE_CHANNEL_ID = "fromchat_call_screenshare"

private val pipReorgSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium,
)

private val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.CAMERA,
)

private enum class VideoSlot {
    None,
    RemoteScreen,
    RemoteCam,
    LocalScreen,
    LocalCam,
}

private data class CallOwnerUi(
    val name: String,
    val avatarName: String,
    val pictureUrl: String?,
    val level: Float,
    val isSelf: Boolean,
)

private tailrec fun findActivity(ctx: Context?): Activity? = when (ctx) {
    is Activity -> ctx
    is ContextWrapper -> findActivity(ctx.baseContext)
    else -> null
}

@Composable
private fun StreamOwnerChip(
    label: String,
    avatarName: String,
    profilePictureUrl: String?,
    audioLevel: Float,
    showName: Boolean,
    modifier: Modifier = Modifier,
) {
    val cap = 0.55f
    val boost = (audioLevel.coerceIn(0f, cap) / cap).coerceIn(0f, 1f)
    val scale = 1f + boost * 0.12f
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f))
            .padding(
                horizontal = if (showName) 6.dp else 4.dp,
                vertical = 4.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (showName) 6.dp else 0.dp),
    ) {
        Box(
            Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        ) {
            Avatar(
                profilePictureUrl = profilePictureUrl,
                displayName = avatarName,
                modifier = Modifier
                    .size(if (showName) 26.dp else 24.dp)
                    .clip(CircleShape),
            )
        }
        if (showName && label.isNotBlank()) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(max = 140.dp),
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CallSlotSharedVideo(
    room: Room,
    sharedTransitionScope: SharedTransitionScope,
    slot: VideoSlot,
    trackRef: TrackReference,
    mirror: Boolean,
    scaleType: ScaleType,
    modifier: Modifier,
    innerClipShape: Shape?,
) {
    with(sharedTransitionScope) {
        val state = rememberSharedContentState(key = slot)
        Box(
            modifier
                .sharedElementWithCallerManagedVisibility(
                    sharedContentState = state,
                    visible = true,
                    boundsTransform = { _, _ ->
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        )
                    },
                ),
        ) {
            VideoTrackView(
                trackReference = trackRef,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (innerClipShape != null) Modifier.clip(innerClipShape) else Modifier,
                    ),
                room = room,
                mirror = mirror,
                scaleType = scaleType,
            )
        }
    }
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
    var micRequestedOn by remember(connect?.roomName) { mutableStateOf(true) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        permissionsGranted = result.values.all { it }
        Logger.d(TAG, "media permissions result=$permissionsGranted detail=$result")
    }

    LaunchedEffect(connect, showDialingPlaceholder) {
        val needMedia = connect != null || showDialingPlaceholder
        Logger.d(
            TAG,
            "CallMediaLayer LaunchedEffect(connect,placeholder): needMedia=$needMedia " +
                "connectRoom=${connect?.roomName} perms=$permissionsGranted asked=$askedForPermissions",
        )
        if (needMedia && !permissionsGranted && !askedForPermissions) {
            askedForPermissions = true
            launcher.launch(REQUIRED_PERMISSIONS)
        }
    }

    LaunchedEffect(connect?.roomName, permissionsGranted, micRequestedOn) {
        Logger.d(
            TAG,
            "CallMediaLayer snapshot room=${connect?.roomName} perms=$permissionsGranted micReq=$micRequestedOn",
        )
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
            val statusConnecting = stringResource(Res.string.call_status_connecting)
            val statusReconnecting = stringResource(Res.string.call_status_reconnecting)
            val statusReconnectingWithDetailTemplate =
                stringResource(Res.string.call_status_reconnecting_with_detail)
            var connectionStatusText by remember(connect.roomName) {
                mutableStateOf<String?>(statusConnecting)
            }
            var reconnectNonce by remember(connect.roomName) { mutableStateOf(0) }
            var reconnectAttempt by remember(connect.roomName) { mutableStateOf(0) }
            var reconnectGeneration by remember(connect.roomName) { mutableStateOf(0) }
            val reconnectScope = rememberCoroutineScope()
            val safe = WindowInsets.safeDrawing.asPaddingValues()
            val bannerTop = safe.calculateTopPadding() + 12.dp

            // Start/stop the foreground call service once per call.
            // Reconnect logic re-mounts RoomScope, so putting this here avoids repeated
            // startForegroundService() races / crashes.
            val ongoingChannelName = stringResource(Res.string.notif_call_channel_name)
            val ongoingTitle = stringResource(Res.string.notif_call_ongoing_title)
            val ongoingText = stringResource(Res.string.notif_call_ongoing_text)
            val callStartWallMs = remember(connect.roomName) { System.currentTimeMillis() }
            DisposableEffect(
                ongoingChannelName,
                connect.peerDisplayName,
                ongoingTitle,
                ongoingText,
                callStartWallMs,
            ) {
                CallForegroundService.start(
                    context.applicationContext,
                    ongoingChannelName,
                    connect.peerDisplayName,
                    ongoingTitle,
                    ongoingText,
                    callStartWallMs,
                )
                onDispose {
                    CallForegroundService.stop(context.applicationContext)
                }
            }

            fun simplifyLiveKitErrorDetail(raw: String?): String? {
                val t = raw?.trim().orEmpty()
                if (t.isBlank()) return null
                val m = Regex("\"detail\"\\s*:\\s*\"([^\"]+)\"").find(t)
                return m?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: t
            }

            fun scheduleReconnect(detail: String?) {
                reconnectAttempt += 1
                val attempt = reconnectAttempt
                val simplified = simplifyLiveKitErrorDetail(detail)
                connectionStatusText =
                    simplified?.let {
                        java.lang.String.format(statusReconnectingWithDetailTemplate, it)
                    } ?: statusReconnecting

                val nextGen = reconnectGeneration + 1
                reconnectGeneration = nextGen
                reconnectScope.launch {
                    val delayMs = (attempt * 1000L).coerceAtMost(30_000L)
                    delay(delayMs)
                    if (reconnectGeneration != nextGen) return@launch
                    reconnectNonce += 1
                }
            }

            Logger.i(
                TAG,
                "RoomScope starting url=${connect.serverUrl} room=${connect.roomName} " +
                    "(mic UI sync waits for CONNECTED; DISCONNECTED means join failed or network)",
            )
            key(reconnectNonce) {
                RoomScope(
                url = connect.serverUrl,
                token = connect.token,
                roomOptions = RoomOptions(
                    audioTrackCaptureDefaults = LocalAudioTrackOptions(
                        typingNoiseDetection = false,
                    ),
                ),
                // Subscribe/play remote audio even when the local mic is muted.
                audio = true,
                video = false,
                onError = { r, e ->
                    val msg = e?.message?.takeIf { it.isNotBlank() } ?: e?.toString().orEmpty()
                    val simplified = simplifyLiveKitErrorDetail(msg)
                    connectionStatusText =
                        simplified?.let {
                            java.lang.String.format(statusReconnectingWithDetailTemplate, it)
                        } ?: statusReconnecting
                    Logger.e(
                        TAG,
                        "RoomScope onError state=${r.state} msg=${e?.message}",
                        e,
                    )
                },
                onDisconnected = { r ->
                    Logger.w(TAG, "RoomScope onDisconnected state=${r.state}")
                },
                onConnected = { room ->
                    connectionStatusText = null
                    reconnectAttempt = 0
                    reconnectGeneration += 1 // invalidate any pending reconnect

                    Logger.i(
                        TAG,
                        "RoomScope onConnected state=${room.state} micReq=$micRequestedOn " +
                            "micEn=${room.localParticipant.isMicrophoneEnabled}",
                    )
                    withContext(NonCancellable) {
                        runCatching {
                            room.localParticipant.setCameraEnabled(false)
                        }.onFailure { Logger.e(TAG, "onConnected media enable failed", it) }
                    }
                    Logger.d(
                        TAG,
                        "RoomScope onConnected after camera off: micEn=${room.localParticipant.isMicrophoneEnabled}",
                    )
                },
                ) { room ->
                LaunchedEffect(room) {
                    room.events.collect { event: RoomEvent ->
                        when (event) {
                            is RoomEvent.Connected -> {
                                connectionStatusText = null
                                reconnectAttempt = 0
                                reconnectGeneration += 1 // invalidate any pending reconnect
                                Logger.i(TAG, "RoomEvent.Connected")
                            }
                            is RoomEvent.Disconnected -> {
                                val detail = event.error?.message ?: event.reason.toString()
                                Logger.w(
                                    TAG,
                                    "RoomEvent.Disconnected reason=${event.reason} err=${event.error?.message}",
                                    event.error,
                                )
                                scheduleReconnect(detail)
                            }
                            is RoomEvent.FailedToConnect -> {
                                val msg =
                                    event.error.message?.takeIf { it.isNotBlank() }
                                    ?: event.error.toString()

                                Logger.e(TAG, "RoomEvent.FailedToConnect", event.error)
                                scheduleReconnect(msg.takeIf { it.isNotBlank() })
                            }
                            is RoomEvent.Reconnecting -> {
                                connectionStatusText = statusReconnecting
                                Logger.d(TAG, "RoomEvent.Reconnecting")
                            }
                            is RoomEvent.Reconnected -> {
                                connectionStatusText = null
                                Logger.i(TAG, "RoomEvent.Reconnected")
                            }
                            else -> {}
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    CallRoomContent(
                        room = room,
                        session = connect,
                        showInCallControls = showInCallControls,
                        micRequestedOn = micRequestedOn,
                        onMicRequestedChange = { micRequestedOn = it },
                        modifier = modifier,
                    )
                    val status = connectionStatusText
                    if (status != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(start = 16.dp, end = 16.dp, top = bannerTop, bottom = 12.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.75f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.size(12.dp))
                                Text(
                                    text = status,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
                }
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
    micRequestedOn: Boolean,
    onMicRequestedChange: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current

    DisposableEffect(room) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val previousMode = audioManager.mode
        runCatching {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        }
        onDispose {
            runCatching {
                audioManager.mode = previousMode
            }
        }
    }

    DisposableEffect(Unit) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val tag = "${TAG}:CallWake"
        val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag).apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L)
        }
        onDispose {
            runCatching {
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    LaunchedEffect(room, micRequestedOn) {
        Logger.d(
            TAG,
            "micSync effect START micReq=$micRequestedOn roomState=${room.state} " +
                "micEn=${room.localParticipant.isMicrophoneEnabled}",
        )
        var lastLoggedRoomState = room.state
        while (isActive) {
            val state = room.state
            if (state != lastLoggedRoomState) {
                Logger.d(
                    TAG,
                    "micSync room.state $lastLoggedRoomState → $state micReq=$micRequestedOn " +
                        "micEn=${room.localParticipant.isMicrophoneEnabled}",
                )
                lastLoggedRoomState = state
            }
            if (state != Room.State.CONNECTED) {
                delay(300)
                continue
            }

            val current = room.localParticipant.isMicrophoneEnabled
            if (current != micRequestedOn) {
                Logger.d(TAG, "micSync calling setMicrophoneEnabled($micRequestedOn) current=$current")
                val ok = runCatching {
                    room.localParticipant.setMicrophoneEnabled(micRequestedOn)
                }.onFailure { e ->
                    Logger.e(TAG, "setMicrophoneEnabled($micRequestedOn) threw", e)
                }.getOrDefault(false)

                if (!ok) {
                    Logger.w(TAG, "setMicrophoneEnabled($micRequestedOn) returned false")
                }
                Logger.d(
                    TAG,
                    "micSync after setMicrophoneEnabled: wanted=$micRequestedOn " +
                        "micEn=${room.localParticipant.isMicrophoneEnabled} ok=$ok",
                )
            }

            if (!micRequestedOn || room.localParticipant.isMicrophoneEnabled) {
                Logger.d(
                    TAG,
                    "micSync effect DONE micReq=$micRequestedOn micEn=${room.localParticipant.isMicrophoneEnabled}",
                )
                break
            }

            Logger.w(
                TAG,
                "micSync mismatch after apply; retry in 800ms micReq=$micRequestedOn " +
                    "micEn=${room.localParticipant.isMicrophoneEnabled}",
            )
            delay(800)
        }
    }

    val participants = rememberParticipants(room).value
    val local = room.localParticipant
    val remote: Participant? = participants.firstOrNull { it != local }
    val hazeState = rememberHazeState(blurEnabled = showInCallControls)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
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
        // Note: we intentionally do NOT blur the whole scene here.
    // Only individual remote preview tiles apply haze when needed.
        if (showInCallControls) {
            CallInlineControlBar(
                room = room,
                micRequestedOn = micRequestedOn,
                onMicRequestedChange = onMicRequestedChange,
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
    var localCamOn by remember { mutableStateOf(false) }
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
    val selfAvatarLabel = self?.displayName?.trim().orEmpty()
    val peerAvatarLabel = ProfileCache.get(session.peerUserId)?.avatarLabelForInitials(self?.id).orEmpty()
    val peerPic = ProfileCache.get(session.peerUserId)?.profilePicture
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        when {
            // Never full-screen your own screen share; show camera or placeholders instead.
            lcRef != null && localCamOn -> {
                Box(Modifier.fillMaxSize()) {
                    VideoTrackView(
                        trackReference = lcRef,
                        modifier = Modifier.fillMaxSize(),
                        room = room,
                        mirror = true,
                        scaleType = ScaleType.Fill,
                    )
                }
            }
            lcRef != null && !localCamOn -> {
                RemoteVideoOffPlaceholder(
                    displayName = selfAvatarLabel,
                    profilePictureUrl = selfPic,
                    audioLevel = localLevel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                RemoteVideoOffPlaceholder(
                    displayName = peerAvatarLabel,
                    profilePictureUrl = peerPic,
                    audioLevel = 0f,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DuoCallParticipantVideos(
    room: Room,
    remote: Participant,
    local: LocalParticipant,
    session: LiveKitConnectSession,
    showInCallControls: Boolean,
) {
    var remoteCamOn by remember { mutableStateOf(true) }
    var localCamOn by remember { mutableStateOf(false) }
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

    val effectiveMain: VideoSlot = (userMain ?: defaultMainSlot()) ?: VideoSlot.None

    LaunchedEffect(userMain, rsRef, rcRef, lsRef, lcRef, remoteCamOn, localCamOn) {
        val u = userMain ?: return@LaunchedEffect
        val ok = when (u) {
            VideoSlot.None -> false
            VideoSlot.RemoteScreen -> rsRef != null
            VideoSlot.RemoteCam -> rcRef != null
            VideoSlot.LocalScreen -> false
            VideoSlot.LocalCam -> lcRef != null
        }
        if (!ok) userMain = null
    }

    fun refFor(slot: VideoSlot): TrackReference? = when (slot) {
        VideoSlot.None -> null
        VideoSlot.RemoteScreen -> rsRef
        VideoSlot.RemoteCam -> rcRef
        VideoSlot.LocalScreen -> lsRef
        VideoSlot.LocalCam -> lcRef
    }

    fun isScreen(slot: VideoSlot): Boolean =
        slot == VideoSlot.RemoteScreen || slot == VideoSlot.LocalScreen

    fun camEnabled(slot: VideoSlot): Boolean = when (slot) {
        VideoSlot.None -> false
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
    val selfAvatarLabel = self?.displayName?.trim().orEmpty()
    val peerAvatarLabel = ProfileCache.get(session.peerUserId)?.avatarLabelForInitials(selfId).orEmpty()
    val youLabel = stringResource(Res.string.message_sender_you)
    val controlsReserve = if (showInCallControls) 168.dp else 0.dp
    val screenShareMainBottomPad = controlsReserve

    fun streamOwner(slot: VideoSlot): CallOwnerUi = when (slot) {
        VideoSlot.RemoteScreen, VideoSlot.RemoteCam ->
            CallOwnerUi(
                name = session.peerDisplayName,
                avatarName = peerAvatarLabel,
                pictureUrl = peerPic,
                level = remoteLevel,
                isSelf = false,
            )
        VideoSlot.LocalCam, VideoSlot.LocalScreen ->
            CallOwnerUi(
                name = youLabel,
                avatarName = selfAvatarLabel,
                pictureUrl = selfPic,
                level = localLevel,
                isSelf = true,
            )
        else ->
            CallOwnerUi(
                name = "",
                avatarName = "",
                pictureUrl = null,
                level = 0f,
                isSelf = false,
            )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SharedTransitionLayout(Modifier.fillMaxSize()) {
            val shared = this@SharedTransitionLayout
            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                ) {
                    val slot = effectiveMain
                    val mainRef = refFor(slot)
                    val screenInsetMod = if (isScreen(slot) && mainRef != null) {
                        Modifier
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(bottom = screenShareMainBottomPad)
                    } else {
                        Modifier
                    }
                    val baseModifier = Modifier.fillMaxSize().then(screenInsetMod)
                    when {
                        slot != VideoSlot.None && mainRef != null && (isScreen(slot) || camEnabled(slot)) -> {
                            CallSlotSharedVideo(
                                room = room,
                                sharedTransitionScope = shared,
                                slot = slot,
                                trackRef = mainRef,
                                mirror = slot == VideoSlot.LocalCam || slot == VideoSlot.LocalScreen,
                                scaleType = if (isScreen(slot)) ScaleType.FitInside else ScaleType.Fill,
                                modifier = baseModifier,
                                innerClipShape = null,
                            )
                        }
                        slot != VideoSlot.None && mainRef != null && !isScreen(slot) && !camEnabled(slot) -> {
                            RemoteVideoOffPlaceholder(
                                displayName = if (slot == VideoSlot.RemoteCam) {
                                    peerAvatarLabel
                                } else {
                                    selfAvatarLabel
                                },
                                profilePictureUrl = if (slot == VideoSlot.RemoteCam) peerPic else selfPic,
                                audioLevel = if (slot == VideoSlot.RemoteCam) remoteLevel else localLevel,
                                modifier = baseModifier,
                            )
                        }
                        else -> {
                            RemoteVideoOffPlaceholder(
                                displayName = peerAvatarLabel,
                                profilePictureUrl = peerPic,
                                audioLevel = remoteLevel,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }

                CallPreviewCluster(
                    room = room,
                    previewSlots = previewSlots,
                    refFor = ::refFor,
                    isScreen = ::isScreen,
                    camEnabled = ::camEnabled,
                    showInCallControls = showInCallControls,
                    onSelectMain = { s -> userMain = s },
                    streamOwner = ::streamOwner,
                    sharedTransitionScope = shared,
                )
            }
        }
    }
}

@Composable
private fun RemoteVideoOffPlaceholder(
    displayName: String,
    profilePictureUrl: String?,
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
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CallPreviewCluster(
    room: Room,
    previewSlots: List<VideoSlot>,
    refFor: (VideoSlot) -> TrackReference?,
    isScreen: (VideoSlot) -> Boolean,
    camEnabled: (VideoSlot) -> Boolean,
    showInCallControls: Boolean,
    onSelectMain: (VideoSlot) -> Unit,
    streamOwner: (VideoSlot) -> CallOwnerUi,
    sharedTransitionScope: SharedTransitionScope,
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

    val posX = remember { Animatable(0f) }
    val posY = remember { Animatable(0f) }
    var layoutReady by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val marginPx = with(density) { 16.dp.toPx() }
        val pipWpx = with(density) { 104.dp.toPx() }
        val pipHpx = with(density) { 138.dp.toPx() }
        val gapPx = with(density) { gap.toPx() }
        val n = previewSlots.size.coerceAtLeast(1)
        val clusterHCol = n * pipHpx + (n - 1).coerceAtLeast(0) * gapPx
        val maxXPxCol = with(density) { maxWidth.toPx() } - endPx - pipWpx
        val maxYPxCol = with(density) { maxHeight.toPx() } - bottomPx - controlsReserve - clusterHCol

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

        if (previewSlots.isEmpty()) return@BoxWithConstraints

        fun moveCluster(amount: Offset) {
            val raw = Offset(posX.value + amount.x, posY.value + amount.y)
            val next = clampCol(raw)
            scope.launch {
                posX.snapTo(next.x)
                posY.snapTo(next.y)
            }
        }

        val dragModifier = Modifier.pointerInput(layoutReady, maxXPxCol, maxYPxCol, marginPx, startPx, topPx) {
            if (!layoutReady) return@pointerInput
            detectDragGestures(
                onDrag = { change, amount ->
                    change.consume()
                    moveCluster(amount)
                },
                onDragEnd = {
                    scope.launch {
                        val t = nearestCornerCol(Offset(posX.value, posY.value))
                        coroutineScope {
                            awaitAll(
                                async { posX.animateTo(t.x, pipReorgSpring) },
                                async { posY.animateTo(t.y, pipReorgSpring) },
                            )
                        }
                    }
                },
                onDragCancel = {
                    scope.launch {
                        val t = nearestCornerCol(Offset(posX.value, posY.value))
                        coroutineScope {
                            awaitAll(
                                async { posX.animateTo(t.x, pipReorgSpring) },
                                async { posY.animateTo(t.y, pipReorgSpring) },
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
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                for (slot in previewSlots) {
                    val owner = streamOwner(slot)
                    PreviewTile(
                        room = room,
                        slot = slot,
                        sharedTransitionScope = sharedTransitionScope,
                        ref = refFor(slot),
                        mirror = slot == VideoSlot.LocalCam || slot == VideoSlot.LocalScreen,
                        showVideo = refFor(slot) != null && (isScreen(slot) || camEnabled(slot)),
                        displayName = owner.name,
                        avatarDisplayName = owner.avatarName,
                        profilePictureUrl = owner.pictureUrl,
                        audioLevel = owner.level,
                        onTap = { onSelectMain(slot) },
                        dragModifier = dragModifier,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalHazeMaterialsApi::class)
@Composable
private fun PreviewTile(
    room: Room,
    slot: VideoSlot,
    sharedTransitionScope: SharedTransitionScope,
    ref: TrackReference?,
    mirror: Boolean,
    showVideo: Boolean,
    displayName: String,
    avatarDisplayName: String,
    profilePictureUrl: String?,
    audioLevel: Float,
    onTap: () -> Unit,
    dragModifier: Modifier = Modifier,
) {
    val tileShape = RoundedCornerShape(16.dp)
    // Only blur the *tile content* when the track isn't available yet.
    // Do not blur the whole call scene; otherwise the opponent video stays blurred.
    val hazeState = rememberHazeState(blurEnabled = ref == null || !showVideo)
    Box(
        modifier = Modifier
            .size(104.dp, 138.dp)
            .clip(tileShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(dragModifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            ),
    ) {
        if (ref != null && showVideo) {
            CallSlotSharedVideo(
                room = room,
                sharedTransitionScope = sharedTransitionScope,
                slot = slot,
                trackRef = ref,
                mirror = mirror,
                scaleType = ScaleType.Fill,
                modifier = Modifier.fillMaxSize(),
                innerClipShape = tileShape,
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    // Placeholder blur only for missing/disabled track.
                    .hazeEffect(state = hazeState, style = HazeMaterials.thick())
                    .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f)),
            )
        }
        if (displayName.isNotBlank()) {
            StreamOwnerChip(
                label = displayName,
                avatarName = avatarDisplayName,
                profilePictureUrl = profilePictureUrl,
                audioLevel = audioLevel,
                showName = true,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
            )
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun CallInlineControlBar(
    room: Room,
    micRequestedOn: Boolean,
    onMicRequestedChange: (Boolean) -> Unit,
    hazeState: HazeState,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val localParticipant = room.localParticipant
    var micOn by remember { mutableStateOf(micRequestedOn) }
    var camOn by remember { mutableStateOf(false) }
    var shareOn by remember { mutableStateOf(false) }

    LaunchedEffect(localParticipant) {
        while (isActive) {
            micOn = localParticipant.isMicrophoneEnabled
            camOn = localParticipant.isCameraEnabled
            shareOn = localParticipant.isScreenShareEnabled
            delay(400)
        }
    }

    LaunchedEffect(micRequestedOn) {
        micOn = micRequestedOn
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
        return NotificationCompat.Builder(context, SCREEN_SHARE_CHANNEL_ID)
            .setContentTitle(updatedTitle)
            .setContentText(updatedText)
            .setSmallIcon(NotificationSmallIcon.resId(context))
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
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.48f)),
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = {
                    val next = !micRequestedOn
                    Logger.d(
                        TAG,
                        "mic button: toggle $micRequestedOn → $next room.state=${room.state} " +
                            "lp.micEn=${localParticipant.isMicrophoneEnabled}",
                    )
                    micOn = next
                    onMicRequestedChange(next)
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
}