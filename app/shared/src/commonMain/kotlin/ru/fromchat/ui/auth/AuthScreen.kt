package ru.fromchat.ui.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pr0gramm3r101.utils.crypto.deriveAuthSecret
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Logger
import ru.fromchat.Res
import ru.fromchat.api.ApiClient
import ru.fromchat.api.crypto.IdentityKeyManager
import ru.fromchat.api.instance.ServerProbeResult
import ru.fromchat.api.instance.probeServer
import ru.fromchat.api.local.cache.CacheContext
import ru.fromchat.api.local.db.clearAccountCacheOnLogout
import ru.fromchat.api.schema.core.ErrorResponse
import ru.fromchat.api.schema.user.auth.LoginResponse
import ru.fromchat.api.schema.user.auth.RegisterConfirmRequest
import ru.fromchat.api.schema.user.auth.SmartCaptchaParams
import ru.fromchat.api.schema.user.auth.YandexOAuthParams
import ru.fromchat.change_server
import ru.fromchat.config.Settings
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.auth.captcha.SmartCaptchaLog
import ru.fromchat.ui.auth.captcha.SmartCaptchaNav
import ru.fromchat.ui.auth.register.confirmPasswordStepPage
import ru.fromchat.ui.auth.register.profileStepPage
import ru.fromchat.ui.auth.yandex.yandexIdStepPage
import ru.fromchat.ui.components.ExpressiveStepFlowScaffold
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.components.TextCta
import ru.fromchat.ui.components.rememberExpressiveStepFlow
import ru.fromchat.ui.components.showLoggedSnackbar
import ru.fromchat.ui.extraStatusBars
import ru.fromchat.ui.main.settings.SettingsStepHorizontalPadding
import kotlin.time.Duration.Companion.milliseconds

private enum class AuthFlowStep {
    Username,
    Password,
    ConfirmPassword,
    YandexId,
    Profile,
}

internal sealed interface PasswordStepResult {
    data object LoginSuccess : PasswordStepResult
    data class NeedsRegister(
        val yandexRequired: Boolean,
        val yandex: YandexOAuthParams?,
        val captchaRequired: Boolean,
        val captcha: SmartCaptchaParams?,
    ) : PasswordStepResult
    data class WrongPassword(val message: String) : PasswordStepResult
    data class RateLimited(val message: String) : PasswordStepResult
    data class Error(val message: String, val cause: Throwable? = null) : PasswordStepResult
}

internal sealed interface RegisterResult {
    data object Success : RegisterResult
    data object UsernameTaken : RegisterResult
    data class Error(val message: String, val cause: Throwable? = null) : RegisterResult
}

private const val AUTH_SERVER_PROBE_TIMEOUT_MS = 5_000L

internal suspend fun probeCurrentServer() = runCatching {
    withTimeout(AUTH_SERVER_PROBE_TIMEOUT_MS.milliseconds) {
        probeServer(Settings.readServerConfig()) is ServerProbeResult.Supported
    }
}.getOrDefault(false)

private suspend fun fullLogin(
    username: String,
    password: String,
    request: suspend () -> LoginResponse,
) {
    val previousInstanceId = runCatching { CacheContext.activeInstanceId.value.trim() }.getOrDefault("")
    runCatching { clearAccountCacheOnLogout(previousInstanceId) }
    ApiClient.clearMemorySession()

    val response = request()

    ApiClient.bindSession(response)

    try {
        IdentityKeyManager.ensureKeysOnLogin(
            username = username,
            password = password,
            token = response.token,
        )
    } catch (e: Exception) {
        ApiClient.clearMemorySession()
        throw e
    }

    ApiClient.persistSessionToStorage(response)
    runCatching { ApiClient.refreshServerInstanceFingerprint() }
}

