package ru.fromchat.ui.components

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.pr0gramm3r101.utils.LastAnchoredBottomArrangement
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.back
import ru.fromchat.ui.main.settings.SettingsStepHorizontalPadding
import kotlin.math.abs

private val ExpressiveStepSnackbarAnchorGap = 8.dp

enum class ExpressiveStepSnackbarAnchorRole {
    /** Bottom-bar primary CTA (Next / Login / etc.). */
    PrimaryCta,
    /** In-list secondary action (e.g. change server). */
    SecondaryCta,
}

@Stable
class ExpressiveStepSnackbarAnchors {
    var primaryCtaTopInWindow by mutableStateOf<Float?>(null)
        internal set
    var secondaryCtaBoundsInWindow by mutableStateOf<Rect?>(null)
        internal set

    internal fun anchorTopInWindow(listViewport: Rect?): Float? {
        val primaryTop = primaryCtaTopInWindow ?: return null
        val secondary = secondaryCtaBoundsInWindow
        if (secondary != null && secondary.height > 1f && listViewport != null) {
            val inView = secondary.bottom > listViewport.top && secondary.top < listViewport.bottom
            if (inView) return secondary.top
        }
        return primaryTop
    }
}

fun Modifier.trackExpressiveStepSnackbarAnchor(
    anchors: ExpressiveStepSnackbarAnchors,
    role: ExpressiveStepSnackbarAnchorRole,
): Modifier = onGloballyPositioned { coordinates ->
    val bounds = coordinates.boundsInWindow()
    when (role) {
        ExpressiveStepSnackbarAnchorRole.PrimaryCta ->
            anchors.primaryCtaTopInWindow = bounds.top
        ExpressiveStepSnackbarAnchorRole.SecondaryCta ->
            anchors.secondaryCtaBoundsInWindow = bounds
    }
}

/** Outline shape for expressive step-flow text fields. */
val SettingsPasswordOutlineFieldShape = RoundedCornerShape(18.dp)

@Stable
class ExpressiveStepFlowState internal constructor(
    val pagerState: PagerState,
    internal val scope: CoroutineScope,
    internal val pageCount: Int,
) {
    internal var predictiveFromPage by mutableStateOf<Int?>(null)
    internal var predictiveToPage by mutableStateOf<Int?>(null)
    internal var predictiveProgress by mutableFloatStateOf(0f)

    fun resetPredictiveState() {
        predictiveFromPage = null
        predictiveToPage = null
        predictiveProgress = 0f
    }
}

@Immutable
data class ExpressiveHeroSpec(
    val icon: ImageVector,
    val polygon: RoundedPolygon,
    val containerColor: Color,
    val contentColor: Color,
)

/** One step in an expressive flow: scrollable [content] and its bottom-bar [button]. */
@Stable
class ExpressiveStepPage(
    val hero: ExpressiveHeroSpec,
    val content: @Composable (LazyListImeScrollState) -> Unit,
    val button: @Composable () -> Unit,
    val listFooter: (@Composable () -> Unit)? = null,
    val autoFocusPrimaryField: Boolean = false,
)

@Composable
fun rememberExpressiveStepFlow(pageCount: Int): ExpressiveStepFlowState {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    return remember(pagerState, scope, pageCount) {
        ExpressiveStepFlowState(pagerState, scope, pageCount)
    }
}

@Composable
fun expressiveStepFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    disabledBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
)

private val ExpressiveStepHeroTitleSpacing = 16.dp

/** [HorizontalPager] pages only honor content width unless forced to the page slot width. */
private fun Modifier.pagerPageFullWidth(): Modifier = layout { measurable, constraints ->
    val width = constraints.maxWidth
    val placeable = measurable.measure(
        constraints.copy(minWidth = width, maxWidth = width),
    )
    layout(width, placeable.height) {
        placeable.place(0, 0)
    }
}

@Composable
fun ExpressiveStepPageHeader(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SettingsStepHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = scheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.titleLarge.lineHeight,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
        )
        Spacer(Modifier.height(16.dp))
    }
}

