package ru.fromchat.ui.main.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pr0gramm3r101.components.Category
import com.pr0gramm3r101.components.ListItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.action_delete
import ru.fromchat.back
import ru.fromchat.cancel
import ru.fromchat.confirm
import ru.fromchat.cd_close_selection
import ru.fromchat.logs_clear_all_cd
import ru.fromchat.logs_clear_all_confirm_body
import ru.fromchat.logs_clear_all_confirm_title
import ru.fromchat.logs_delete_files_confirm_body
import ru.fromchat.logs_delete_files_confirm_title
import ru.fromchat.logs_file_size_kb
import ru.fromchat.logs_file_size_mb
import ru.fromchat.logs_files_title
import ru.fromchat.logs_open
import ru.fromchat.logs_rotate
import ru.fromchat.logs_rotate_confirm_body
import ru.fromchat.logs_rotate_confirm_title
import ru.fromchat.logs_selected_count
import ru.fromchat.logs_share
import ru.fromchat.logs_title
import ru.fromchat.logging.AppLogStore
import ru.fromchat.logging.FromChatLogDirs
import ru.fromchat.logging.LogCleanMode
import ru.fromchat.logging.LogCleanRequest
import ru.fromchat.logging.LogFileInfo
import ru.fromchat.logging.LogShare
import ru.fromchat.logging.LogShareCompression
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.components.BackHandler
import ru.fromchat.ui.components.PredictiveBackHandler
import ru.fromchat.ui.components.ScreenSurface
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.main.chats.ChatSelectionTransitionSpring
import ru.fromchat.ui.main.chats.SelectionCheckmarkSlot
import ru.fromchat.utils.haptic.HapticFeedbackEvent
import ru.fromchat.utils.haptic.rememberHapticFeedback