internal suspend fun authPasswordStep(
    username: String,
    password: String,
    wrongPasswordMessage: String,
    rateLimitMessage: String,
    unexpectedError: String,
) = try {
    val derived = deriveAuthSecret(username.trim(), password.trim())
    when (val outcome = ApiClient.authPasswordStep(username.trim(), derived)) {
        is ApiClient.AuthPasswordStepOutcome.LoggedIn -> {
            fullLogin(username.trim(), password.trim()) { outcome.response }
            PasswordStepResult.LoginSuccess
        }

        is ApiClient.AuthPasswordStepOutcome.NeedsRegister -> {
            Logger.i(
                SmartCaptchaLog.TAG,
                "password step needs_register yandexRequired=${outcome.yandexRequired} " +
                    "captchaRequired=${outcome.captchaRequired} " +
                    "clientKey=${SmartCaptchaLog.redactKey(outcome.captcha?.client_key)}",
            )
            PasswordStepResult.NeedsRegister(
                yandexRequired = outcome.yandexRequired,
                yandex = outcome.yandex,
                captchaRequired = outcome.captchaRequired,
                captcha = outcome.captcha,
            )
        }
    }
} catch (e: ClientRequestException) {
    when (e.response.status.value) {
        401 -> PasswordStepResult.WrongPassword(
            parseClientError(e, wrongPasswordMessage).ifBlank { wrongPasswordMessage },
        )

        429 -> PasswordStepResult.RateLimited(
            parseClientError(e, rateLimitMessage).ifBlank { rateLimitMessage },
        )

        else -> PasswordStepResult.Error(parseClientError(e, unexpectedError))
    }
} catch (e: Exception) {
    PasswordStepResult.Error(unexpectedError, e)
}

internal suspend fun register(
    username: String,
    displayName: String,
    password: String,
    bio: String,
    registrationProof: String?,
    captchaToken: String?,
    unexpectedError: String,
) = try {
    Logger.i(
        SmartCaptchaLog.TAG,
        "register confirm start username=${username.trim()} " +
            "hasRegistrationProof=${!registrationProof.isNullOrBlank()} " +
            "captchaToken=${SmartCaptchaLog.redactToken(captchaToken)}",
    )
    fullLogin(username.trim(), password.trim()) {
        val derived = deriveAuthSecret(username.trim(), password.trim())
        ApiClient.authRegisterConfirm(
            RegisterConfirmRequest(
                username = username.trim(),
                display_name = displayName.trim(),
                password = derived,
                confirm_password = derived,
                bio = bio.trim().takeIf { it.isNotEmpty() },
                registration_proof = registrationProof,
                captcha_token = captchaToken,
            ),
        )
    }
    Logger.i(SmartCaptchaLog.TAG, "register confirm success username=${username.trim()}")
    RegisterResult.Success
} catch (e: ClientRequestException) {
    Logger.w(
        SmartCaptchaLog.TAG,
        "register confirm HTTP ${e.response.status.value} username=${username.trim()}",
        e,
    )
    if (e.response.status.value == 400 && isUsernameTakenError(e)) {
        RegisterResult.UsernameTaken
    } else {
        RegisterResult.Error(parseClientError(e, unexpectedError))
    }
} catch (e: Exception) {
    Logger.e(SmartCaptchaLog.TAG, "register confirm failed username=${username.trim()}", e)
    RegisterResult.Error(unexpectedError, e)
}

private suspend fun isUsernameTakenError(e: ClientRequestException) =
    runCatching { e.response.body<ErrorResponse>().detail }.getOrNull().orEmpty().let {
        it.contains("уже занято", ignoreCase = true) ||
            it.contains("already taken", ignoreCase = true)
    }

private suspend fun parseClientError(e: ClientRequestException, fallback: String): String {
    return if (e.response.status.value in arrayOf(401, 403, 429, 400)) {
        runCatching { e.response.body<ErrorResponse>().detail }.getOrDefault(fallback)
    } else {
        fallback
    }
}

