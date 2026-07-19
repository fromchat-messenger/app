package ru.fromchat.ui.auth

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
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
import com.pr0gramm3r101.utils.crypto.deriveAuthSecret
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.ApiClient
import ru.fromchat.api.crypto.IdentityKeyManager
import ru.fromchat.api.local.cache.CacheContext
import ru.fromchat.api.local.db.clearAccountCacheOnLogout
import ru.fromchat.api.instance.ServerProbeResult
import ru.fromchat.api.instance.probeServer
import ru.fromchat.api.schema.core.ErrorResponse
import ru.fromchat.api.schema.user.auth.LoginResponse
import ru.fromchat.api.schema.user.auth.RegisterConfirmRequest
import ru.fromchat.api.schema.user.auth.YandexOAuthParams
import ru.fromchat.change_server
import ru.fromchat.config.Settings
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.auth.register.confirmPasswordStepPage
import ru.fromchat.ui.auth.register.profileStepPage
import ru.fromchat.ui.auth.yandex.yandexIdStepPage
import ru.fromchat.ui.components.ExpressiveStepFlowScaffold
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.components.TextCta
import ru.fromchat.ui.components.rememberExpressiveStepFlow
import ru.fromchat.ui.components.showLoggedSnackbar
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

        is ApiClient.AuthPasswordStepOutcome.NeedsRegister -> PasswordStepResult.NeedsRegister(
            yandexRequired = outcome.yandexRequired,
            yandex = outcome.yandex,
        )
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
    unexpectedError: String,
) = try {
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
            ),
        )
    }
    RegisterResult.Success
} catch (e: ClientRequestException) {
    if (e.response.status.value == 400 && isUsernameTakenError(e)) {
        RegisterResult.UsernameTaken
    } else {
        RegisterResult.Error(parseClientError(e, unexpectedError))
    }
} catch (e: Exception) {
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
    var registrationProof by remember { mutableStateOf(AuthRegisterDraft.registrationProof) }

    fun persistDraft() {
        AuthRegisterDraft.username = username
        AuthRegisterDraft.password = password
        AuthRegisterDraft.confirmPassword = confirmPassword
        AuthRegisterDraft.displayName = displayName
        AuthRegisterDraft.bio = bio
        AuthRegisterDraft.yandexRequired = yandexRequired
        AuthRegisterDraft.yandexParams = yandexParams
        AuthRegisterDraft.registrationProof = registrationProof
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
        registrationProof = null
        AuthRegisterDraft.clear()
        flowState.resetPredictiveState()
        scope.launch {
            flowState.pagerState.animateScrollToPage(AuthFlowStep.Username.ordinal)
        }
    }

    DisposableEffect(Unit) {
        onDispose { persistDraft() }
    }

    LaunchedEffect(username, password, confirmPassword, displayName, bio, yandexRequired, yandexParams, registrationProof) {
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
                if (page == AuthFlowStep.YandexId.ordinal && !yandexRequired) {
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
                            registrationProof = null
                        }

                        AuthFlowStep.Password.ordinal -> {
                            password = ""
                            confirmPassword = ""
                            yandexRequired = false
                            yandexParams = null
                            registrationProof = null
                        }

                        AuthFlowStep.ConfirmPassword.ordinal -> {
                            // Keep confirm password when returning from Yandex ID / OAuth.
                            registrationProof = null
                        }

                        AuthFlowStep.YandexId.ordinal -> {
                            registrationProof = null
                        }
                    }
                }
                settledPage = page
            }
    }

    val yandexStep = yandexParams
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
                onNeedsRegister = { required, params ->
                    yandexRequired = required
                    yandexParams = params
                    registrationProof = null
                    flowState.pagerState.animateScrollToPage(AuthFlowStep.ConfirmPassword.ordinal)
                },
                onSnackbar = ::snackbar,
            ),
            confirmPasswordStepPage(
                confirmPassword = confirmPassword,
                onConfirmPasswordChange = { confirmPassword = it },
                password = password,
                onContinue = {
                    if (yandexRequired && yandexParams != null) {
                        flowState.pagerState.animateScrollToPage(AuthFlowStep.YandexId.ordinal)
                    } else {
                        flowState.pagerState.animateScrollToPage(AuthFlowStep.Profile.ordinal)
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
                        flowState.pagerState.animateScrollToPage(AuthFlowStep.Profile.ordinal)
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
                onRegisterSuccess = wrappedAuthSuccess,
                onUsernameTaken = resetToUsername,
                onSnackbar = ::snackbar,
            ),
        ),
        snackbarHostState = snackbarHostState,
        onBackAtFirstPage = wrappedBackToWelcome,
    )
}