private enum class LogFilesListMode {
    Normal,
    Selecting,
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun LogFilesScreen(
    onOpenFile: (LogFileInfo) -> Unit,
) {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHapticFeedback()
    val density = LocalDensity.current

    var logFiles by remember { mutableStateOf<List<LogFileInfo>>(emptyList()) }
    val listState: LazyListState = rememberLazyListState()
    var listMode by remember { mutableStateOf(LogFilesListMode.Normal) }
    var selectedFilePaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    val selectionTransitionProgress = remember { Animatable(0f) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRotateConfirm by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var pendingSharePaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingShareIsCurrent by remember { mutableStateOf(false) }
    var deletingFilePaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    val gestureState = rememberLogsListGestureState()
    var dragAnchorIndex by remember { mutableIntStateOf(-1) }
    var dragLastY by remember { mutableFloatStateOf(0f) }
    var listRootY by remember { mutableFloatStateOf(0f) }

    val shareTitle = stringResource(Res.string.logs_title)

    val selectionMode = listMode == LogFilesListMode.Selecting
    val selectionProgress = selectionTransitionProgress.value
    val showClearFab = !selectionMode && selectionProgress <= 0f
    val canOpenSingleFile = selectedFilePaths.size == 1
    val canRotateCurrentLog = selectedFilePaths.size == 1 &&
        logFiles.any {
            it.path in selectedFilePaths && it.name == FromChatLogDirs.CURRENT_LOG_FILE
        }

    val selectedCountTitle = stringResource(Res.string.logs_selected_count, selectedFilePaths.size)
    val closeSelectionCd = stringResource(Res.string.cd_close_selection)
    val openLabel = stringResource(Res.string.logs_open)
    val shareLabel = stringResource(Res.string.logs_share)
    val deleteLabel = stringResource(Res.string.action_delete)
    val rotateLabel = stringResource(Res.string.logs_rotate)
    val clearAllCd = stringResource(Res.string.logs_clear_all_cd)

    fun refreshLogFiles() {
        logFiles = AppLogStore.listLogFiles()
    }

    fun enterSelection(path: String) {
        haptic(HapticFeedbackEvent.SelectionModeEntered)
        scope.launch { selectionTransitionProgress.snapTo(0f) }
        listMode = LogFilesListMode.Selecting
        selectedFilePaths = setOf(path)
    }

    fun exitSelection() {
        gestureState.reset()
        scope.launch { selectionTransitionProgress.snapTo(0f) }
        listMode = LogFilesListMode.Normal
        selectedFilePaths = emptySet()
        dragAnchorIndex = -1
    }

    fun requestExitSelection() {
        scope.launch {
            selectionTransitionProgress.animateTo(0f, ChatSelectionTransitionSpring)
            exitSelection()
        }
    }

    fun clearAllLogs() {
        val pathsToClear = logFiles.map { it.path }.toSet()
        if (pathsToClear.isEmpty()) {
            showClearAllConfirm = false
            return
        }
        deletingFilePaths = deletingFilePaths + pathsToClear
        scope.launch {
            AppLogStore.clean(LogCleanRequest(mode = LogCleanMode.All))
            showClearAllConfirm = false
            requestExitSelection()
            // Allow shrink animation to complete before refreshing the list.
            delay(220)
            deletingFilePaths = emptySet()
            refreshLogFiles()
        }
    }

    fun performShare(compression: LogShareCompression) {
        if (pendingSharePaths.isEmpty()) return
        scope.launch {
            val path = if (pendingSharePaths.size > 1) {
                AppLogStore.prepareMultiFileShareZip(pendingSharePaths)
            } else {
                AppLogStore.prepareSharePath(
                    sourcePath = pendingSharePaths.single(),
                    isCurrentLog = pendingShareIsCurrent,
                    compression = compression,
                )
            }
            val mimeType = when {
                pendingSharePaths.size > 1 -> "application/zip"
                compression == LogShareCompression.Compressed -> "application/gzip"
                path.endsWith(".gz") -> "application/gzip"
                else -> "text/plain"
            }
            LogShare.shareFile(shareTitle, path, mimeType)
            pendingSharePaths = emptyList()
            showShareSheet = false
            requestExitSelection()
        }
    }

    fun deleteSelectedFiles() {
        val pathsToDelete = selectedFilePaths
        if (pathsToDelete.isEmpty()) {
            showDeleteConfirm = false
            return
        }
        deletingFilePaths = deletingFilePaths + pathsToDelete
        scope.launch {
            pathsToDelete.forEach { path ->
                AppLogStore.deleteLogFile(path)
            }
            showDeleteConfirm = false
            requestExitSelection()
            // Allow shrink animation to complete before refreshing the list.
            delay(220)
            deletingFilePaths = emptySet()
            refreshLogFiles()
        }
    }

    fun applyDragSelectionRange(toIndex: Int) {
        val anchor = dragAnchorIndex
        if (anchor < 0 || toIndex < 0) return
        val start = minOf(anchor, toIndex)
        val end = maxOf(anchor, toIndex)
        selectedFilePaths = logFiles.subList(start, end + 1).map { it.path }.toSet()
    }

    fun beginDragSelection(index: Int) {
        if (index !in logFiles.indices) return
        gestureState.onDragSelectionStart()
        dragAnchorIndex = index
        val path = logFiles[index].path
        if (!selectionMode) {
            enterSelection(path)
        } else {
            applyDragSelectionRange(index)
        }
    }

    LaunchedEffect(Unit) {
        refreshLogFiles()
    }

    LaunchedEffect(listMode) {
        if (listMode == LogFilesListMode.Selecting) {
            selectionTransitionProgress.animateTo(1f, ChatSelectionTransitionSpring)
        }
    }

    LaunchedEffect(selectedFilePaths, listMode) {
        if (listMode == LogFilesListMode.Selecting && selectedFilePaths.isEmpty()) {
            requestExitSelection()
        }
    }

    LaunchedEffect(gestureState.dragSelectActive, listState) {
        if (!gestureState.dragSelectActive) return@LaunchedEffect
        val edgeThresholdPx = with(density) { 72.dp.toPx() }
        while (isActive && gestureState.dragSelectActive) {
            val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()
            when {
                dragLastY < edgeThresholdPx -> {
                    listState.scrollBy(-18f)
                    listState.logFileIndexAtY(dragLastY, logFiles.size)
                        ?.let { applyDragSelectionRange(it) }
                }
                dragLastY > viewportHeight - edgeThresholdPx -> {
                    listState.scrollBy(18f)
                    listState.logFileIndexAtY(dragLastY, logFiles.size)
                        ?.let { applyDragSelectionRange(it) }
                }
            }
            delay(16)
        }
    }

    DisposableEffect(Unit) {
        onDispose { exitSelection() }
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text(stringResource(Res.string.logs_clear_all_confirm_title)) },
            text = { Text(stringResource(Res.string.logs_clear_all_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { clearAllLogs() }) {
                    Text(stringResource(Res.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(Res.string.logs_delete_files_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        Res.string.logs_delete_files_confirm_body,
                        selectedFilePaths.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { deleteSelectedFiles() }) {
                    Text(deleteLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    if (showRotateConfirm) {
        AlertDialog(
            onDismissRequest = { showRotateConfirm = false },
            title = { Text(stringResource(Res.string.logs_rotate_confirm_title)) },
            text = { Text(stringResource(Res.string.logs_rotate_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showRotateConfirm = false
                    scope.launch {
                        AppLogStore.rotate()
                        refreshLogFiles()
                    }
                }) {
                    Text(stringResource(Res.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRotateConfirm = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    if (showShareSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showShareSheet = false
                pendingSharePaths = emptyList()
            },
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
        ) {
            LogsShareBottomSheet(
                onUncompressed = { performShare(LogShareCompression.Uncompressed) },
                onCompressed = { performShare(LogShareCompression.Compressed) },
            )
        }
    }

    BackHandler(enabled = selectionMode) { requestExitSelection() }
    PredictiveBackHandler(
        enabled = selectionMode,
        onProgress = { backProgress ->
            scope.launch {
                selectionTransitionProgress.snapTo((1f - backProgress).coerceIn(0f, 1f))
            }
        },
        onCommit = { requestExitSelection() },
        onCancel = {
            if (selectionMode) {
                scope.launch {
                    selectionTransitionProgress.animateTo(1f, ChatSelectionTransitionSpring)
                }
            }
        },
    )

    val selectionBarVisible = selectionMode || selectionProgress > 0f
    val listBottomInset = if (selectionBarVisible) 88.dp else 8.dp
    val fileCategoryColor = MaterialTheme.colorScheme.surfaceContainer

    ScreenSurface {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.navigationBars,
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            val fabReveal = (1f - selectionProgress).coerceIn(0f, 1f)
            LogsAnimatedFab(
                visible = showClearFab && fabReveal > 0f,
                alpha = fabReveal,
                onClick = { showClearAllConfirm = true },
                contentDescription = clearAllCd,
                icon = Icons.Default.DeleteSweep,
            )
        },
        topBar = {
            Box {
                TopAppBar(
                    windowInsets = settingsDetailWindowInsets(),
                    modifier = Modifier
                        .graphicsLayer { alpha = 1f - selectionProgress }
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    navigationIcon = {
                        IconButton(
                            onClick = { navController.navigateUp() },
                            enabled = selectionProgress < 1f,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.back),
                            )
                        }
                    },
                    title = {
                        Text(
                            text = stringResource(Res.string.logs_files_title),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
                if (selectionMode || selectionProgress > 0f) {
                    TopAppBar(
                        windowInsets = settingsDetailWindowInsets(),
                        modifier = Modifier.graphicsLayer { alpha = selectionProgress },
                        navigationIcon = {
                            IconButton(
                                onClick = { requestExitSelection() },
                                enabled = selectionProgress > 0f,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = closeSelectionCd,
                                )
                            }
                        },
                        title = {
                            Text(
                                text = selectedCountTitle,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            DisableSelection {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onGloballyPositioned { listRootY = it.positionInRoot().y },
                    contentPadding = PaddingValues(bottom = listBottomInset),
                ) {
                    Category(
                        margin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        containerColor = fileCategoryColor,
                    ) {
                        logFiles.forEachIndexed { index, file ->
                            val isSelected = file.path in selectedFilePaths
                            item {
                                AnimatedVisibility(
                                    visible = file.path !in deletingFilePaths,
                                    enter = fadeIn(ChatSelectionTransitionSpring),
                                    exit = fadeOut(ChatSelectionTransitionSpring) + shrinkVertically(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                        shrinkTowards = Alignment.Top,
                                    ),
                                ) {
                                    LogFileRow(
                                        file = file,
                                        fileSelectionMode = selectionMode,
                                        fileSelectionProgress = selectionProgress,
                                        isSelected = isSelected,
                                        divider = index < logFiles.lastIndex,
                                        onTap = {
                                            if (gestureState.shouldSuppressTap()) return@LogFileRow
                                            if (selectionMode) {
                                                selectedFilePaths = if (file.path in selectedFilePaths) {
                                                    selectedFilePaths - file.path
                                                } else {
                                                    selectedFilePaths + file.path
                                                }
                                            } else {
                                                onOpenFile(file)
                                            }
                                        },
                                        gestureState = gestureState,
                                        getListRootY = { listRootY },
                                        onBeginDragSelection = { beginDragSelection(index) },
                                        onDragAtListLocalY = { listLocalY ->
                                            dragLastY = listLocalY
                                            listState.logFileIndexAtY(listLocalY, logFiles.size)
                                                ?.let { applyDragSelectionRange(it) }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = selectionBarVisible,
                enter = slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    initialOffsetY = { fullHeight -> fullHeight },
                ),
                exit = slideOutVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    targetOffsetY = { fullHeight -> fullHeight },
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    HorizontalFloatingToolbar(
                        expanded = true,
                    ) {
                        IconButton(
                            onClick = {
                                val selected = logFiles.filter { it.path in selectedFilePaths }
                                if (selected.size == 1) onOpenFile(selected.first())
                            },
                            enabled = canOpenSingleFile,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, openLabel)
                        }
                        IconButton(
                            onClick = { showRotateConfirm = true },
                            enabled = canRotateCurrentLog,
                        ) {
                            Icon(Icons.Default.Sync, rotateLabel)
                        }
                        IconButton(
                            onClick = {
                                val selected = logFiles.filter { it.path in selectedFilePaths }
                                if (selected.isEmpty()) return@IconButton
                                pendingSharePaths = selected.map { it.path }
                                pendingShareIsCurrent = selected.all {
                                    it.name == FromChatLogDirs.CURRENT_LOG_FILE
                                }
                                showShareSheet = true
                            },
                            enabled = selectedFilePaths.isNotEmpty(),
                        ) {
                            Icon(Icons.Default.Share, shareLabel)
                        }
                        IconButton(
                            onClick = {
                                if (selectedFilePaths.isEmpty()) return@IconButton
                                showDeleteConfirm = true
                            },
                            enabled = selectedFilePaths.isNotEmpty(),
                        ) {
                            Icon(Icons.Rounded.Delete, deleteLabel)
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun LogFileRow(
    file: LogFileInfo,
    fileSelectionMode: Boolean,
    fileSelectionProgress: Float,
    isSelected: Boolean,
    divider: Boolean,
    onTap: () -> Unit,
    gestureState: LogsListGestureState,
    getListRootY: () -> Float,
    onBeginDragSelection: () -> Unit,
    onDragAtListLocalY: (Float) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val rowRootYHolder = remember { LogsRowRootYHolder() }

    val tintProgress = if (isSelected) fileSelectionProgress.coerceIn(0f, 1f) else 0f
    val colors = logsSelectionColors(
        isSelected = isSelected,
        selectionProgress = tintProgress,
        baseContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    )

    ListItem(
        modifier = Modifier
            .onGloballyPositioned { rowRootYHolder.y = it.positionInRoot().y }
            .logsRowDragSelectGestures(
                gestureState = gestureState,
                scope = scope,
                rowRootYHolder = rowRootYHolder,
                getListRootY = getListRootY,
                onDragStart = onBeginDragSelection,
                onDragAtListLocalY = onDragAtListLocalY,
            ),
        headline = file.name,
        supportingText = formatLogFileSize(file.sizeBytes),
        containerColor = colors.containerColor,
        onClick = {
            if (!gestureState.shouldSuppressTap()) {
                onTap()
            }
        },
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SelectionCheckmarkSlot(
                    selectionTransitionProgress = fileSelectionProgress,
                    isSelected = isSelected,
                )
                Icon(
                    imageVector = when {
                        file.name == FromChatLogDirs.CURRENT_LOG_FILE -> Icons.Default.History
                        file.isGzip -> Icons.Default.Archive
                        else -> Icons.AutoMirrored.Rounded.InsertDriveFile
                    },
                    contentDescription = null,
                    tint = colors.iconColor,
                )
            }
        },
        divider = divider,
    )
}

@Composable
private fun formatLogFileSize(sizeBytes: Long): String {
    val kb = (sizeBytes / 1024).toInt()
    if (sizeBytes < 1024 * 1024) {
        return stringResource(Res.string.logs_file_size_kb, kb)
    }
    val mb = sizeBytes / (1024.0 * 1024.0)
    val megabytes = ((kotlin.math.round(mb * 10.0) / 10.0)).toString()
    return stringResource(Res.string.logs_file_size_mb, megabytes)
}
