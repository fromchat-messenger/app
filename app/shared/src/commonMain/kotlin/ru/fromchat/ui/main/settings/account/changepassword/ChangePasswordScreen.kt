package ru.fromchat.ui.main.settings.account.changepassword

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
import kotlinx.coroutines.launch
import ru.fromchat.ui.components.ExpressiveStepFlowScaffold
import ru.fromchat.ui.components.rememberExpressiveStepFlow
import ru.fromchat.ui.components.showReplacingSnackbar

private enum class ChangePasswordFlowStep {
    Current,
    New,
    Confirm,
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChangePasswordScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val flowState = rememberExpressiveStepFlow(ChangePasswordFlowStep.entries.size)

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

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
        currentPassword = ""
        newPassword = ""
        confirmPassword = ""
    }

    ExpressiveStepFlowScaffold(
        flowState = flowState,
        pages = listOf(
            currentPasswordStepPage(
                currentPassword = currentPassword,
                onCurrentPasswordChange = { currentPassword = it },
                onContinue = { flowState.pagerState.animateScrollToPage(1) },
                onSnackbar = ::showSnack,
            ),
            newPasswordStepPage(
                newPassword = newPassword,
                onNewPasswordChange = { newPassword = it },
                onContinue = { flowState.pagerState.animateScrollToPage(2) },
                onSnackbar = ::showSnack,
            ),
            confirmPasswordStepPage(
                currentPassword = currentPassword,
                newPassword = newPassword,
                confirmPassword = confirmPassword,
                onConfirmPasswordChange = { confirmPassword = it },
                onDone = onDone,
                onSnackbar = ::showSnack,
            ),
        ),
        snackbarHostState = snackbarHostState,
        onBackAtFirstPage = onBack,
    )
}
