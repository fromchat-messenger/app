package ru.fromchat.ui.main.settings.account.delete

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.error_unexpected
import ru.fromchat.settings_delete_account_button
import ru.fromchat.settings_delete_confirm_phrase
import ru.fromchat.settings_delete_confirm_phrase_instruction
import ru.fromchat.settings_delete_confirm_phrase_quote
import ru.fromchat.settings_delete_step_final_body
import ru.fromchat.settings_delete_step_final_title
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
internal fun finalWarningStepPage(
    confirmationPhrase: String,
    onConfirmationPhraseChange: (String) -> Unit,
    passwordDerived: String,
    onDeleted: () -> Unit,
    onSnackbar: (String) -> Unit,
): ExpressiveStepPage {
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme

    val errUnexpected = stringResource(Res.string.error_unexpected)
    val requiredPhrase = stringResource(Res.string.settings_delete_confirm_phrase)

    var busy by remember { mutableStateOf(false) }
    val phraseMatches = confirmationPhrase.equals(requiredPhrase, ignoreCase = true)

    return ExpressiveStepPage(
        hero = ExpressiveHeroSpec(
            icon = Icons.Filled.Warning,
            polygon = MaterialShapes.Cookie7Sided.normalized(),
            containerColor = scheme.tertiaryContainer,
            contentColor = scheme.onTertiaryContainer,
        ),
        content = { imeScroll ->
            ExpressiveStepPageHeader(
                title = stringResource(Res.string.settings_delete_step_final_title),
                body = stringResource(Res.string.settings_delete_step_final_body),
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(
                        Res.string.settings_delete_confirm_phrase_instruction,
                        stringResource(Res.string.settings_delete_confirm_phrase_quote),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SettingsStepHorizontalPadding)
                        .padding(top = 4.dp, bottom = 12.dp),
                    textAlign = TextAlign.Start,
                )

                OutlinedTextField(
                    value = confirmationPhrase,
                    onValueChange = onConfirmationPhraseChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .trackImeScrollTarget(imeScroll, ExpressiveStepLazyListIndices.STEPS_BODY)
                        .padding(horizontal = SettingsStepHorizontalPadding),
                    enabled = !busy,
                    singleLine = true,
                    colors = expressiveStepFieldColors(),
                    shape = SettingsPasswordOutlineFieldShape,
                )
            }
        },
        button = {
            ActionButton(
                onClick = {
                    if (!phraseMatches) return@ActionButton

                    if (passwordDerived.isBlank()) {
                        onSnackbar(errUnexpected)
                        return@ActionButton
                    }

                    scope.launch {
                        busy = true

                        try {
                            deleteAccountWithDerivedPassword(passwordDerived)
                            onDeleted()
                        } catch (e: Exception) {
                            onSnackbar(deleteErrorMessage(e, errUnexpected))
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = phraseMatches,
                loading = busy,
                destructive = true,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.settings_delete_account_button))
            }
        },
    )
}