private suspend fun applyPredictivePagerSync(
    pagerState: PagerState,
    fromPage: Int,
    toPage: Int,
    progress: Float,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val (page, offset) = when {
        clamped <= 0f -> fromPage to 0f
        clamped >= 1f -> toPage to 0f
        clamped <= 0.5f -> fromPage to (-clamped).coerceIn(-0.5f, 0f)
        else -> toPage to (1f - clamped).coerceIn(0f, 0.5f)
    }
    pagerState.scrollToPage(page = page, pageOffsetFraction = offset)
}

private suspend fun finishPredictiveMorph(
    flowState: ExpressiveStepFlowState,
    pagerState: PagerState,
    startProgress: Float,
    targetProgress: Float,
) {
    val fromPage = flowState.predictiveFromPage ?: return
    val toPage = flowState.predictiveToPage ?: return
    Animatable(startProgress).animateTo(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 220),
    ) {
        flowState.predictiveProgress = value
    }
    val settledPage = if (targetProgress >= 0.5f) toPage else fromPage
    snapshotFlow {
        pagerState.currentPage to pagerState.currentPageOffsetFraction
    }.first { (page, offset) ->
        page == settledPage && abs(offset) < 0.01f
    }
    flowState.resetPredictiveState()
}

@Composable
fun MorphedExpressiveStepButton(
    pages: List<ExpressiveStepPage>,
    fromIndex: Int,
    toIndex: Int,
    morphProgress: Float,
    morphing: Boolean,
    settledPage: Int,
    modifier: Modifier = Modifier,
) {
    val lastIndex = (pages.size - 1).coerceAtLeast(0)
    val settled = settledPage.coerceIn(0, lastIndex)
    val from = fromIndex.coerceIn(0, lastIndex)
    val to = toIndex.coerceIn(0, lastIndex)

    Box(modifier.fillMaxWidth()) {
        if (morphing && from != to) {
            val p = morphProgress.coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .zIndex(if (p < 0.5f) 1f else 0f)
                    .graphicsLayer { alpha = 1f - p },
            ) {
                pages[from].button()
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .zIndex(if (p >= 0.5f) 1f else 0f)
                    .graphicsLayer { alpha = p },
            ) {
                pages[to].button()
            }
        } else {
            pages[settled].button()
        }
    }
}