@Composable
internal fun ChangeServerButton(modifier: Modifier = Modifier) {
    val navController = LocalNavController.current

    TextCta(
        onClick = { navController.navigate("serverConfig") },
        modifier = Modifier.padding(horizontal = SettingsStepHorizontalPadding).then(modifier),
        leadingIcon = Icons.Filled.Storage,
    ) {
        Text(stringResource(Res.string.change_server))
    }
}

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    onBackToWelcome: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val flowState = rememberExpressiveStepFlow(AuthFlowStep.entries.size)

    var username by remember { mutableStateOf(AuthRegisterDraft.username) }
    var password by remember { mutableStateOf(AuthRegisterDraft.password) }
    var confirmPassword by remember { mutableStateOf(AuthRegisterDraft.confirmPassword) }
    var displayName by remember { mutableStateOf(AuthRegisterDraft.displayName) }
    var bio by remember { mutableStateOf(AuthRegisterDraft.bio) }
    var yandexRequired by remember { mutableStateOf(AuthRegisterDraft.yandexRequired) }
    var yandexParams by remember { mutableStateOf(AuthRegisterDraft.yandexParams) }
    var captchaRequired by remember { mutableStateOf(AuthRegisterDraft.captchaRequired) }
    var captchaParams by remember { mutableStateOf(AuthRegisterDraft.captchaParams) }
    var registrationProof by remember { mutableStateOf(AuthRegisterDraft.registrationProof) }
    var captchaToken by remember { mutableStateOf(AuthRegisterDraft.captchaToken) }
    val navController = LocalNavController.current

    fun persistDraft() {
        AuthRegisterDraft.username = username
        AuthRegisterDraft.password = password
        AuthRegisterDraft.confirmPassword = confirmPassword
        AuthRegisterDraft.displayName = displayName
        AuthRegisterDraft.bio = bio
        AuthRegisterDraft.yandexRequired = yandexRequired
        AuthRegisterDraft.yandexParams = yandexParams
        AuthRegisterDraft.captchaRequired = captchaRequired
        AuthRegisterDraft.captchaParams = captchaParams
        AuthRegisterDraft.registrationProof = registrationProof
        AuthRegisterDraft.captchaToken = captchaToken
        AuthRegisterDraft.page = flowState.pagerState.currentPage
    }

    fun snackbar(text: String, cause: Throwable? = null) {
        scope.showLoggedSnackbar(
            hostState = snackbarHostState,
            message = text,
            logTag = "Auth",
            cause = cause,
        )
    }

    val wrappedAuthSuccess: () -> Unit = {
        AuthRegisterDraft.clear()
        onAuthSuccess()
    }

    val wrappedBackToWelcome: () -> Unit = {
        AuthRegisterDraft.clear()
        onBackToWelcome()
    }

    val resetToUsername: () -> Unit = {
        username = ""
        password = ""
        confirmPassword = ""
        displayName = ""
        bio = ""
        yandexRequired = false
        yandexParams = null
        captchaRequired = false
        captchaParams = null
        registrationProof = null
        captchaToken = null
        AuthRegisterDraft.clear()
        flowState.resetPredictiveState()
        scope.launch {
            flowState.pagerState.animateScrollToPage(AuthFlowStep.Username.ordinal)
        }
    }

    DisposableEffect(Unit) {
        onDispose { persistDraft() }
    }

    LaunchedEffect(
        username,
        password,
        confirmPassword,
        displayName,
        bio,
        yandexRequired,
        yandexParams,
        captchaRequired,
        captchaParams,
        registrationProof,
        captchaToken,
    ) {
        persistDraft()
    }

    LaunchedEffect(flowState.pagerState) {
        val restored = AuthRegisterDraft.page
        if (restored in 1 until AuthFlowStep.entries.size &&
            flowState.pagerState.currentPage != restored
        ) {
            flowState.pagerState.scrollToPage(restored)
        }
        var settledPage = flowState.pagerState.currentPage
        snapshotFlow { flowState.pagerState.currentPage }
            .collect { page ->
                AuthRegisterDraft.page = page
                // Don't skip the Yandex slot mid predictive-back — that fights pager morph
                // (Profile ↔ ConfirmPassword) and glitches when the gesture is cancelled.
                if (page == AuthFlowStep.YandexId.ordinal &&
                    !yandexRequired &&
                    flowState.predictiveFromPage == null
                ) {
                    val target = if (page > settledPage) {
                        AuthFlowStep.Profile.ordinal
                    } else {
                        AuthFlowStep.ConfirmPassword.ordinal
                    }
                    settledPage = target
                    flowState.pagerState.scrollToPage(target)
                    return@collect
                }
                if (page < settledPage) {
                    when (page) {
                        AuthFlowStep.Username.ordinal -> {
                            password = ""
                            confirmPassword = ""
                            yandexRequired = false
                            yandexParams = null
                            captchaRequired = false
                            captchaParams = null
                            registrationProof = null
                            captchaToken = null
                        }

                        AuthFlowStep.Password.ordinal -> {
                            password = ""
                            confirmPassword = ""
                            yandexRequired = false
                            yandexParams = null
                            captchaRequired = false
                            captchaParams = null
                            registrationProof = null
                            captchaToken = null
                        }

                        AuthFlowStep.ConfirmPassword.ordinal -> {
                            // Keep confirm password when returning from Yandex ID / OAuth / captcha.
                            registrationProof = null
                            captchaToken = null
                        }

                        AuthFlowStep.YandexId.ordinal -> {
                            registrationProof = null
                        }
                    }
                }
                settledPage = page
            }
    }

    // After predictive back settles on the skipped Yandex slot, jump to ConfirmPassword.
    LaunchedEffect(flowState.predictiveFromPage, yandexRequired) {
        if (flowState.predictiveFromPage != null || yandexRequired) return@LaunchedEffect
        if (flowState.pagerState.currentPage == AuthFlowStep.YandexId.ordinal) {
            flowState.pagerState.scrollToPage(AuthFlowStep.ConfirmPassword.ordinal)
        }
    }

    LaunchedEffect(navController) {
        val handle = navController.currentBackStackEntry?.savedStateHandle ?: return@LaunchedEffect
        handle.getStateFlow<String?>(SmartCaptchaNav.RESULT_TOKEN, null).collect { token ->
            if (token == null) return@collect
            handle.remove<String>(SmartCaptchaNav.RESULT_TOKEN)
            Logger.i(
                SmartCaptchaLog.TAG,
                "AuthScreen received token ${SmartCaptchaLog.redactToken(token)} → Profile",
            )
            captchaToken = token
            flowState.pagerState.animateScrollToPage(AuthFlowStep.Profile.ordinal)
        }
    }

    LaunchedEffect(navController) {
        val handle = navController.currentBackStackEntry?.savedStateHandle ?: return@LaunchedEffect
        handle.getStateFlow<String?>(SmartCaptchaNav.RESULT_ERROR, null).collect { message ->
            if (message == null) return@collect
            handle.remove<String>(SmartCaptchaNav.RESULT_ERROR)
            Logger.w(SmartCaptchaLog.TAG, "AuthScreen captcha error: $message")
            snackbar(message)
        }
    }

    fun openCaptchaRoute(clientKey: String) {
        Logger.i(
            SmartCaptchaLog.TAG,
            "navigate ${SmartCaptchaNav.ROUTE} clientKey=${SmartCaptchaLog.redactKey(clientKey)}",
        )
        SmartCaptchaNav.pending = SmartCaptchaNav.Session(clientKey = clientKey)
        navController.navigate(SmartCaptchaNav.ROUTE)
    }

    val yandexStep = yandexParams
    // Clear title bar / system bars outside the rounded panel (same as WelcomeScreen).
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.extraStatusBars)),
    ) {
        AuthContentFrame { _ ->
            ExpressiveStepFlowScaffold(
                flowState = flowState,
                pages = listOf(
                    usernameStepPage(
                        username = username,
                        onUsernameChange = { username = it },
                        onContinue = {
                            flowState.pagerState.animateScrollToPage(AuthFlowStep.Password.ordinal)
                        },
                        onSnackbar = ::snackbar,
                    ),
                    passwordStepPage(
                        username = username,
                        password = password,
                        onPasswordChange = { password = it },
                        onLoginSuccess = wrappedAuthSuccess,
                        onNeedsRegister = { required, params, captchaReq, captcha ->
                            Logger.i(
                                SmartCaptchaLog.TAG,
                                "onNeedsRegister yandexRequired=$required captchaRequired=$captchaReq " +
                                    "clientKey=${SmartCaptchaLog.redactKey(captcha?.client_key)}",
                            )
                            yandexRequired = required
                            yandexParams = params
                            captchaRequired = captchaReq
                            captchaParams = captcha
                            registrationProof = null
                            captchaToken = null
                            flowState.pagerState.animateScrollToPage(AuthFlowStep.ConfirmPassword.ordinal)
                        },
                        onSnackbar = ::snackbar,
                    ),
                    confirmPasswordStepPage(
                        confirmPassword = confirmPassword,
                        onConfirmPasswordChange = { confirmPassword = it },
                        password = password,
                        onContinue = {
                            val captchaKey = captchaParams?.client_key?.trim().orEmpty()
                            when {
                                yandexRequired && yandexParams != null -> {
                                    Logger.i(SmartCaptchaLog.TAG, "confirm → YandexId (captcha skipped)")
                                    flowState.pagerState.animateScrollToPage(AuthFlowStep.YandexId.ordinal)
                                }
                                captchaRequired &&
                                    captchaToken.isNullOrBlank() &&
                                    captchaKey.isNotEmpty() -> {
                                    openCaptchaRoute(captchaKey)
                                }
                                else -> {
                                    Logger.i(
                                        SmartCaptchaLog.TAG,
                                        "confirm → Profile captchaRequired=$captchaRequired " +
                                            "hasToken=${!captchaToken.isNullOrBlank()}",
                                    )
                                    flowState.pagerState.animateScrollToPage(AuthFlowStep.Profile.ordinal)
                                }
                            }
                        },
                        onSnackbar = ::snackbar,
                    ),
                    if (yandexStep != null) {
                        yandexIdStepPage(
                            yandex = yandexStep,
                            onProof = { proof ->
                                registrationProof = proof
                                flowState.pagerState.animateScrollToPage(AuthFlowStep.Profile.ordinal)
                            },
                            onSnackbar = ::snackbar,
                        )
                    } else {
                        confirmPasswordStepPage(
                            confirmPassword = confirmPassword,
                            onConfirmPasswordChange = { confirmPassword = it },
                            password = password,
                            onContinue = {
                                val captchaKey = captchaParams?.client_key?.trim().orEmpty()
                                when {
                                    captchaRequired &&
                                        captchaToken.isNullOrBlank() &&
                                        captchaKey.isNotEmpty() -> {
                                        openCaptchaRoute(captchaKey)
                                    }
                                    else -> {
                                        Logger.i(
                                            SmartCaptchaLog.TAG,
                                            "yandex-placeholder confirm → Profile",
                                        )
                                        flowState.pagerState.animateScrollToPage(AuthFlowStep.Profile.ordinal)
                                    }
                                }
                            },
                            onSnackbar = ::snackbar,
                        )
                    },
                    profileStepPage(
                        username = username,
                        displayName = displayName,
                        onDisplayNameChange = { displayName = it },
                        bio = bio,
                        onBioChange = { bio = it },
                        password = password,
                        registrationProof = registrationProof,
                        captchaToken = captchaToken,
                        onRegisterSuccess = wrappedAuthSuccess,
                        onUsernameTaken = resetToUsername,
                        onSnackbar = ::snackbar,
                    ),
                ),
                snackbarHostState = snackbarHostState,
                onBackAtFirstPage = wrappedBackToWelcome,
                trailingTopBarActions = { PreAuthOverflowMenu() },
                contentBackground = MaterialTheme.colorScheme.background,
                topBarWindowInsets = WindowInsets(0, 0, 0, 0),
            )
        }
    }
}
