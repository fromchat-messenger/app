package ru.fromchat.ui.chat

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import ru.fromchat.api.local.messages.formatChatDateSeparator
import ru.fromchat.chat_date_today
import ru.fromchat.chat_date_yesterday
import ru.fromchat.utils.rememberRegistrationDateFormatStrings
import com.pr0gramm3r101.utils.resetFocus
import com.pr0gramm3r101.utils.supportClipboardManagerImpl
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Logger
import ru.fromchat.Res
import ru.fromchat.presence_recently
import ru.fromchat.api.ApiClient
import ru.fromchat.api.calls.CallStore
import ru.fromchat.api.local.AttachmentMediaLog
import ru.fromchat.api.local.WebSocketManager
import ru.fromchat.api.local.db.store.ConnectionStateStore
import ru.fromchat.api.local.db.store.ConnectionStatus
import ru.fromchat.api.local.db.store.MessageRepository
import ru.fromchat.api.local.db.store.ProfileCache
import ru.fromchat.api.StatusSubscriptionCoordinator
import ru.fromchat.api.local.db.store.UserStatus
import ru.fromchat.api.local.db.store.UserStatusStore
import ru.fromchat.api.local.download.SavableMessageImage
import ru.fromchat.api.local.download.ensureFileDownloadedForSave
import ru.fromchat.api.local.download.isMessageImageFullyLoaded
import ru.fromchat.api.local.download.mimeTypeForImageFilename
import ru.fromchat.api.local.download.rememberSaveMessageFile
import ru.fromchat.api.local.download.rememberSaveMessageImage
import ru.fromchat.api.local.download.resolveImageSourceUri
import ru.fromchat.api.local.download.resolveSavableMessageFile
import ru.fromchat.api.local.download.resolveSavableMessageImage
import ru.fromchat.api.local.messages.generateClientMessageId
import ru.fromchat.api.local.messages.nowMessageTimestampIso
import ru.fromchat.api.local.messages.optimisticMessageIdForClientMessageId
import ru.fromchat.api.local.send.OutboundSendNotifier
import ru.fromchat.api.local.send.OutboundSendProgress
import ru.fromchat.api.local.send.OutgoingMessageCoordinator
import ru.fromchat.api.local.send.prepareOutboundFileForSend
import ru.fromchat.api.local.send.prepareOutboundImageForSend
import ru.fromchat.api.local.workers.AttachmentUploadNotifier
import ru.fromchat.api.local.workers.AttachmentUploadProgress
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.api.schema.websocket.WebSocketMessage
import ru.fromchat.api.schema.websocket.types.WebSocketUpdatesData
import ru.fromchat.back
import ru.fromchat.action_delete_chat
import ru.fromchat.ui.profile.peerIsDeleted
import ru.fromchat.cd_call
import ru.fromchat.chat_scroll_to_bottom_cd
import ru.fromchat.chat_group_label
import ru.fromchat.status_connecting
import ru.fromchat.status_updating
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.chat.utils.AttachmentDownloadVisibility
import ru.fromchat.ui.chat.utils.getImageAspectRatio
import ru.fromchat.ui.chat.utils.getImageDimensions
import ru.fromchat.ui.chat.utils.imageAttachmentKey
import ru.fromchat.ui.chat.utils.visibleMessageIdsInChatList
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.components.SuspendedAccountSupportSheet
import ru.fromchat.utils.NetworkConnectivity
import ru.fromchat.utils.formatLastSeen
import ru.fromchat.utils.haptic.HapticFeedbackEvent
import ru.fromchat.utils.haptic.rememberHapticFeedback
import ru.fromchat.utils.rememberLastSeenFormatStrings
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun ChatScreen(
    panel: ChatPanel,
    currentUserId: Int?,
    modifier: Modifier = Modifier,
    scrollToMessageId: Int? = null,
    onTitleClick: (() -> Unit)? = null,
    hideTitleBarAvatar: Boolean = false,
    onAvatarSlotBounds: ((Rect) -> Unit)? = null,
    onTitleAvatarChange: ((AvatarInfo?) -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedAvatarKey: Any? = null
) {
    var panelState by remember(panel) { mutableStateOf(panel.getState()) }
    
    // Observe state changes
    LaunchedEffect(panel) {
        panel.setOnStateChange { newState ->
            Logger.d("ChatScreen", "State change callback received: messages=${newState.messages.size}, typingUsers=${newState.typingUsers.map { it.username }}")
            // Force state update to trigger recomposition
            panelState = newState.copy() // Ensure new instance
            Logger.d("ChatScreen", "panelState updated: messages=${panelState.messages.size}, typingUsers=${panelState.typingUsers.map { it.username }}")
        }
        // Initial state
        panelState = panel.getState()
    }
    
    LaunchedEffect(panelState.titleAvatar) {
        onTitleAvatarChange?.invoke(panelState.titleAvatar)
    }

    val panelId = panelState.id
    val listState = rememberSaveable(panelId, saver = LazyListState.Saver) {
        LazyListState(0, 0)
    }
    val fakeScrollOffset = remember(panelId) { Animatable(0f) }
    val isNearBottom by remember(panelId) {
        derivedStateOf { listState.isChatNearBottom() }
    }
    val density = LocalDensity.current
    val fallbackMessageHeightPx = remember(density) { with(density) { 80.dp.roundToPx() } }
    val scope = rememberCoroutineScope()
    var highlightMessageId by remember { mutableStateOf<Int?>(null) }
    var highlightFading by remember { mutableStateOf(false) }
    LaunchedEffect(highlightMessageId) {
        val messageId = highlightMessageId ?: return@LaunchedEffect
        highlightFading = false
        delay(1.seconds)
        highlightFading = true
        delay(300.milliseconds)
        if (highlightMessageId == messageId) {
            highlightMessageId = null
            highlightFading = false
        }
    }
    val chatScrollClearancePx = remember { mutableStateOf(0 to 0) }
    val dateToday = stringResource(Res.string.chat_date_today)
    val dateYesterday = stringResource(Res.string.chat_date_yesterday)
    val registrationDateStrings = rememberRegistrationDateFormatStrings()
    val listItems = remember(
        panelState.messages,
        dateToday,
        dateYesterday,
        registrationDateStrings,
    ) {
        buildChatListItems(panelState.messages) { date: LocalDate ->
            formatChatDateSeparator(
                date = date,
                todayLabel = dateToday,
                yesterdayLabel = dateYesterday,
                monthName = registrationDateStrings.monthName,
            )
        }
    }
    var revealedTimestampKeys by rememberSaveable(panelId) {
        mutableStateOf(setOf<String>())
    }
    var hiddenDefaultTimestampKeys by rememberSaveable(panelId) {
        mutableStateOf(setOf<String>())
    }
    // Visit-scoped (not saveable): reopen must re-seed history, never replay enter.
    val openingMessages = remember(panelId) { panel.getState().messages }
    val mountSnapshotKeys = remember(panelId) {
        openingMessages.mapTo(mutableSetOf()) { messageListKey(it) }.toSet()
    }
    val mountSnapshotIdentities = remember(panelId) {
        openingMessages.mapTo(mutableSetOf()) { messageEnterIdentity(it) }.toSet()
    }
    var lastAnimatedMessageKeys by remember(panelId) { mutableStateOf(mountSnapshotKeys) }
    var knownEnterIdentities by remember(panelId) { mutableStateOf(mountSnapshotIdentities) }
    var enterAnimationsSeeded by remember(panelId) {
        mutableStateOf(mountSnapshotKeys.isNotEmpty())
    }
    // Blocks composition-only enter before the first LaunchedEffect seed on this visit.
    var visitEnterSyncComplete by remember(panelId) {
        mutableStateOf(mountSnapshotKeys.isNotEmpty())
    }
    val enterCoordinator = remember(panelId) { MessageEnterCoordinator(scope) }
    val activeEnterAnimation by enterCoordinator.currentItem.collectAsState()
    val pendingNewMessageKeys by enterCoordinator.pendingNewMessageKeys.collectAsState()
    val queuedEnter by enterCoordinator.queuedEnter.collectAsState()
    var previousNewestFingerprint by remember(panelId) { mutableStateOf("") }
    var previousEnterMessageCount by remember(panelId) { mutableIntStateOf(0) }

    LaunchedEffect(panelId) {
        val initial = panel.getState().messages
        if (initial.isEmpty()) return@LaunchedEffect
        val presentKeys = initial.mapTo(mutableSetOf()) { messageListKey(it) }
        val presentIdentities = initial.mapTo(mutableSetOf()) { messageEnterIdentity(it) }
        lastAnimatedMessageKeys = presentKeys
        knownEnterIdentities = presentIdentities
        enterAnimationsSeeded = true
        visitEnterSyncComplete = true
        previousEnterMessageCount = initial.size
        val newest = initial.lastOrNull()
        if (newest != null) {
            previousNewestFingerprint = "${messageListKey(newest)}|${initial.size}"
        }
    }

    LaunchedEffect(panelState.messages) {
        val messages = panelState.messages
        val newest = messages.lastOrNull()
        if (newest == null) {
            // Always reset so the next hydrate is treated as a fresh seed, not a +N append.
            previousNewestFingerprint = ""
            previousEnterMessageCount = 0
            return@LaunchedEffect
        }
        val newestKey = messageListKey(newest)
        val newestIdentity = messageEnterIdentity(newest)
        val fingerprint = "$newestKey|${messages.size}"
        if (fingerprint == previousNewestFingerprint) return@LaunchedEffect

        val previousFingerprint = previousNewestFingerprint
        val previousCount = previousEnterMessageCount
        val sizeDelta = messages.size - previousCount
        val presentKeys = messages.mapTo(mutableSetOf()) { messageListKey(it) }
        val presentIdentities = messages.mapTo(mutableSetOf()) { messageEnterIdentity(it) }
        val newIdentities = presentIdentities - knownEnterIdentities

        fun seedWithoutAnimating(reason: String) {
            Logger.d(
                "EnterAnim",
                "$reason newestKey=${newestKey.take(12)} sizeDelta=$sizeDelta " +
                    "count=${messages.size} newIdentities=${newIdentities.size} " +
                    "newestId=${newest.id}",
            )
            lastAnimatedMessageKeys = lastAnimatedMessageKeys + presentKeys
            knownEnterIdentities = knownEnterIdentities + presentIdentities
            previousNewestFingerprint = fingerprint
            previousEnterMessageCount = messages.size
            enterAnimationsSeeded = true
            visitEnterSyncComplete = true
        }

        // First non-empty load for this visit: seed history without animating.
        // Exception: empty → single live/own message should animate (first bubble in chat).
        if (!enterAnimationsSeeded || previousFingerprint.isEmpty()) {
            val isLiveFirstMessage =
                previousCount == 0 &&
                    messages.size == 1 &&
                    sizeDelta == 1
            if (!isLiveFirstMessage) {
                seedWithoutAnimating("seed_first_load")
                return@LaunchedEffect
            }
            enterAnimationsSeeded = true
            visitEnterSyncComplete = true
        }

        previousNewestFingerprint = fingerprint
        previousEnterMessageCount = messages.size

        val previousNewestKey = previousFingerprint.substringBefore('|')
        // History prepend / cache hydration: newest unchanged, older rows appeared.
        if (newestKey == previousNewestKey) {
            seedWithoutAnimating("skip_newest_unchanged")
            return@LaunchedEffect
        }
        // In-place remap (timestamp / client id attach) — same count, new list key.
        if (sizeDelta == 0) {
            seedWithoutAnimating("skip_remap")
            return@LaunchedEffect
        }
        // Transient shrink: keep identities; drop list keys that left so a restore
        // can re-enqueue only if the identity is truly new (it won't be).
        if (sizeDelta < 0) {
            lastAnimatedMessageKeys = lastAnimatedMessageKeys.intersect(presentKeys)
            knownEnterIdentities = knownEnterIdentities.intersect(presentIdentities)
            Logger.d(
                "EnterAnim",
                "skip_shrink newestKey=${newestKey.take(12)} sizeDelta=$sizeDelta " +
                    "count=${messages.size} newestId=${newest.id}",
            )
            return@LaunchedEffect
        }
        // Bulk replace / multi-message sync / hydrate jump — seed, don't animate.
        if (sizeDelta > 1 || newIdentities.size != 1) {
            seedWithoutAnimating("skip_bulk_or_multi_new")
            return@LaunchedEffect
        }
        // Exactly one new identity, but it isn't the newest row (insert/reorder).
        if (newestIdentity !in newIdentities) {
            seedWithoutAnimating("skip_new_not_newest")
            return@LaunchedEffect
        }
        if (newestKey in mountSnapshotKeys || newestIdentity in mountSnapshotIdentities) {
            seedWithoutAnimating("skip_opening_snapshot")
            return@LaunchedEffect
        }
        if (newestKey in lastAnimatedMessageKeys || newestIdentity in knownEnterIdentities) {
            seedWithoutAnimating("skip_already_known")
            return@LaunchedEffect
        }

        val previous = messages.getOrNull(messages.lastIndex - 1)
        val mode = classifyEnterMode(previous, newest)
        if (mode == EnterMode.None) {
            seedWithoutAnimating("skip_mode_none")
            return@LaunchedEffect
        }
        Logger.d(
            "EnterAnim",
            "will_enqueue newestKey=${newestKey.take(12)} " +
                "prevKey=${previous?.let { messageListKey(it).take(12) }} mode=$mode " +
                "sizeDelta=$sizeDelta newestId=${newest.id}",
        )
        lastAnimatedMessageKeys = lastAnimatedMessageKeys + newestKey
        knownEnterIdentities = knownEnterIdentities + newestIdentity
        enterCoordinator.enqueue(
            PendingEnter(
                newMessageKey = newestKey,
                previousMessageKey = previous?.let { messageListKey(it) },
                mode = mode,
                newDateSeparatorEpochDay = if (mode == EnterMode.NewDay) {
                    messageLocalDate(newest)?.toEpochDays()
                } else {
                    null
                },
            ),
        )
    }

    val scrollToChatMessage: (Int) -> Unit = { messageId ->
        val lazyIndex = lazyIndexForMessageId(listItems, messageId)
        if (lazyIndex != null) {
            scope.launch {
                val (topClearancePx, bottomClearancePx) = chatScrollClearancePx.value
                listState.scrollChatMessageToCenter(
                    lazyIndex,
                    topClearancePx,
                    bottomClearancePx,
                    fallbackMessageHeightPx,
                )
                highlightMessageId = messageId
            }
        }
    }
    val saveMessageImage = rememberSaveMessageImage { /* best-effort */ }
    val saveMessageFile = rememberSaveMessageFile { /* best-effort */ }
    val haptic = rememberHapticFeedback()
    val navController = LocalNavController.current
    val runNav = rememberChatNavigationGate(navController, animatedVisibilityScope)
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val profileUserId = panelState.profileUserId
    val hazeState = rememberHazeState(
        blurEnabled = !(panelState.isLoading && panelState.messages.isEmpty())
    )

    val currentTypingUsers = panelState.typingUsers // Directly use from panelState
    val statusMap by UserStatusStore.status.collectAsState()
    val connectionStatus by ConnectionStateStore.status.collectAsState()
    val online by NetworkConnectivity.isOnline.collectAsState(initial = true)
    val suspensionState by ApiClient.suspensionState.collectAsState()
    val isReadOnly = suspensionState.isSuspended
    val dmRecipientId = panel.getRecipientId()
    var peerDeleted by remember(dmRecipientId) { mutableStateOf(false) }
    val deleteChatLabel = stringResource(Res.string.action_delete_chat)
    LaunchedEffect(dmRecipientId) {
        val userId = dmRecipientId ?: return@LaunchedEffect
        peerDeleted = peerIsDeleted(userId = userId, currentUserId = currentUserId)
        if (!peerDeleted) {
            runCatching { ApiClient.getProfileById(userId) }.onSuccess { profile ->
                ProfileCache.applyServerProfile(profile, force = false)
                UserStatusStore.update(profile.id, profile.online, profile.lastSeen)
                peerDeleted = peerIsDeleted(
                    userId = userId,
                    currentUserId = currentUserId,
                    deleted = profile.deleted,
                    suspended = profile.suspended,
                    username = profile.username,
                )
            }
        }
    }
    val lastSeenFormat = rememberLastSeenFormatStrings()
    var showSuspendedSupportSheet by remember { mutableStateOf(false) }
    val statusConnecting = stringResource(Res.string.status_connecting)
    val statusUpdating = stringResource(Res.string.status_updating)
    val presenceRecently = stringResource(Res.string.presence_recently)
    val chatGroupLabel = stringResource(Res.string.chat_group_label)
    val scrollToBottomCd = stringResource(Res.string.chat_scroll_to_bottom_cd)
    val cdCall = stringResource(Res.string.cd_call)
    LaunchedEffect(currentTypingUsers) {
        Logger.d("ChatScreen", "currentTypingUsers updated (from panelState): ${currentTypingUsers.map { it.username }}")
    }

    val subtitleKey = when {
        connectionStatus == ConnectionStatus.UPDATING -> "updating"
        !online || (connectionStatus == ConnectionStatus.CONNECTING && !WebSocketManager.isConnected) -> "connecting"
        currentTypingUsers.isNotEmpty() -> "typing"
        panel.usesPublicGroupSubtitle -> {
            if (panelState.publicGroupMetaLoading || panelState.publicGroupMemberCount == null) {
                "group"
            } else {
                "members:${panelState.publicGroupMemberCount}"
            }
        }
        panelState.profileUserId != null -> {
            val peerId = panelState.profileUserId!!
            val userStatus = statusMap[peerId]
                ?: ProfileCache.get(peerId)?.let { profile ->
                    UserStatus(online = profile.online, lastSeen = profile.lastSeen)
                }
            val statusText = userStatus?.let { status ->
                formatLastSeen(status.online, status.lastSeen, lastSeenFormat)
                    .ifEmpty { if (!status.online) presenceRecently else "" }
            }.orEmpty()
            if (statusText.isNotEmpty()) {
                "presence:$statusText"
            } else {
                ""
            }
        }
        else -> ""
    }

    LaunchedEffect(isReadOnly) {
        if (!isReadOnly) {
            showSuspendedSupportSheet = false
        }
    }

    // Subscribe to other user's status when DM is visible; re-subscribe after reconnect
    LaunchedEffect(panelState.profileUserId, connectionStatus) {
        val userId = panelState.profileUserId ?: return@LaunchedEffect
        if (connectionStatus == ConnectionStatus.CONNECTED) {
            StatusSubscriptionCoordinator.acquire(userId)
        }
        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            StatusSubscriptionCoordinator.release(userId)
        }
    }

    // Scroll to specific message once when requested (e.g., notification / deep link).
    LaunchedEffect(scrollToMessageId) {
        val messageId = scrollToMessageId ?: return@LaunchedEffect
        snapshotFlow { lazyIndexForMessageId(listItems, messageId) }
            .filterNotNull()
            .first()
        scrollToChatMessage(messageId)
    }

    // UI state
    var inputText by rememberSaveable { mutableStateOf("") }
    var replyTo by rememberSaveable { mutableStateOf<Message?>(null) }
    var editingMessage by rememberSaveable { mutableStateOf<Message?>(null) }
    var contextMenuState by remember {
        mutableStateOf(
            ContextMenuState(
                isOpen = false,
                message = null,
                position = IntOffset(0, 0)
            )
        )
    }
    var expandedImage by remember { mutableStateOf<Pair<Message, Int>?>(null) }
    var isImageClosing by remember { mutableStateOf(false) }
    val imageThumbBounds = remember { mutableStateMapOf<String, Rect>() }
    val expandedImageKey = expandedImage?.let { (msg, idx) ->
        val cid = msg.client_message_id?.trim().orEmpty()
        if (cid.isNotEmpty()) "img_${cid}_$idx" else "img_${msg.id}_$idx"
    }

    LaunchedEffect(listState, panelState.messages, expandedImage) {
        try {
            snapshotFlow {
                visibleMessageIdsInChatList(
                    listState = listState,
                    messages = panelState.messages,
                    extraMessageId = expandedImage?.first?.id,
                )
            }
                .distinctUntilChanged()
                .collect { ids -> AttachmentDownloadVisibility.setVisibleMessageIds(ids) }
        } finally {
            AttachmentDownloadVisibility.setVisibleMessageIds(emptySet())
        }
    }

    // Collect WebSocket messages
    LaunchedEffect(Unit) {
        WebSocketManager.messages.collect { message ->
            Logger.d("ChatScreen", "Received WebSocket message: type=${message.type}, data=${message.data != null}")
            when (message.type) {
                "updates" -> {
                    // Handle batched updates
                    Logger.d("ChatScreen", "Processing updates message")
                    val data = message.data
                    if (data == null) {
                        Logger.w("ChatScreen", "Updates message has no data, skipping")
                        return@collect
                    }
                    Logger.d("ChatScreen", "Updates message has data, parsing...")
                    val json = ApiClient.json
                    try {
                        Logger.d("ChatScreen", "Parsing updates message")
                        val updatesMessage = json.decodeFromJsonElement<WebSocketUpdatesData>(data)
                        Logger.d("ChatScreen", "Updates message parsed: ${updatesMessage.updates.size} updates")
                        // Process each update in the batch
                        updatesMessage.updates.forEach { update ->
                            Logger.d("ChatScreen", "Processing update: type=${update.type}, data=${update.data != null}")
                            val wsMessage = WebSocketMessage(
                                type = update.type,
                                data = update.data
                            )
                            when (update.type) {
                                "statusUpdate" -> update.data?.jsonObject?.let { data ->
                                    val userId = data["userId"]?.jsonPrimitive?.content?.toIntOrNull()
                                    val online = data["online"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                                    val lastSeen = data["lastSeen"]?.jsonPrimitive?.content
                                    if (userId != null) UserStatusStore.update(userId, online, lastSeen)
                                }
                                "newMessage", "messageEdited", "messageDeleted", "sendMessage",
                                "dmNew", "dmEdited", "dmDeleted",
                                "typing", "stopTyping", "dmTyping", "stopDmTyping",
                                "reactionUpdate", "registeredUserCount" -> {
                                    Logger.d("ChatScreen", "handleWebSocketMessage for ${update.type}")
                                    try {
                                        panel.handleWebSocketMessage(wsMessage)
                                    } catch (e: Exception) {
                                        Logger.e("ChatScreen", "Error handling WebSocket message: ${e.message}", e)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Logger.e("ChatScreen", "Error parsing updates message: ${e.message}", e)
                        e.printStackTrace()
                    }
                }
                "statusUpdate" -> message.data?.jsonObject?.let { data ->
                    val userId = data["userId"]?.jsonPrimitive?.content?.toIntOrNull()
                    val online = data["online"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                    val lastSeen = data["lastSeen"]?.jsonPrimitive?.content
                    if (userId != null) UserStatusStore.update(userId, online, lastSeen)
                }
                "newMessage", "messageEdited", "messageDeleted", "dmNew", "dmEdited", "dmDeleted",
                "dmTyping", "stopDmTyping", "typing", "stopTyping", "reactionUpdate",
                "registeredUserCount" -> {
                    scope.launch {
                        panel.handleWebSocketMessage(message)
                    }
                }
                "sendMessage" -> {
                    scope.launch {
                        panel.handleWebSocketMessage(message)
                    }
                }
                "call_signaling" -> {
                    Logger.d(
                        "ChatScreen",
                        "call_signaling (also dispatched to App global → CallStore); skipping panel",
                    )
                }
                else -> {
                    Logger.w("ChatScreen", "Unhandled top-level WebSocket message type: ${message.type}")
                }
            }
        }
    }

    // Scroll to bottom when new messages arrive.
    // LazyColumn uses reverseLayout + chronological messages asReversed(): index 0 is bottom inset, 1..n newest→oldest.
    // - Initial: after one frame, scrollToItem(0) so the first list composition/layout is not merged with scroll in one VSYNC.
    // - Later: same anchor; "near bottom" = smallest visible index is near 0.
    // rememberSaveable(panelId): survives navigation (e.g. profile) so we do not re-run initial scrollToItem(0) and discard scroll.
    var didInitialScroll by rememberSaveable(panelId) { mutableStateOf(false) }
    // LaunchedEffect restarts when returning from profile even if message keys are unchanged; skip auto-scroll unless the list actually changed.
    var previousMessageFingerprint by rememberSaveable(panelId) { mutableStateOf("") }
    var previousMessageCount by rememberSaveable(panelId) { mutableStateOf(-1) }
    LaunchedEffect(
        panelState.messages.size,
        panelState.messages.lastOrNull()?.client_message_id,
        panelState.messages.lastOrNull()?.uploadProgress,
        panelState.messages.lastOrNull()?.pendingFileAspectRatio,
    ) {
        if (panelState.messages.isEmpty()) return@LaunchedEffect

        val newCount = panelState.messages.size
        if (previousMessageCount >= 0 && newCount < previousMessageCount) {
            previousMessageCount = newCount
            val lastMessage = panelState.messages.lastOrNull()
            previousMessageFingerprint =
                "${newCount}|${lastMessage?.client_message_id}|${lastMessage?.uploadProgress}|${lastMessage?.pendingFileAspectRatio}"
            return@LaunchedEffect
        }
        previousMessageCount = newCount

        val lastMessage = panelState.messages.lastOrNull()
        val fingerprint = "${newCount}|${lastMessage?.client_message_id}|${lastMessage?.uploadProgress}|${lastMessage?.pendingFileAspectRatio}"

        if (!didInitialScroll) {
            didInitialScroll = true
            withFrameNanos { }
            listState.scrollToItem(0)
            previousMessageFingerprint = fingerprint
            return@LaunchedEffect
        }

        if (fingerprint == previousMessageFingerprint) return@LaunchedEffect
        previousMessageFingerprint = fingerprint

        val lastIsOurs = lastMessage?.user_id == currentUserId
        val isNearBottom = listState.isChatNearBottom()
        if (lastIsOurs || isNearBottom) {
            if (listState.layoutInfo.visibleItemsInfo.isNotEmpty()) {
                delay(100.milliseconds)
                listState.animateScrollToItem(0)
                delay(150.milliseconds)
                listState.animateScrollToItem(0)
            }
        }
    }

    val clipboard = supportClipboardManagerImpl

    LaunchedEffect(contextMenuState.isOpen, contextMenuState.message, panelState.messages, currentUserId, isReadOnly) {
        if (!contextMenuState.isOpen) return@LaunchedEffect
        val menuMessage = contextMenuState.message ?: return@LaunchedEffect
        val cid = menuMessage.client_message_id?.trim().orEmpty()
        val liveMessage = panelState.messages.find { msg ->
            if (cid.isNotEmpty()) {
                msg.client_message_id?.trim() == cid
            } else {
                msg.id == menuMessage.id
            }
        }

        if (liveMessage == null) {
            contextMenuState = contextMenuState.copy(isOpen = false, message = null)
            return@LaunchedEffect
        }
        val liveAuthor = liveMessage.user_id == currentUserId
        val liveFp = messageContextMenuFingerprint(
            liveMessage,
            liveAuthor,
            liveAuthor || currentUserId == 1,
            isReadOnly,
        )
        val menuAuthor = menuMessage.user_id == currentUserId
        val menuFp = messageContextMenuFingerprint(
            menuMessage,
            menuAuthor,
            menuAuthor || currentUserId == 1,
            isReadOnly,
        )
        if (menuFp != liveFp || liveMessage.id != menuMessage.id) {
            contextMenuState = contextMenuState.copy(message = liveMessage)
        }
    }

    LaunchedEffect(panel) {
        OutboundSendNotifier.progressFlow.collect { progress ->
            when (progress) {
                is OutboundSendProgress.Pending ->
                    panel.updateMessageByClientMessageId(progress.clientMessageId) {
                        it.copy(uploadError = null)
                    }
                is OutboundSendProgress.Success ->
                    panel.handleMessageConfirmed(progress.clientMessageId, progress.message)
                is OutboundSendProgress.Failed ->
                    panel.updateMessageByClientMessageId(progress.clientMessageId) {
                        it.copy(uploadError = progress.error)
                    }
            }
        }
    }

    LaunchedEffect(panel) {
        if (panel.supportsAttachments) {
            AttachmentUploadNotifier.progressFlow.collect { progress ->
                when (progress) {
                    is AttachmentUploadProgress.Pending -> {
                        AttachmentMediaLog.send(
                            "ui_progress_pending",
                            "job" to progress.jobId.take(12),
                        )
                        panel.updateMessageByClientMessageId(progress.jobId) {
                            it.copy(uploadProgress = 0, uploadError = null)
                        }
                    }

                    is AttachmentUploadProgress.InProgress -> {
                        AttachmentMediaLog.send(
                            "ui_progress_apply",
                            "job" to progress.jobId.take(12),
                            "pct" to progress.percent,
                        )
                        panel.updateMessageByClientMessageId(progress.jobId) {
                            it.copy(uploadProgress = progress.percent, uploadError = null)
                        }
                    }

                    is AttachmentUploadProgress.Success -> {
                        AttachmentMediaLog.send(
                            "ui_progress_success",
                            "job" to progress.jobId.take(12),
                        )
                        panel.updateMessageByClientMessageId(progress.jobId) {
                            it.copy(uploadProgress = null)
                        }
                    }

                    is AttachmentUploadProgress.Failed -> {
                        if (progress.error == "Cancelled") return@collect
                        AttachmentMediaLog.send(
                            "ui_progress_failed",
                            "job" to progress.jobId.take(12),
                            "err" to progress.error,
                        )
                        panel.updateMessageByClientMessageId(progress.jobId) {
                            it.copy(uploadProgress = null, uploadError = progress.error)
                        }
                    }
                }
            }
        }
    }


    Box(modifier = Modifier.fillMaxSize()) {
        SuspendedAccountSupportSheet(
            isVisible = isReadOnly && showSuspendedSupportSheet,
            onDismissRequest = { showSuspendedSupportSheet = false }
        )

        Scaffold(
            modifier = modifier.fillMaxSize(),
            // Do not apply safeDrawing top (or other sides) to content — we handle status bars on the
            // floating header Box only; avoids extra top inset on the fade / list and duplicate “padding”.
            contentWindowInsets = WindowInsets.navigationBars,
            bottomBar = {
                Column(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.ime)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .hazeEffect(state = hazeState, style = HazeMaterials.thin()) {
                            progressive = HazeProgressive.verticalGradient(
                                startIntensity = 0f,
                                endIntensity = 1f,
                            )
                        }
                ) {
                    if (peerDeleted && dmRecipientId != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        val messages = runCatching {
                                            MessageRepository.loadDmMessages(dmRecipientId)
                                        }.getOrDefault(emptyList()).filter { it.id > 0 }
                                        messages.forEach { msg ->
                                            runCatching { ApiClient.deleteDm(msg.id, dmRecipientId) }
                                        }
                                        runCatching { MessageRepository.deleteDmConversation(dmRecipientId) }
                                        navController.popBackStack()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                ),
                            ) {
                                Text(deleteChatLabel)
                            }
                        }
                    } else {
                    ChatInput(
                        text = inputText,
                        onTextChange = { inputText = it },
                        currentUserId = currentUserId,
                        onSend = { text, attachments ->
                            if (editingMessage != null) {
                                scope.launch {
                                    panel.handleEditMessage(editingMessage!!.id, text)
                                    editingMessage = null
                                }
                            } else {
                                scope.launch {
                                    val replyToId = replyTo?.id?.takeIf { it > 0 }
                                    val recipientId = panel.getRecipientId()
                                    if (attachments.isNotEmpty() && panel.supportsAttachments) {
                                        val plaintext = text.ifBlank { "" }
                                        attachments.forEach { att ->
                                            val jobId = generateClientMessageId()
                                            val tempId = optimisticMessageIdForClientMessageId(jobId)
                                            val isImage = att.isImage
                                            val sendT0 = AttachmentMediaLog.nowMs()
                                            AttachmentMediaLog.send(
                                                "tap_send",
                                                "job" to jobId.take(12),
                                                "image" to isImage,
                                                "file" to att.filename,
                                                "uri" to att.uri.take(64),
                                                "public" to (recipientId == null),
                                            )
                                            scope.launch(Dispatchers.Default) {
                                                val imageDimensions = if (isImage) {
                                                    getImageDimensions(att.uri)
                                                } else {
                                                    null
                                                }
                                                val aspectRatio = imageDimensions?.let { (w, h) ->
                                                    if (h > 0) w.toFloat() / h.toFloat() else null
                                                }?.takeIf { it > 0f }
                                                    ?: if (isImage) {
                                                        getImageAspectRatio(att.uri)?.takeIf { it > 0f }
                                                    } else {
                                                        null
                                                    }
                                                AttachmentMediaLog.send(
                                                    "staging_start",
                                                    "job" to jobId.take(12),
                                                    "dims" to imageDimensions,
                                                    "aspect" to aspectRatio,
                                                    "elapsedMs" to (AttachmentMediaLog.nowMs() - sendT0),
                                                )
                                                val staged = if (isImage) {
                                                    prepareOutboundImageForSend(
                                                        clientMessageId = jobId,
                                                        sourceUri = att.uri,
                                                        optimisticMessageId = tempId,
                                                        aspectRatio = aspectRatio,
                                                    )
                                                } else {
                                                    prepareOutboundFileForSend(
                                                        clientMessageId = jobId,
                                                        sourceUri = att.uri,
                                                        optimisticMessageId = tempId,
                                                        displayFilename = att.filename,
                                                    )
                                                }
                                                if (staged == null) {
                                                    AttachmentMediaLog.send(
                                                        "staging_failed",
                                                        "job" to jobId.take(12),
                                                        "elapsedMs" to (AttachmentMediaLog.nowMs() - sendT0),
                                                    )
                                                    return@launch
                                                }
                                                AttachmentMediaLog.send(
                                                    "staging_ok",
                                                    "job" to jobId.take(12),
                                                    "bytes" to staged.sizeBytes,
                                                    "preview" to (staged.previewUri?.take(48) ?: "null"),
                                                    "staged" to staged.stagedUri.take(48),
                                                    "elapsedMs" to (AttachmentMediaLog.nowMs() - sendT0),
                                                )
                                                val uploadUri = staged.stagedUri
                                                val previewUri = staged.previewUri?.takeIf { it.isNotBlank() }
                                                    ?: staged.stagedUri
                                                val optimisticMessage = Message(
                                                    id = tempId,
                                                    user_id = currentUserId ?: -1,
                                                    content = plaintext,
                                                    timestamp = nowMessageTimestampIso(),
                                                    is_read = false,
                                                    is_edited = false,
                                                    username = ApiClient.user?.username.orEmpty(),
                                                    displayName = ApiClient.user?.displayName,
                                                    profile_picture = null,
                                                    verified = null,
                                                    reply_to = replyTo,
                                                    client_message_id = jobId,
                                                    reactions = null,
                                                    files = null,
                                                    pendingFileUri = previewUri,
                                                    pendingFilename = att.filename,
                                                    uploadJobId = jobId,
                                                    uploadProgress = 0,
                                                    pendingFileAspectRatio = staged.aspectRatio ?: aspectRatio,
                                                    fileDimensions = imageDimensions?.let { listOf(it) },
                                                    fileSizes = staged.sizeBytes.takeIf { it > 0L }?.let { listOf(it) },
                                                )
                                                withContext(Dispatchers.Main) {
                                                    panel.addMessage(optimisticMessage)
                                                    AttachmentMediaLog.send(
                                                        "optimistic_added",
                                                        "job" to jobId.take(12),
                                                        "tempId" to tempId,
                                                        "pendingUri" to previewUri.take(48),
                                                        "elapsedMs" to (AttachmentMediaLog.nowMs() - sendT0),
                                                    )
                                                }
                                                AttachmentUploadNotifier.emit(
                                                    AttachmentUploadProgress.InProgress(
                                                        jobId = jobId,
                                                        percent = 1,
                                                        filename = att.filename,
                                                    ),
                                                    messageLabel = plaintext,
                                                )
                                                if (recipientId != null) {
                                                    AttachmentMediaLog.send(
                                                        "enqueue_dm",
                                                        "job" to jobId.take(12),
                                                        "peer" to recipientId,
                                                        "elapsedMs" to (AttachmentMediaLog.nowMs() - sendT0),
                                                    )
                                                    OutgoingMessageCoordinator.enqueueDmAttachment(
                                                        recipientId = recipientId,
                                                        plaintext = plaintext,
                                                        clientMessageId = jobId,
                                                        replyToId = replyToId,
                                                        fileUri = uploadUri,
                                                        filename = att.filename,
                                                        optimisticMessage = optimisticMessage,
                                                        aspectRatio = staged.aspectRatio ?: aspectRatio,
                                                        fileSizeBytes = staged.sizeBytes,
                                                    )
                                                } else {
                                                    AttachmentMediaLog.send(
                                                        "enqueue_public",
                                                        "job" to jobId.take(12),
                                                        "elapsedMs" to (AttachmentMediaLog.nowMs() - sendT0),
                                                    )
                                                    OutgoingMessageCoordinator.enqueuePublicAttachment(
                                                        content = plaintext,
                                                        clientMessageId = jobId,
                                                        replyToId = replyToId,
                                                        fileUri = uploadUri,
                                                        filename = att.filename,
                                                        optimisticMessage = optimisticMessage,
                                                        aspectRatio = staged.aspectRatio ?: aspectRatio,
                                                        fileSizeBytes = staged.sizeBytes,
                                                    )
                                                }
                                                AttachmentMediaLog.send(
                                                    "enqueue_done",
                                                    "job" to jobId.take(12),
                                                    "elapsedMs" to (AttachmentMediaLog.nowMs() - sendT0),
                                                )
                                            }
                                        }
                                    } else if (text.isNotBlank()) {
                                        panel.sendMessageWithImmediateDisplay(text, replyToId, replyTo)
                                    }
                                    replyTo = null
                                    haptic(HapticFeedbackEvent.MessageSent)
                                }
                            }
                            inputText = ""
                        },
                        typingHandler = panel.getTypingHandler(),
                        replyTo = replyTo,
                        editingMessage = editingMessage,
                        onClearReply = { replyTo = null },
                        onClearEdit = {
                            editingMessage = null
                            inputText = ""
                        },
                        hazeState = hazeState,
                        supportsAttachments = panel.supportsAttachments,
                        isReadOnly = isReadOnly,
                        onReadOnlyMessageClick = {
                            if (isReadOnly) {
                                showSuspendedSupportSheet = true
                            }
                        }
                    )
                    }
                }
            }
        ) { innerPadding ->
            val density = LocalDensity.current
            val statusBarTopDp = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
            val floatingHeaderClearance =
                statusBarTopDp + 64.dp + 12.dp + ChatFloatingHeaderBottomArcRadius
            SideEffect {
                chatScrollClearancePx.value = with(density) {
                    floatingHeaderClearance.roundToPx() to innerPadding.calculateBottomPadding().roundToPx()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            if (contextMenuState.isOpen) {
                                contextMenuState = contextMenuState.copy(isOpen = false)
                            }
                        }
                    }
            ) {
                if (panelState.isLoading && panelState.messages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationY = fakeScrollOffset.value
                                }
                                .background(MaterialTheme.colorScheme.background)
                                .hazeSource(hazeState),
                            userScrollEnabled = !contextMenuState.isOpen,
                            reverseLayout = true,
                        ) {
                        item {
                            Spacer(
                                Modifier.height(
                                    innerPadding.calculateBottomPadding() + 12.dp,
                                ),
                            )
                        }

                        items(
                            items = listItems,
                            key = { item ->
                                when (item) {
                                    is ChatListItem.DateSeparator -> "d:${item.epochDay}"
                                    is ChatListItem.MessageRow -> messageListKey(item.message)
                                }
                            }
                        ) { item ->
                            when (item) {
                                is ChatListItem.DateSeparator -> {
                                    ChatDateSeparator(
                                        label = item.label,
                                        enterAnimationRole = resolveDateSeparatorEnterRole(
                                            item.epochDay,
                                            activeEnterAnimation,
                                        ),
                                        modifier = Modifier.padding(vertical = 12.dp),
                                    )
                                }
                                is ChatListItem.MessageRow -> {
                                    val message = item.message
                                    var tapPositionInRoot by remember {
                                        mutableStateOf(IntOffset(0, 0))
                                    }
                                    val messageKey = timestampGroupKey(message)
                                    val listKey = messageListKey(message)
                                    // Keep the newest bubble as NewMessage before enqueue runs.
                                    val newest = panelState.messages.lastOrNull()
                                    val newestListKey = newest?.let { messageListKey(it) }
                                    val newestEnterIdentity = newest?.let { messageEnterIdentity(it) }
                                    val compositionPendingNewest =
                                        visitEnterSyncComplete &&
                                            enterAnimationsSeeded &&
                                            newestListKey != null &&
                                            newestEnterIdentity != null &&
                                            newestListKey !in mountSnapshotKeys &&
                                            newestEnterIdentity !in mountSnapshotIdentities &&
                                            newestListKey !in lastAnimatedMessageKeys &&
                                            newestEnterIdentity !in knownEnterIdentities &&
                                            newestListKey !in pendingNewMessageKeys &&
                                            activeEnterAnimation?.newMessageKey != newestListKey
                                    val pendingKeysForRole =
                                        if (compositionPendingNewest) {
                                            pendingNewMessageKeys + newestListKey
                                        } else {
                                            pendingNewMessageKeys
                                        }
                                    val enterRole = resolveMessageEnterRole(
                                        listKey,
                                        activeEnterAnimation,
                                        pendingKeysForRole,
                                        queuedEnter,
                                    )
                                    // Grouping flips isLastInGroup as soon as the new row exists.
                                    // Hold the previous bubble's timestamp until PreviousLast is
                                    // applied so fade runs in parallel with the enter spring —
                                    // not before it (that looked like "fade, wait, then animate").
                                    val holdTimestampForEnter = run {
                                        if (item.group.isLastInGroup) return@run false
                                        if (enterRole == EnterAnimationRole.PreviousLast) {
                                            return@run true
                                        }
                                        val enter = queuedEnter ?: activeEnterAnimation?.let {
                                            PendingEnter(
                                                newMessageKey = it.newMessageKey,
                                                previousMessageKey = it.previousMessageKey,
                                                mode = it.mode,
                                                newDateSeparatorEpochDay =
                                                    it.newDateSeparatorEpochDay,
                                            )
                                        }
                                        if (
                                            enter != null &&
                                            enter.mode == EnterMode.ExtendGroup &&
                                            enter.previousMessageKey == listKey
                                        ) {
                                            return@run true
                                        }
                                        if (!compositionPendingNewest) return@run false
                                        val previous = panelState.messages
                                            .getOrNull(panelState.messages.lastIndex - 1)
                                        previous != null &&
                                            messageListKey(previous) == listKey &&
                                            classifyEnterMode(previous, newest!!) ==
                                                EnterMode.ExtendGroup
                                    }
                                    val showTimestamp = when {
                                        messageKey in revealedTimestampKeys -> true
                                        messageKey in hiddenDefaultTimestampKeys -> false
                                        item.group.isLastInGroup || holdTimestampForEnter -> true
                                        else -> false
                                    }

                                    val isPendingOwnTextSend =
                                        message.id < 0 &&
                                            message.files.isNullOrEmpty() &&
                                            message.user_id == currentUserId
                                    MessageItem(
                                        message = message,
                                        isAuthor = message.user_id == currentUserId,
                                        group = item.group,
                                        showTimestamp = showTimestamp,
                                        onBubbleTap = if (isPendingOwnTextSend) {
                                            null
                                        } else {
                                            {
                                                if (item.group.isLastInGroup &&
                                                    messageKey !in revealedTimestampKeys
                                                ) {
                                                // Default-visible last bubble: tap hides.
                                                hiddenDefaultTimestampKeys =
                                                    if (messageKey in hiddenDefaultTimestampKeys) {
                                                        hiddenDefaultTimestampKeys - messageKey
                                                    } else {
                                                        hiddenDefaultTimestampKeys + messageKey
                                                    }
                                            } else {
                                                revealedTimestampKeys =
                                                    if (messageKey in revealedTimestampKeys) {
                                                        revealedTimestampKeys - messageKey
                                                    } else {
                                                        revealedTimestampKeys + messageKey
                                                    }
                                                hiddenDefaultTimestampKeys =
                                                    hiddenDefaultTimestampKeys - messageKey
                                            }
                                            }
                                        },
                                        enterAnimationRole = if (!visitEnterSyncComplete) {
                                            EnterAnimationRole.None
                                        } else {
                                            enterRole
                                        },
                                        isExiting = messageDissolveKey(message) in
                                            panelState.dissolvingMessageKeys,
                                        onExitAnimationFinished = {
                                            panel.finishDissolveAnimation(messageDissolveKey(message))
                                        },
                                        modifier = Modifier.padding(top = item.spacingAbove),
                                        isContextMenuOpen = contextMenuState.isOpen,
                                        isContextMenuForThisMessage =
                                            contextMenuState.isOpen && run {
                                                val menu = contextMenuState.message
                                                    ?: return@run false
                                                val cid = menu.client_message_id?.trim().orEmpty()
                                                if (cid.isNotEmpty()) {
                                                    message.client_message_id?.trim() == cid
                                                } else {
                                                    menu.id == message.id
                                                }
                                            },
                                        onLongPress = {
                                            if (isReadOnly) {
                                                return@MessageItem
                                            }
                                            haptic(HapticFeedbackEvent.ContextMenuOpened)
                                            contextMenuState = ContextMenuState(
                                                isOpen = true,
                                                message = message,
                                                position = tapPositionInRoot
                                            )
                                        },
                                        onTapPosition = { offset ->
                                            tapPositionInRoot =
                                                IntOffset(offset.x.toInt(), offset.y.toInt())
                                        },
                                        onUsernameClick =
                                            if (panel.supportsNavigateToSenderProfile &&
                                                message.user_id != currentUserId &&
                                                message.user_id > 0
                                            ) {
                                                {
                                                    ProfileCache.mergePreviewFromPublicMessage(
                                                        message,
                                                    )
                                                    navController.navigate(
                                                        "profile/${message.user_id}",
                                                    )
                                                }
                                            } else {
                                                null
                                            },
                                        onImageClick = { msg, idx ->
                                            resetFocus(keyboardController, focusManager)
                                            expandedImage = msg to idx
                                        },
                                        onImageBounds = { key, rect ->
                                            imageThumbBounds[key] = rect
                                        },
                                        expandedImageKey = expandedImageKey,
                                        isImageClosing = isImageClosing,
                                        showUsername = panel.showUsernamesInMessages,
                                        currentUserId = currentUserId,
                                        onCancelOutboundAttachment = { msg ->
                                            scope.launch { panel.cancelQueuedMessage(msg) }
                                        },
                                        onRetryOutboundAttachment = { msg ->
                                            val cid = msg.client_message_id?.trim().orEmpty()
                                            if (cid.isNotEmpty()) {
                                                panel.updateMessageByClientMessageId(cid) {
                                                    it.copy(
                                                        uploadError = null,
                                                        uploadProgress = 0,
                                                    )
                                                }
                                                OutgoingMessageCoordinator
                                                    .retryDmAttachmentUpload(cid)
                                            }
                                        },
                                        onReplyClick = scrollToChatMessage,
                                        highlightMessageId = highlightMessageId,
                                        highlightFading = highlightFading,
                                    )
                                }
                            }
                        }

                        item { Spacer(modifier.height(floatingHeaderClearance)) }
                        }

                        val showScrollToBottomFab = !isNearBottom &&
                            panelState.messages.isNotEmpty() &&
                            !contextMenuState.isOpen
                        AnimatedVisibility(
                            visible = showScrollToBottomFab,
                            enter = fadeIn(
                                animationSpec = tween(
                                    durationMillis = 260,
                                    easing = FastOutSlowInEasing,
                                ),
                            ) + scaleIn(
                                initialScale = 0.7f,
                                animationSpec = tween(
                                    durationMillis = 260,
                                    easing = FastOutSlowInEasing,
                                ),
                            ),
                            exit = fadeOut(
                                animationSpec = tween(
                                    durationMillis = 200,
                                    easing = FastOutSlowInEasing,
                                ),
                            ) + scaleOut(
                                targetScale = 0.7f,
                                animationSpec = tween(
                                    durationMillis = 200,
                                    easing = FastOutSlowInEasing,
                                ),
                            ),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .zIndex(2f)
                                .padding(
                                    end = 13.dp,
                                    bottom = innerPadding.calculateBottomPadding() + 12.dp,
                                ),
                        ) {
                            ChatScrollToBottomButton(
                                onClick = {
                                    scope.launch {
                                        // Snap list to bottom first so content is correct.
                                        listState.scrollChatToBottom()

                                        // Fake a short smooth scroll by translating the whole chat
                                        // surface from a small offset back to rest.
                                        fakeScrollOffset.snapTo(64f)
                                        fakeScrollOffset.animateTo(
                                            targetValue = 0f,
                                            animationSpec = tween(
                                                durationMillis = 260,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        )
                                    }
                                },
                                contentDescription = scrollToBottomCd,
                                hazeState = hazeState,
                            )
                        }

                        ChatTopBar(
                            hazeState = hazeState,
                            onBack = {
                                runNav { navController.navigateUp() }
                            },
                            backContentDescription = stringResource(Res.string.back),
                            showCallButton = panel.showCallButton() && !isReadOnly,
                            onCallClick = {
                                val peer = panelState.profileUserId
                                if (peer != null && peer > 0) {
                                    scope.launch { CallStore.startOutgoingCall(peer) }
                                }
                            },
                            callContentDescription = cdCall,
                            titleChrome = {
                                ChatTopBarInner(
                                    title = panelState.title,
                                    titleAvatar = panelState.titleAvatar,
                                    profileUserId = profileUserId,
                                    onTitleClick = onTitleClick?.let { click ->
                                        { runNav(click) }
                                    },
                                    hideTitleBarAvatar = hideTitleBarAvatar,
                                    onAvatarSlotBounds = onAvatarSlotBounds,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    sharedAvatarKey = sharedAvatarKey,
                                    subtitleKey = subtitleKey,
                                    currentTypingUsers = currentTypingUsers,
                                    typingShowsUsernames = panel.usesPublicGroupSubtitle,
                                    statusConnecting = statusConnecting,
                                    statusUpdating = statusUpdating,
                                    chatGroupLabel = chatGroupLabel,
                                )
                            },
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val density = LocalDensity.current
                    val screenWidthPx = with(density) { maxWidth.toPx().toInt() }
                    val screenHeightPx = with(density) { maxHeight.toPx().toInt() }

                    MessageContextMenu(
                        state = contextMenuState,
                        isAuthor = contextMenuState.message?.user_id == currentUserId,
                        canDelete = contextMenuState.message?.user_id == currentUserId ||
                            currentUserId == 1,
                        isReadOnly = isReadOnly,
                        screenWidthPx = screenWidthPx,
                        screenHeightPx = screenHeightPx,
                        onDismiss = { contextMenuState = contextMenuState.copy(isOpen = false) },
                        onReply = { message ->
                            replyTo = message
                            if (editingMessage != null) {
                                editingMessage = null
                                inputText = ""
                            }
                        },
                        onEdit = { message ->
                            editingMessage = message
                            inputText = if (message.isContentCorrupted) "" else message.content
                            replyTo = null
                        },
                        onDelete = { message ->
                            scope.launch {
                                panel.handleDeleteMessage(message.id)
                            }
                        },
                        onCopy = { message ->
                            if (message.isContentCorrupted) return@MessageContextMenu
                            val text = message.content.trim()
                            if (text.isNotEmpty()) {
                                scope.launch { clipboard.setText(text) }
                            }
                        },
                        onSave = { message ->
                            resolveSavableMessageImage(message)?.let { savable ->
                                saveMessageImage(savable)
                            } ?: resolveSavableMessageFile(message)?.let { savable ->
                                saveMessageFile(savable)
                                scope.launch {
                                    ensureFileDownloadedForSave(message, savable)
                                }
                            }
                        },
                        onCancelSend = { message ->
                            scope.launch { panel.cancelQueuedMessage(message) }
                        },
                        onRetrySend = { message ->
                            val cid = message.client_message_id?.trim().orEmpty()
                            if (cid.isNotEmpty()) {
                                OutgoingMessageCoordinator.retryOutboundMessage(cid, panel.outboxConversationId())
                            }
                        },
                    )
                }
            }
        }

        expandedImage?.let { (msg, idx) ->
            val key = imageAttachmentKey(msg, idx)
            ImageFullscreenPreview(
                message = msg,
                fileIndex = idx,
                currentUserId = currentUserId,
                onDismiss = {
                    isImageClosing = false
                    expandedImage = null
                },
                onClosingChange = { isImageClosing = it },
                onReply = { m ->
                    replyTo = m
                    if (editingMessage != null) {
                        editingMessage = null
                        inputText = ""
                    }
                    isImageClosing = false
                    expandedImage = null
                },
                onDelete = { m ->
                    scope.launch {
                        panel.handleDeleteMessage(m.id)
                    }
                },
                onSave = { msg, fileIndex ->
                    if (!isMessageImageFullyLoaded(msg, fileIndex)) return@ImageFullscreenPreview
                    val file = msg.files?.getOrNull(fileIndex) ?: return@ImageFullscreenPreview
                    resolveImageSourceUri(msg, fileIndex)?.let { source ->
                        saveMessageImage(
                            SavableMessageImage(
                                fileIndex = fileIndex,
                                sourceUri = source,
                                filename = file.name,
                                mimeType = mimeTypeForImageFilename(file.name),
                            ),
                        )
                    }
                },
                sharedTransitionScope = null,
                animatedVisibilityScope = null,
                sharedImageKey = null,
                modifier = Modifier.fillMaxSize(),
                thumbnailBounds = imageThumbBounds[key]
            )
        }
    }

}

@Composable
private fun ChatDateSeparator(
    label: String,
    enterAnimationRole: EnterAnimationRole,
    modifier: Modifier = Modifier,
) {
    val animateEnter = enterAnimationRole == EnterAnimationRole.NewDateSeparator
    AnimatedVisibility(
        visible = true,
        enter = if (animateEnter) {
            fadeIn(tween(150)) + expandVertically(expandFrom = Alignment.Bottom)
        } else {
            fadeIn(tween(0))
        },
        exit = fadeOut(tween(0)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

private val ChatScrollToBottomButtonSize = 44.dp
private val ChatScrollToBottomIconSize = 24.dp

@Composable
private fun ChatScrollToBottomButton(
    onClick: () -> Unit,
    contentDescription: String,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val hazeStyle = rememberChatSurfaceContainerHazeStyle()
    Box(
        modifier = modifier
            .size(ChatScrollToBottomButtonSize)
            .clip(CircleShape)
            .clickable {
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.91f))
                .hazeEffect(state = hazeState, style = hazeStyle),
        )
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = contentDescription,
            modifier = Modifier.size(ChatScrollToBottomIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private suspend fun LazyListState.scrollChatMessageToCenter(
    lazyIndex: Int,
    topClearancePx: Int,
    bottomClearancePx: Int,
    fallbackItemHeightPx: Int,
) {
    val info = layoutInfo
    val viewportHeight = (
        info.viewportEndOffset - info.afterContentPadding - info.viewportStartOffset
    ).coerceAtLeast(0)
    val itemHeight = info.visibleItemsInfo.firstOrNull { it.index == lazyIndex }?.size
        ?: info.visibleItemsInfo
            .filter { it.index > 0 }
            .map { it.size }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.toInt()
        ?: fallbackItemHeightPx
    // reverseLayout: scrollOffset 0 pins the item to the visual bottom (under the input bar).
    // Negative scrollOffset moves it upward into the viewport; positive would push it further off-screen.
    val visibleTop = topClearancePx
    val visibleBottom = (viewportHeight - bottomClearancePx).coerceAtLeast(visibleTop)
    val targetCenterY = (visibleTop + visibleBottom) / 2f
    val scrollOffset = (targetCenterY + itemHeight / 2f - viewportHeight).roundToInt()
    animateScrollToItem(lazyIndex, scrollOffset)
}
