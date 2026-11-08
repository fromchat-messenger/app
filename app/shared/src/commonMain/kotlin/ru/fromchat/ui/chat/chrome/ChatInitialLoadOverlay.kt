package ru.fromchat.ui.chat.chrome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

private const val CHAT_LOAD_COVER_HOLD_AFTER_LOAD_MS = 250L
private const val CHAT_LOAD_COVER_FADE_OUT_MS = 360

/**
 * Keeps the load cover visible until [isInitialLoadActive] is false, then holds for
 * [CHAT_LOAD_COVER_HOLD_AFTER_LOAD_MS] before hiding (fade-out runs in [ChatInitialLoadCover]).
 */
@Composable
fun rememberChatInitialLoadCoverVisible(isInitialLoadActive: Boolean): Boolean {
    var coverVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isInitialLoadActive) {
        if (isInitialLoadActive) {
            coverVisible = true
        } else if (coverVisible) {
            delay(CHAT_LOAD_COVER_HOLD_AFTER_LOAD_MS)
            if (!isInitialLoadActive) {
                coverVisible = false
            }
        }
    }
    return coverVisible
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatInitialLoadCover(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = EnterTransition.None,
        exit = fadeOut(
            animationSpec = tween(CHAT_LOAD_COVER_FADE_OUT_MS, easing = FastOutSlowInEasing),
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            LoadingIndicator()
        }
    }
}