@OptIn(
    ExperimentalAnimationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalHazeMaterialsApi::class,
)
@Composable
fun ExpressiveStepFlowScaffold(
    flowState: ExpressiveStepFlowState,
    pages: List<ExpressiveStepPage>,
    snackbarHostState: SnackbarHostState,
    onBackAtFirstPage: () -> Unit,
    trailingTopBarActions: @Composable (() -> Unit)? = null,
    hazeScaffold: Boolean = true,
) {
    val pagerState = flowState.pagerState
    val scope = flowState.scope
    val pageCount = pages.size.coerceAtLeast(1)
    val heroSpecs = remember(pages) { pages.map { it.hero } }
    val isHazeLazyMode = hazeScaffold
    val pageOffset by derivedStateOf { pagerState.currentPageOffsetFraction }
    val predictiveThreshold = 0.15f

    PredictiveBackHandler(
        enabled = pagerState.currentPage > 0,
        onProgress = { p ->
            val clamped = p.coerceIn(0f, 1f)
            if (clamped <= 0f) {
                flowState.resetPredictiveState()
            } else {
                if (flowState.predictiveFromPage == null || flowState.predictiveToPage == null) {
                    val fromPage = pagerState.currentPage
                    val toPage = (fromPage - 1).coerceAtLeast(0)
                    flowState.predictiveFromPage = fromPage
                    flowState.predictiveToPage = toPage
                }
                flowState.predictiveProgress = clamped
            }
        },
        onCommit = {
            val fromPageSnapshot = flowState.predictiveFromPage
            val toPageSnapshot = flowState.predictiveToPage
            val lastProgress = flowState.predictiveProgress.coerceIn(0f, 1f)
            if (lastProgress < predictiveThreshold || fromPageSnapshot == null || toPageSnapshot == null) {
                scope.launch {
                    finishPredictiveMorph(
                        flowState = flowState,
                        pagerState = pagerState,
                        startProgress = lastProgress,
                        targetProgress = 0f,
                    )
                }
            } else {
                scope.launch {
                    finishPredictiveMorph(
                        flowState = flowState,
                        pagerState = pagerState,
                        startProgress = lastProgress,
                        targetProgress = 1f,
                    )
                }
            }
        },
        onCancel = {
            val fromPageSnapshot = flowState.predictiveFromPage
            val lastProgress = flowState.predictiveProgress.coerceIn(0f, 1f)
            if (fromPageSnapshot == null) {
                flowState.resetPredictiveState()
                return@PredictiveBackHandler
            }
            scope.launch {
                val commit = lastProgress >= predictiveThreshold
                finishPredictiveMorph(
                    flowState = flowState,
                    pagerState = pagerState,
                    startProgress = lastProgress,
                    targetProgress = if (commit) 1f else 0f,
                )
            }
        },
    )

    LaunchedEffect(
        flowState.predictiveFromPage,
        flowState.predictiveToPage,
        flowState.predictiveProgress,
    ) {
        val fromPage = flowState.predictiveFromPage ?: return@LaunchedEffect
        val toPage = flowState.predictiveToPage ?: return@LaunchedEffect
        applyPredictivePagerSync(
            pagerState = pagerState,
            fromPage = fromPage,
            toPage = toPage,
            progress = flowState.predictiveProgress,
        )
    }

    val lastIndex = (pageCount - 1).coerceAtLeast(0)
    val fromIndex: Int
    val toIndex: Int
    val morphProgress: Float
    if (flowState.predictiveFromPage != null && flowState.predictiveToPage != null && flowState.predictiveProgress > 0f) {
        fromIndex = flowState.predictiveFromPage!!
        toIndex = flowState.predictiveToPage!!
        morphProgress = flowState.predictiveProgress
    } else if (pageOffset < 0f) {
        fromIndex = pagerState.currentPage
        toIndex = (fromIndex - 1).coerceAtLeast(0)
        morphProgress = -pageOffset
    } else if (pageOffset > 0f) {
        fromIndex = pagerState.currentPage
        toIndex = (fromIndex + 1).coerceAtMost(lastIndex)
        morphProgress = pageOffset
    } else {
        fromIndex = pagerState.currentPage
        toIndex = fromIndex
        morphProgress = 0f
    }
    val effectiveMorphProgress = morphProgress.coerceIn(0f, 1f)
    val currentPage = pagerState.currentPage
    val morphing = fromIndex != toIndex
    val fromSpec = heroSpecs.getOrElse(fromIndex) { heroSpecs.first() }
    val toSpec = heroSpecs.getOrElse(toIndex) { heroSpecs.first() }
    val currentSpec = heroSpecs.getOrElse(currentPage) { heroSpecs.first() }

    val isPageTransitionSettled by remember {
        derivedStateOf {
            !pagerState.isScrollInProgress &&
                abs(pagerState.currentPageOffsetFraction) < 0.01f &&
                flowState.predictiveProgress <= 0f &&
                flowState.predictiveFromPage == null
        }
    }
    val autoFocusPrimaryField = pages.getOrNull(currentPage)?.autoFocusPrimaryField == true &&
        isPageTransitionSettled

    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val snackbarAnchors = remember { ExpressiveStepSnackbarAnchors() }
    var snackbarOverlayBounds by remember { mutableStateOf<Rect?>(null) }
    var listViewportBoundsForSnackbar by remember { mutableStateOf<Rect?>(null) }

    @Composable
    fun BoxScope.ExpressiveStepSnackbarHost() {
        val bottomPadding by remember(snackbarOverlayBounds, listViewportBoundsForSnackbar) {
            derivedStateOf {
                val overlay = snackbarOverlayBounds
                val anchorTop = snackbarAnchors.anchorTopInWindow(listViewportBoundsForSnackbar)
                if (overlay == null || anchorTop == null) {
                    ExpressiveStepSnackbarAnchorGap
                } else {
                    with(density) {
                        (overlay.bottom - anchorTop + ExpressiveStepSnackbarAnchorGap.toPx())
                            .toDp()
                            .coerceAtLeast(ExpressiveStepSnackbarAnchorGap)
                    }
                }
            }
        }

        FromChatSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = SettingsStepHorizontalPadding)
                .padding(bottom = bottomPadding)
                .fillMaxWidth(),
            snackbarModifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        )
    }

    @Composable
    fun ExpressiveStepBottomBar(modifier: Modifier = Modifier) {
        MorphedExpressiveStepButton(
            pages = pages,
            fromIndex = fromIndex,
            toIndex = toIndex,
            morphProgress = effectiveMorphProgress,
            morphing = morphing,
            settledPage = currentPage,
            modifier = modifier,
        )
    }

    @Composable
    fun ExpressiveHeroSlot(page: Int) {
        val pageSpec = heroSpecs.getOrElse(page) { heroSpecs.first() }
        val morphing = fromIndex != toIndex
        MorphedExpressiveHero(
            currentSpec = if (morphing) currentSpec else pageSpec,
            fromSpec = if (morphing) fromSpec else pageSpec,
            toSpec = if (morphing) toSpec else pageSpec,
            morphProgress = if (morphing) effectiveMorphProgress else null,
        )
    }

    @Composable
    fun ExpressiveStepHeroSection(modifier: Modifier = Modifier) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            ExpressiveHeroSlot(currentPage)
        }
    }

    val navigateBack: () -> Unit = {
        if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        } else {
            onBackAtFirstPage()
        }
    }

    @Composable
    fun HazeTopBar(hazeState: HazeState) {
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = navigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.back),
                    )
                }
            },
            actions = {
                trailingTopBarActions?.invoke()
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
            modifier = Modifier.hazeEffect(state = hazeState, style = HazeMaterials.thin()) {
                progressive = HazeProgressive.verticalGradient(
                    startIntensity = 1f,
                    endIntensity = 0f,
                )
            },
        )
    }

    @Composable
    fun BackButtonRow() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = navigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.back),
                )
            }
            Spacer(Modifier.weight(1f))
            trailingTopBarActions?.invoke()
        }
    }

    CompositionLocalProvider(
        LocalExpressiveStepFocusEnabled provides isPageTransitionSettled,
        LocalExpressiveStepAutoFocusPrimary provides autoFocusPrimaryField,
    ) {
    if (isHazeLazyMode) {
        val hazeState = rememberHazeState()
        val listState = rememberLazyListState()
        val imeScrollState = rememberLazyListImeScrollState()
        var listViewportBounds by remember { mutableStateOf<Rect?>(null) }

        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { snackbarOverlayBounds = it.boundsInWindow() },
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets.navigationBars,
                containerColor = Color.Transparent,
                contentColor = scheme.onSurface,
                topBar = { HazeTopBar(hazeState = hazeState) },
                bottomBar = {
                    HazeBottomBar(hazeState = hazeState) {
                        Box(
                            Modifier.trackExpressiveStepSnackbarAnchor(
                                anchors = snackbarAnchors,
                                role = ExpressiveStepSnackbarAnchorRole.PrimaryCta,
                            ),
                        ) {
                            ExpressiveStepBottomBar()
                        }
                    }
                },
            ) { innerPadding ->
                LazyListImeScrollEffect(
                    listState = listState,
                    scrollState = imeScrollState,
                    viewportBoundsInWindow = listViewportBounds,
                    contentPaddingTop = innerPadding.calculateTopPadding(),
                    contentPaddingBottom = innerPadding.calculateBottomPadding(),
                    predictiveBackProgress = { flowState.predictiveProgress },
                )

                DisabledBringIntoViewSpec {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(scheme.background)
                            .hazeSource(hazeState)
                            .onGloballyPositioned {
                                listViewportBounds = it.boundsInWindow()
                                listViewportBoundsForSnackbar = it.boundsInWindow()
                            },
                        contentPadding = innerPadding,
                        verticalArrangement = remember { LastAnchoredBottomArrangement(space = 4.dp) },
                    ) {
                        item { Spacer(Modifier.height(8.dp)) }

                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                ExpressiveStepHeroSection()
                                Spacer(Modifier.height(ExpressiveStepHeroTitleSpacing))
                                HorizontalPager(
                                    state = pagerState,
                                    userScrollEnabled = false,
                                    beyondViewportPageCount = 1,
                                    pageSpacing = 0.dp,
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { page ->
                                    Column(modifier = Modifier.pagerPageFullWidth()) {
                                        pages[page].content(imeScrollState)
                                    }
                                }
                            }
                        }

                        val currentListFooter = pages.getOrNull(currentPage)?.listFooter
                        if (currentListFooter != null) {
                            item(key = "expressive_step_footer_$currentPage") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp, bottom = 4.dp)
                                        .trackExpressiveStepSnackbarAnchor(
                                            anchors = snackbarAnchors,
                                            role = ExpressiveStepSnackbarAnchorRole.SecondaryCta,
                                        ),
                                ) {
                                    currentListFooter()
                                }
                            }
                        } else {
                            // Absorbs [LastAnchoredBottomArrangement] slack so hero/content stays at the top.
                            item(key = "expressive_step_bottom_anchor") {
                                Spacer(Modifier.height(1.dp))
                            }
                        }
                    }
                }
            }

            ExpressiveStepSnackbarHost()
        }
    } else {
        val imeScrollState = rememberLazyListImeScrollState()
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = scheme.surface,
            contentColor = scheme.onSurface,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .onGloballyPositioned { snackbarOverlayBounds = it.boundsInWindow() },
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    BackButtonRow()

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ExpressiveStepHeroSection()
                            Spacer(Modifier.height(ExpressiveStepHeroTitleSpacing))

                            HorizontalPager(
                                state = pagerState,
                                userScrollEnabled = false,
                                beyondViewportPageCount = 1,
                                pageSpacing = 0.dp,
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.fillMaxWidth(),
                            ) { page ->
                                Column(
                                    modifier = Modifier.pagerPageFullWidth(),
                                ) {
                                    pages[page].content(imeScrollState)
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = SettingsStepHorizontalPadding)
                            .padding(top = 12.dp, bottom = 16.dp)
                            .trackExpressiveStepSnackbarAnchor(
                                anchors = snackbarAnchors,
                                role = ExpressiveStepSnackbarAnchorRole.PrimaryCta,
                            ),
                    ) {
                        ExpressiveStepBottomBar()
                    }
                }

                ExpressiveStepSnackbarHost()
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class)
@Composable
fun MorphedExpressiveHero(
    currentSpec: ExpressiveHeroSpec,
    fromSpec: ExpressiveHeroSpec,
    toSpec: ExpressiveHeroSpec,
    modifier: Modifier = Modifier,
    containerSize: Dp = 132.dp,
    iconSize: Dp = 48.dp,
    morphProgress: Float? = null,
) {
    val usePredictive = morphProgress != null && fromSpec != toSpec
    val p = (morphProgress ?: 0f).coerceIn(0f, 1f)

    val morph = remember(fromSpec.polygon, toSpec.polygon) {
        Morph(fromSpec.polygon, toSpec.polygon)
    }

    val deep = lerp(fromSpec.containerColor, toSpec.containerColor, p)
    val contentColor = lerp(fromSpec.contentColor, toSpec.contentColor, p)
    val light = deep.copy(alpha = 0.72f)

    Box(
        modifier = modifier.size(containerSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = minOf(size.width, size.height) * 0.94f
            translate(left = size.width / 2f, top = size.height / 2f) {
                scale(scaleX = unit, scaleY = unit, pivot = Offset.Zero) {
                    translate(left = -0.5f, top = -0.5f) {
                        drawPath(
                            path = morph.toPath(p, Path()),
                            brush = Brush.linearGradient(
                                colors = listOf(light, deep),
                                start = Offset.Zero,
                                end = Offset(1f, 1f),
                            ),
                        )
                    }
                }
            }
        }

        if (usePredictive) {
            Box(
                modifier = Modifier.size(iconSize),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = fromSpec.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = 1f - p },
                    tint = contentColor,
                )
                Icon(
                    imageVector = toSpec.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = p },
                    tint = contentColor,
                )
            }
        } else {
            Icon(
                imageVector = currentSpec.icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = currentSpec.contentColor,
            )
        }
    }
}
