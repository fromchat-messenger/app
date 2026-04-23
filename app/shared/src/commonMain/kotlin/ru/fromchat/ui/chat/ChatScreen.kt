package ru.fromchat.ui.chat

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.*
import ru.fromchat.api.ApiClient
import ru.fromchat.api.ProfileCache
import ru.fromchat.api.AttachmentUploadJob
import ru.fromchat.api.AttachmentUploadQueue
import ru.fromchat.api.ConnectionStateStore
import ru.fromchat.api.ConnectionStatus
import ru.fromchat.api.Message
import ru.fromchat.api.UserStatusStore
import ru.fromchat.api.WebSocketManager
import ru.fromchat.api.WebSocketMessage
import ru.fromchat.api.WebSocketUpdatesData
import ru.fromchat.calls.CallStore
import ru.fromchat.core.Logger
import ru.fromchat.net.NetworkConnectivity
import ru.fromchat.ui.HapticFeedbackEvent
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.rememberHapticFeedback
import ru.fromchat.ui.chat.getImageAspectRatio
import ru.fromchat.ui.suspension.SuspendedAccountSupportSheet
import ru.fromchat.utils.formatLastSeen
import ru.fromchat.utils.rememberLastSeenFormatStrings
import kotlin.time.Clock

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
    val scope = rememberCoroutineScope()
    val haptic = rememberHapticFeedback()
    val navController = LocalNavController.current
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
    val lastSeenFormat = rememberLastSeenFormatStrings()
    var showSuspendedSupportSheet by remember { mutableStateOf(false) }
    val statusConnecting = stringResource(Res.string.status_connecting)
    val statusUpdating = stringResource(Res.string.status_updating)
    val chatGroupLabel = stringResource(Res.string.chat_group_label)
    val cdCall = stringResource(Res.string.cd_call)
    LaunchedEffect(currentTypingUsers) {
        Logger.d("ChatScreen", "currentTypingUsers updated (from panelState): ${currentTypingUsers.map { it.username }}")
    }

    val subtitleKey = when {
        !online -> "connecting"
        connectionStatus == ConnectionStatus.UPDATING -> "updating"
        connectionStatus != ConnectionStatus.CONNECTED -> "connecting"
        currentTypingUsers.isNotEmpty() -> "typing"
        panel.usesPublicGroupSubtitle -> {
            if (panelState.publicGroupMetaLoading || panelState.publicGroupMemberCount == null) {
                "group"
            } else {
                "members:${panelState.publicGroupMemberCount}"
            }
        }
        panelState.profileUserId != null -> {
            val userStatus = statusMap[panelState.profileUserId]
            val statusText = userStatus?.let {
                formatLastSeen(it.online, it.lastSeen, lastSeenFormat)
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

    // Subscribe to other user's status when DM is visible; unsubscribe on leave
    LaunchedEffect(panelState.profileUserId) {
        val userId = panelState.profileUserId
        if (userId != null) {
            runCatching { ApiClient.sendSubscribeStatus(userId) }
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                runCatching { ApiClient.sendUnsubscribeStatus(userId) }
            }
        }
    }

    // Scroll to specific message when requested (e.g., from notification click)
    LaunchedEffect(scrollToMessageId, panelState.messages) {
        scrollToMessageId?.let { messageId ->
            val messages = panelState.messages
            val messageIndex = messages.indexOfFirst { it.id == messageId }
            if (messageIndex != -1) {
                scope.launch {
                    // reverseLayout list: index 0 = bottom spacer, 1..n = newest..oldest
                    val lazyIndex = 1 + (messages.size - 1 - messageIndex)
                    listState.animateScrollToItem(index = lazyIndex, scrollOffset = 0)
                }
            }
        }
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
    val expandedImageKey = expandedImage?.let { (msg, idx) -> "img_${msg.id}_$idx" }

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
                                "newMessage", "messageEdited", "messageDeleted",
                                "dmNew", "dmEdited", "dmDeleted",
                                "typing", "stopTyping", "dmTyping", "stopDmTyping",
                                "registeredUserCount" -> {
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
                "dmTyping", "stopDmTyping", "registeredUserCount" -> {
                    scope.launch {
                        panel.handleWebSocketMessage(message)
                    }
                }
                "sendMessage" -> {
                    scope.launch {
                        panel.handleWebSocketMessage(message)
                    }
                }
                else -> {
                    Logger.d("ChatScreen", "Unhandled top-level WebSocket message type: ${message.type}")
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
    LaunchedEffect(panelState.messages.size, panelState.messages.lastOrNull()?.id, panelState.messages.lastOrNull()?.pendingFileAspectRatio) {
        if (panelState.messages.isEmpty()) return@LaunchedEffect

        val lastMessage = panelState.messages.lastOrNull()
        val fingerprint = "${panelState.messages.size}|${lastMessage?.id}|${lastMessage?.pendingFileAspectRatio}"

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
        val minVisibleIndex = listState.layoutInfo.visibleItemsInfo.minOfOrNull { it.index } ?: Int.MAX_VALUE
        val isNearBottom = minVisibleIndex <= 2
        if (lastIsOurs || isNearBottom) {
            if (listState.layoutInfo.visibleItemsInfo.isNotEmpty()) {
                delay(100)
                listState.animateScrollToItem(0)
                delay(150)
                listState.animateScrollToItem(0)
            }
        }
    }

    LaunchedEffect(panel) {
        if (panel.getRecipientId() != null) {
            AttachmentUploadQueue.progressFlow.collect { progress ->
                when (progress) {
                    is ru.fromchat.api.AttachmentUploadProgress.InProgress ->
                        panel.updateMessage(-progress.jobId.hashCode().let { if (it == 0) -1 else it }) {
                            if (it.uploadJobId == progress.jobId) it.copy(uploadProgress = progress.percent) else it
                        }
                    is ru.fromchat.api.AttachmentUploadProgress.Success ->
                        panel.updateMessage(-progress.jobId.hashCode().let { if (it == 0) -1 else it }) {
                            if (it.uploadJobId == progress.jobId) it.copy(uploadProgress = null) else it
                        }
                    else -> {}
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
                        .hazeEffect(state = hazeState, style = HazeMaterials.thin()) {
                            progressive = HazeProgressive.verticalGradient(
                                startIntensity = 0f,
                                endIntensity = 1f,
                            )
                        }
                ) {
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
                                    val replyToId = replyTo?.id
                                    val recipientId = panel.getRecipientId()
                                    if (attachments.isNotEmpty() && recipientId != null) {
                                        val plaintext = text.ifBlank { "" }
                                        attachments.forEach { att ->
                                            val jobId = "dm_${Clock.System.now().toEpochMilliseconds()}_${att.id}"
                                            val hc = jobId.hashCode()
                                            val absHc = if (hc == Int.MIN_VALUE) Int.MAX_VALUE else kotlin.math.abs(hc)
                                            val tempId = -(absHc.let { if (it == 0) 1 else it })
                                            val isImage = att.isImage
                                            val optimisticMessage = Message(
                                                id = tempId,
                                                user_id = currentUserId ?: -1,
                                                content = plaintext.ifBlank { att.filename },
                                                timestamp = Clock.System.now().toString(),
                                                is_read = false,
                                                is_edited = false,
                                                username = "You",
                                                profile_picture = null,
                                                verified = null,
                                                reply_to = replyTo,
                                                client_message_id = jobId,
                                                reactions = null,
                                                files = null,
                                                pendingFileUri = att.uri,
                                                pendingFilename = att.filename,
                                                uploadJobId = jobId,
                                                uploadProgress = 0
                                            )
                                            panel.addMessage(optimisticMessage)
                                            if (isImage) {
                                                scope.launch {
                                                    val aspectRatio = getImageAspectRatio(att.uri)
                                                    if (aspectRatio != null && aspectRatio > 0f) {
                                                        panel.updateMessage(tempId) {
                                                            if (it.uploadJobId == jobId) it.copy(pendingFileAspectRatio = aspectRatio) else it
                                                        }
                                                    }
                                                }
                                            }
                                            AttachmentUploadQueue.enqueue(
                                                AttachmentUploadJob(
                                                    jobId = jobId,
                                                    fileUri = att.uri,
                                                    filename = att.filename,
                                                    recipientId = recipientId,
                                                    plaintext = plaintext.ifBlank { att.filename },
                                                    replyToId = replyToId
                                                )
                                            )
                                        }
                                    } else if (text.isNotBlank()) {
                                        panel.sendMessageWithImmediateDisplay(text, replyToId)
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
                        recipientId = panel.getRecipientId(),
                        isReadOnly = isReadOnly,
                        onReadOnlyMessageClick = {
                            if (isReadOnly) {
                                showSuspendedSupportSheet = true
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            val density = LocalDensity.current
            val statusBarTopDp = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
            val floatingHeaderClearance = statusBarTopDp + 64.dp + 12.dp

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
                if (panelState.isLoading) {
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
                                .background(MaterialTheme.colorScheme.background)
                                .hazeSource(hazeState),
                            userScrollEnabled = !contextMenuState.isOpen,
                            reverseLayout = true,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                        item { Spacer(Modifier.height(innerPadding.calculateBottomPadding())) }

                        items(
                            items = panelState.messages.asReversed(),
                            key = { msg ->
                                msg.client_message_id ?: "id_${msg.id}_${msg.timestamp}"
                            }
                        ) { message ->
                            var tapPositionInRoot by remember { mutableStateOf(IntOffset(0, 0)) }

                            MessageItem(
                                message = message,
                                isAuthor = message.user_id == currentUserId,
                                isContextMenuOpen = contextMenuState.isOpen,
                                isContextMenuForThisMessage = contextMenuState.isOpen && contextMenuState.message?.id == message.id,
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
                                    tapPositionInRoot = IntOffset(offset.x.toInt(), offset.y.toInt())
                                },
                                onUsernameClick =
                                    if (panel.supportsNavigateToSenderProfile &&
                                        message.user_id != currentUserId &&
                                        message.user_id > 0
                                    ) {
                                        {
                                            ProfileCache.mergePreviewFromPublicMessage(message)
                                            navController.navigate(
                                                "profile/${message.user_id}" +
                                                    "?useSharedElement=true&sourceMessageId=${message.id}"
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                onImageClick = { msg, idx -> expandedImage = msg to idx },
                                onImageBounds = { key, rect ->
                                    imageThumbBounds[key] = rect
                                },
                                expandedImageKey = expandedImageKey,
                                isImageClosing = isImageClosing,
                                showUsername = panel.showUsernamesInMessages,
                                currentUserId = currentUserId,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                sharedAvatarNavKey =
                                    if (
                                        panel.supportsNavigateToSenderProfile &&
                                        sharedTransitionScope != null &&
                                        animatedVisibilityScope != null &&
                                        message.user_id != currentUserId &&
                                        message.user_id > 0
                                    ) {
                                        publicChatProfileSharedAvatarKey(
                                            message.user_id,
                                            message.id
                                        )
                                    } else {
                                        null
                                    }
                            )
                        }

                        item { Spacer(Modifier.height(floatingHeaderClearance)) }
                        }

                        ChatFloatingHeaderBox(
                            hazeState = hazeState,
                            onBack = { navController.navigateUp() },
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
                                ChatFloatingTitleChrome(
                                    hazeState = hazeState,
                                    title = panelState.title,
                                    titleAvatar = panelState.titleAvatar,
                                    profileUserId = profileUserId,
                                    onTitleClick = onTitleClick,
                                    hideTitleBarAvatar = hideTitleBarAvatar,
                                    onAvatarSlotBounds = onAvatarSlotBounds,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    sharedAvatarKey = sharedAvatarKey,
                                    subtitleKey = subtitleKey,
                                    currentTypingUsers = currentTypingUsers,
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
                    )
                }
            }
        }

        expandedImage?.let { (msg, idx) ->
            val key = "img_${msg.id}_$idx"
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
                onSave = { _, _ -> /* TODO: platform-specific save to gallery */ },
                sharedTransitionScope = null,
                animatedVisibilityScope = null,
                sharedImageKey = null,
                modifier = Modifier.fillMaxSize(),
                thumbnailBounds = imageThumbBounds[key]
            )
        }
    }

}
