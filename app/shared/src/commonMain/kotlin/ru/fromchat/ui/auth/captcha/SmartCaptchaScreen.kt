package ru.fromchat.ui.auth.captcha

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import ru.fromchat.ui.components.ChromeBackIconButton
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.logging.Logger
import ru.fromchat.Res
import ru.fromchat.auth_captcha_failed
import ru.fromchat.auth_captcha_title
import ru.fromchat.back
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.components.Text

/**
 * Full-route SmartCaptcha screen (shown after password confirm when Yandex OAuth is off).
 * Returns a token / error via [SmartCaptchaNav] saved-state results.
 */
@Composable
internal fun SmartCaptchaScreen() {
    val navController = LocalNavController.current
    var session by rememberSaveable(stateSaver = SmartCaptchaNav.SessionSaver) {
        mutableStateOf(SmartCaptchaNav.pending)
    }
    var pageReady by remember { mutableStateOf(false) }
    val failedMessage = stringResource(Res.string.auth_captcha_failed)
    val barColor = MaterialTheme.colorScheme.surfaceContainer
    val languageTag = Locale.current.toLanguageTag()
    val screenId = remember { (100000..999999).random().toString(16) }

    DisposableEffect(screenId) {
        Logger.i(
            SmartCaptchaLog.TAG,
            "route enter id=$screenId sessionNull=${session == null} " +
                "pendingNull=${SmartCaptchaNav.pending == null} " +
                "clientKey=${SmartCaptchaLog.redactKey(session?.clientKey)} languageTag=$languageTag",
        )
        onDispose {
            Logger.i(SmartCaptchaLog.TAG, "route dispose id=$screenId pageReady=$pageReady")
        }
    }

    LaunchedEffect(session) {
        if (session == null) {
            Logger.w(SmartCaptchaLog.TAG, "session null → popBackStack id=$screenId")
            navController.popBackStack()
        } else {
            SmartCaptchaNav.pending = session
        }
    }

    val active = session ?: return

    fun finishWithToken(token: String) {
        Logger.i(
            SmartCaptchaLog.TAG,
            "finishWithToken id=$screenId ${SmartCaptchaLog.redactToken(token)}",
        )
        SmartCaptchaNav.pending = null
        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.set(SmartCaptchaNav.RESULT_TOKEN, token)
        navController.popBackStack()
    }

    fun finishWithError(message: String) {
        Logger.w(SmartCaptchaLog.TAG, "finishWithError id=$screenId message=$message")
        SmartCaptchaNav.pending = null
        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.set(SmartCaptchaNav.RESULT_ERROR, message)
        navController.popBackStack()
    }

    fun cancel() {
        Logger.i(SmartCaptchaLog.TAG, "cancel id=$screenId")
        SmartCaptchaNav.pending = null
        navController.popBackStack()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        // Top bar draws into the status bar; bottom nav-bar inset keeps the WebView above it
        // while [containerColor] still paints the gesture/nav area.
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.auth_captcha_title)) },
                navigationIcon = {
                    ChromeBackIconButton(onClick = { cancel() })
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = barColor,
                    scrolledContainerColor = barColor,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        containerColor = barColor,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            SmartCaptchaWebView(
                sitekey = active.clientKey,
                languageTag = languageTag,
                modifier = Modifier.fillMaxSize(),
                onToken = { finishWithToken(it) },
                onReady = {
                    Logger.i(SmartCaptchaLog.TAG, "route pageReady id=$screenId")
                    pageReady = true
                },
                onChallengeVisible = {
                    Logger.i(SmartCaptchaLog.TAG, "route challengeVisible id=$screenId")
                },
                onChallengeHidden = {
                    Logger.i(SmartCaptchaLog.TAG, "route challengeHidden id=$screenId")
                },
                onError = { message ->
                    finishWithError(message.ifBlank { failedMessage })
                },
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = !pageReady,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(barColor),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularWavyProgressIndicator()
                }
            }
        }
    }
}
