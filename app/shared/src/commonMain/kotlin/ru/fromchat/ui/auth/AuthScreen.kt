package ru.fromchat.ui.auth

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
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
import ru.fromchat.api.schema.user.auth.LoginRequest
import ru.fromchat.api.schema.user.auth.LoginResponse
import ru.fromchat.api.schema.user.auth.RegisterRequest
import ru.fromchat.change_server
import ru.fromchat.config.Settings
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.auth.register.confirmPasswordStepPage
import ru.fromchat.ui.auth.register.profileStepPage
import ru.fromchat.ui.components.ExpressiveStepFlowScaffold
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.components.TextCta
import ru.fromchat.ui.components.rememberExpressiveStepFlow
import ru.fromchat.ui.components.showReplacingSnackbar
import ru.fromchat.ui.main.settings.SettingsStepHorizontalPadding
import kotlin.time.Duration.Companion.milliseconds

private enum class AuthFlowStep {
    Username,
    Password,
    ConfirmPassword,
    Profile,
}

internal sealed interface PasswordStepResult {
    data object LoginSuccess : PasswordStepResult
    data object AdvanceToRegister : PasswordStepResult
    data class WrongPassword(val message: String) : PasswordStepResult
    data class RateLimited(val message: String) : PasswordStepResult
    data class Error(val message: String) : PasswordStepResult
}

internal sealed interface RegisterResult {
    data object Success : RegisterResult
    data object UsernameTaken : RegisterResult
    data class Error(val message: String) : RegisterResult
}

private const val AUTH_SERVER_PROBE_TIMEOUT_MS = 5_000L

internal suspend fun probeCurrentServer() = runCatching {
    withTimeout(AUTH_SERVER_PROBE_TIMEOUT_MS.milliseconds) {
        probeServer(Settings.readServerConfig()) is ServerProbeResult.Supported
    }
}.getOrDefault(false)

internal suspend fun authBranch(
    username: String,
    password: String,
    wrongPasswordMessage: String,
    rateLimitMessage: String,
    unexpectedError: String,
) = login(
    username.trim(),
    password,
    wrongPasswordMessage,
    rateLimitMessage,
    unexpectedError,
).let { result ->
    if (
        result is PasswordStepResult.WrongPassword &&
        !runCatching { ApiClient.checkUsername(username.trim()).exists }.getOrDefault(true)
    ) {
        PasswordStepResult.AdvanceToRegister
    } else {
        result
    }
}

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

private suspend fun login(
    username: String,
    password: String,
    wrongPasswordMessage: String,
    rateLimitMessage: String,
    unexpectedError: String,
) = try {
    fullLogin(username, password.trim()) {
        ApiClient.loginRequest(
            LoginRequest(username, deriveAuthSecret(username, password.trim())),
        )
    }

    PasswordStepResult.LoginSuccess
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
} catch (_: Exception) {
    PasswordStepResult.Error(unexpectedError)
}

internal suspend fun register(
    username: String,
    displayName: String,
    password: String,
    bio: String,
    unexpectedError: String,
) = try {
    if (runCatching { ApiClient.checkUsername(username.trim()).exists }.getOrDefault(false)) {
        RegisterResult.UsernameTaken
    } else {
        fullLogin(username.trim(), password.trim()) {
            val derived = deriveAuthSecret(username.trim(), password.trim())

            ApiClient.registerRequest(
                RegisterRequest(
                    username = username.trim(),
                    display_name = displayName.trim(),
                    password = derived,
                    confirm_password = derived,
                    bio = bio.trim().takeIf { it.isNotEmpty() },
                ),
            )
        }

        RegisterResult.Success
    }
} catch (e: ClientRequestException) {
    if (e.response.status.value == 400 && isUsernameTakenError(e)) {
        RegisterResult.UsernameTaken
    } else {
        RegisterResult.Error(parseClientError(e, unexpectedError))
    }
} catch (_: Exception) {
    RegisterResult.Error(unexpectedError)
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

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }

    fun snackbar(text: String) {
        scope.launch {
            snackbarHostState.showReplacingSnackbar(
                message = text,
                withDismissAction = false,
                duration = SnackbarDuration.Short,
            )
        }
    }

    val resetToUsername: () -> Unit = {
        username = ""
        password = ""
        confirmPassword = ""
        displayName = ""
        bio = ""
        flowState.resetPredictiveState()
        scope.launch {
            flowState.pagerState.animateScrollToPage(AuthFlowStep.Username.ordinal)
        }
    }

    LaunchedEffect(Unit) {
        username = ""
        password = ""
        confirmPassword = ""
        displayName = ""
        bio = ""
    }

    LaunchedEffect(flowState.pagerState) {
        var settledPage = flowState.pagerState.currentPage
        snapshotFlow { flowState.pagerState.currentPage }
            .collect { page ->
                if (page < settledPage) {
                    when (page) {
                        AuthFlowStep.Username.ordinal -> {
                            password = ""
                            confirmPassword = ""
                        }

                        AuthFlowStep.Password.ordinal -> {
                            password = ""
                            confirmPassword = ""
                        }

                        AuthFlowStep.ConfirmPassword.ordinal -> {
                            confirmPassword = ""
                        }
                    }
                }
                settledPage = page
            }
    }

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
                onLoginSuccess = onAuthSuccess,
                onRegister = {
                    flowState.pagerState.animateScrollToPage(AuthFlowStep.ConfirmPassword.ordinal)
                },
                onSnackbar = ::snackbar,
            ),
            confirmPasswordStepPage(
                confirmPassword = confirmPassword,
                onConfirmPasswordChange = { confirmPassword = it },
                password = password,
                onContinue = {
                    flowState.pagerState.animateScrollToPage(AuthFlowStep.Profile.ordinal)
                },
                onSnackbar = ::snackbar,
            ),
            profileStepPage(
                username = username,
                displayName = displayName,
                onDisplayNameChange = { displayName = it },
                bio = bio,
                onBioChange = { bio = it },
                password = password,
                onRegisterSuccess = onAuthSuccess,
                onUsernameTaken = resetToUsername,
                onSnackbar = ::snackbar,
            ),
        ),
        snackbarHostState = snackbarHostState,
        onBackAtFirstPage = onBackToWelcome,
    )
}
