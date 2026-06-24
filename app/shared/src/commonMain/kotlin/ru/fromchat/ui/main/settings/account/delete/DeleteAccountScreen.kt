package ru.fromchat.ui.main.settings.account.delete

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.launch
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.WebSocketManager
import ru.fromchat.ui.components.ExpressiveStepFlowScaffold
import ru.fromchat.ui.components.rememberExpressiveStepFlow
import ru.fromchat.ui.components.showReplacingSnackbar

private enum class DeleteAccountFlowStep {
    Consequences,
    Password,
    Final,
}

internal object DeleteAccountDraft {
    var passwordDerived: String = ""

    fun clear() {
        passwordDerived = ""
    }
}

private class DeleteAccountWrongPasswordException : Exception()

internal suspend fun verifyPasswordForDeletion(passwordDerived: String) {
    try {
        ApiClient.verifyPasswordDerived(passwordDerived)
    } catch (e: ClientRequestException) {
        if (e.response.status.value == 400) {
            throw DeleteAccountWrongPasswordException()
        }
        throw e
    }
}

internal suspend fun deleteAccountWithDerivedPassword(passwordDerived: String) {
    ApiClient.deleteAccount(passwordDerived)
}

internal fun passwordVerifyErrorMessage(
    error: Throwable,
    wrongPassword: String,
    errUnexpected: String,
) = when (error) {
    is DeleteAccountWrongPasswordException -> wrongPassword
    is ClientRequestException -> "Error ${error.response.status.value}"
    else -> error.message ?: errUnexpected
}

internal fun deleteErrorMessage(error: Throwable, errUnexpected: String) =
    (error as? ClientRequestException)?.response?.let { "Error ${it.status.value}" }
        ?: error.message
        ?: errUnexpected

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeleteAccountScreen(onBack: () -> Unit, onDeleted: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val flowState = rememberExpressiveStepFlow(DeleteAccountFlowStep.entries.size)

    var password by remember { mutableStateOf("") }
    var confirmationPhrase by remember { mutableStateOf("") }

    fun showSnack(text: String) {
        scope.launch {
            snackbarHostState.showReplacingSnackbar(
                message = text,
                withDismissAction = false,
                duration = SnackbarDuration.Short,
            )
        }
    }

    LaunchedEffect(Unit) {
        DeleteAccountDraft.clear()
        password = ""
        confirmationPhrase = ""
    }

    ExpressiveStepFlowScaffold(
        flowState = flowState,
        pages = listOf(
            consequencesStepPage(
                onContinue = { flowState.pagerState.animateScrollToPage(1) },
            ),
            passwordVerifyStepPage(
                password = password,
                onPasswordChange = { password = it },
                onContinue = { derived ->
                    DeleteAccountDraft.passwordDerived = derived
                    flowState.pagerState.animateScrollToPage(2)
                },
                onSnackbar = ::showSnack,
            ),
            finalWarningStepPage(
                confirmationPhrase = confirmationPhrase,
                onConfirmationPhraseChange = { confirmationPhrase = it },
                passwordDerived = DeleteAccountDraft.passwordDerived,
                onDeleted = {
                    scope.launch {
                        WebSocketManager.disconnect()
                        ApiClient.clearLocalSession()
                        DeleteAccountDraft.clear()
                        onDeleted()
                    }
                },
                onSnackbar = ::showSnack,
            ),
        ),
        snackbarHostState = snackbarHostState,
        onBackAtFirstPage = onBack,
    )
}
