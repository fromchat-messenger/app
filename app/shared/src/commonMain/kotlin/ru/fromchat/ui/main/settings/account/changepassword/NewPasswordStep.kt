package ru.fromchat.ui.main.settings.account.changepassword

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.password_length_error
import ru.fromchat.settings_new_password
import ru.fromchat.settings_next
import ru.fromchat.settings_security_step_new_body
import ru.fromchat.settings_security_step_new_title
import ru.fromchat.ui.components.ActionButton
import ru.fromchat.ui.components.ExpressiveHeroSpec
import ru.fromchat.ui.components.ExpressiveStepLazyListIndices
import ru.fromchat.ui.components.ExpressiveStepPage
import ru.fromchat.ui.components.ExpressiveStepPageHeader
import ru.fromchat.ui.components.SettingsPasswordOutlineFieldShape
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.components.expressiveStepFieldColors
import ru.fromchat.ui.components.trackImeScrollTarget
import ru.fromchat.ui.main.settings.SettingsStepHorizontalPadding

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun newPasswordStepPage(
    newPassword: String,
    onNewPasswordChange: (String) -> Unit,
    onContinue: suspend () -> Unit,
    onSnackbar: (String) -> Unit,
): ExpressiveStepPage {
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme

    val pwdLen = stringResource(Res.string.password_length_error)
    val nextLabel = stringResource(Res.string.settings_next)

    return ExpressiveStepPage(
        hero = ExpressiveHeroSpec(
            icon = Icons.Filled.Lock,
            polygon = MaterialShapes.Cookie6Sided.normalized(),
            containerColor = scheme.tertiaryContainer,
            contentColor = scheme.onTertiaryContainer,
        ),
        content = { imeScroll ->
            ExpressiveStepPageHeader(
                title = stringResource(Res.string.settings_security_step_new_title),
                body = stringResource(Res.string.settings_security_step_new_body),
            )

            OutlinedTextField(
                value = newPassword,
                onValueChange = onNewPasswordChange,
                label = { Text(stringResource(Res.string.settings_new_password)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .trackImeScrollTarget(imeScroll, ExpressiveStepLazyListIndices.STEPS_BODY)
                    .padding(horizontal = SettingsStepHorizontalPadding),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                colors = expressiveStepFieldColors(),
                shape = SettingsPasswordOutlineFieldShape,
            )
        },
        button = {
            ActionButton(
                onClick = {
                    if (newPassword.length !in 5..50) {
                        onSnackbar(pwdLen)
                        return@ActionButton
                    }

                    scope.launch { onContinue() }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(nextLabel)
            }
        },
    )
}
