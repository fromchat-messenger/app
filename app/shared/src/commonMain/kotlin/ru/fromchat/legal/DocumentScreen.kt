package ru.fromchat.legal

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pr0gramm3r101.utils.ToggleNavScrimEffect
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.about_link_privacy
import ru.fromchat.about_link_terms
import ru.fromchat.attachment_retry
import ru.fromchat.back
import ru.fromchat.legal_document_cached_banner
import ru.fromchat.legal_document_load_error
import ru.fromchat.ui.chat.rememberChatSurfaceContainerHazeStyle
import ru.fromchat.ui.components.ActionButton
import ru.fromchat.ui.components.Text

private data class PendingDocument(
    val parsed: ParsedDocument,
    val isCached: Boolean,
)

private sealed interface DocumentDisplayState {
    data object Loading : DocumentDisplayState

    data object Error : DocumentDisplayState

    data class Success(
        val parsed: ParsedDocument,
    ) : DocumentDisplayState

    data class CachedSuccess(
        val parsed: ParsedDocument,
    ) : DocumentDisplayState
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun DocumentScreen(
    type: DocumentType,
    onBack: () -> Unit,
    onOpenLegalDocument: (DocumentType) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val hazeState = rememberHazeState(blurEnabled = true)

    LaunchedEffect(type) {
        listState.scrollToItem(0)
    }

    ToggleNavScrimEffect()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        text = when (type) {
                            DocumentType.Privacy -> stringResource(Res.string.about_link_privacy)
                            DocumentType.Terms -> stringResource(Res.string.about_link_terms)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = Color.Transparent
                ),
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.91f))
                    .hazeEffect(
                        state = hazeState,
                        style = rememberChatSurfaceContainerHazeStyle(),
                    ),
            )
        },
    ) { innerPadding ->
        var loadAttempt by remember(type) { mutableIntStateOf(0) }
        var displayState by remember(type) { mutableStateOf<DocumentDisplayState>(DocumentDisplayState.Loading) }
        var pendingDocument by remember(type) { mutableStateOf<PendingDocument?>(null) }

        val topContentPadding = innerPadding.calculateTopPadding()
        val bottomContentPadding = innerPadding.calculateBottomPadding() + 16.dp

        LaunchedEffect(type, loadAttempt) {
            displayState = DocumentDisplayState.Loading
            pendingDocument = null

            when (val result = DocumentRepository.fetch(type)) {
                is DocumentLoadResult.Success -> {
                    pendingDocument = PendingDocument(
                        parsed = parseMarkdown(result.markdown),
                        isCached = result.isCached,
                    )
                }

                DocumentLoadResult.Failure -> {
                    displayState = DocumentDisplayState.Error
                }
            }
        }

        LaunchedEffect(pendingDocument) {
            val pending = pendingDocument ?: return@LaunchedEffect

            withFrameNanos { }
            withFrameNanos { }

            displayState = if (pending.isCached) {
                DocumentDisplayState.CachedSuccess(pending.parsed)
            } else {
                DocumentDisplayState.Success(pending.parsed)
            }
            pendingDocument = null
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .hazeSource(hazeState),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                pendingDocument?.let { pending ->
                    DocumentContent(
                        parsed = pending.parsed,
                        showCachedBanner = pending.isCached,
                        listState = listState,
                        onOpenLegalDocument = onOpenLegalDocument,
                        topContentPadding = topContentPadding,
                        bottomContentPadding = bottomContentPadding,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(0f),
                    )
                }

                AnimatedContent(
                    targetState = displayState,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "legal_document_${type.name}",
                ) { state ->
                when (state) {
                    DocumentDisplayState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = topContentPadding),
                            contentAlignment = Alignment.Center,
                        ) {
                            LoadingIndicator(modifier = Modifier.padding(24.dp))
                        }
                    }

                    DocumentDisplayState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = topContentPadding)
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                text = stringResource(Res.string.legal_document_load_error),
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )

                            ActionButton(onClick = { loadAttempt++ }) {
                                Text(stringResource(Res.string.attachment_retry))
                            }
                        }
                    }

                    is DocumentDisplayState.Success -> {
                        DocumentContent(
                            parsed = state.parsed,
                            showCachedBanner = false,
                            listState = listState,
                            onOpenLegalDocument = onOpenLegalDocument,
                            topContentPadding = topContentPadding,
                            bottomContentPadding = bottomContentPadding,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    is DocumentDisplayState.CachedSuccess -> {
                        DocumentContent(
                            parsed = state.parsed,
                            showCachedBanner = true,
                            listState = listState,
                            onOpenLegalDocument = onOpenLegalDocument,
                            topContentPadding = topContentPadding,
                            bottomContentPadding = bottomContentPadding,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun DocumentContent(
    parsed: ParsedDocument,
    showCachedBanner: Boolean,
    listState: LazyListState,
    onOpenLegalDocument: (DocumentType) -> Unit,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val defaultUriHandler = LocalUriHandler.current

    CompositionLocalProvider(
        LocalUriHandler provides remember(onOpenLegalDocument, defaultUriHandler) {
            object : UriHandler {
                override fun openUri(uri: String) {
                    resolveLegalDocumentType(uri)?.let { linkedType ->
                        onOpenLegalDocument(linkedType)
                        return
                    }

                    defaultUriHandler.openUri(uri)
                }
            }
        },
    ) {
        SelectionContainer(modifier = modifier) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    top = topContentPadding,
                    bottom = bottomContentPadding,
                ),
            ) {
            if (showCachedBanner) {
                item(key = "cached-banner") {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.legal_document_cached_banner),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }

            if (parsed.preamble.isNotBlank()) {
                item(key = "preamble") {
                    Markdown(
                        content = parsed.preamble,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
            }

            parsed.sections.forEachIndexed { index, section ->
                item(key = "section-$index") {
                    Column {
                        ExpressiveSectionHeader(
                            directive = section.directive,
                            title = section.title,
                            modifier = Modifier.padding(top = 8.dp),
                        )

                        Markdown(
                            content = section.bodyMarkdown,
                            modifier = Modifier.padding(bottom = 24.dp),
                        )
                    }
                }
            }
        }
        }
    }
}
