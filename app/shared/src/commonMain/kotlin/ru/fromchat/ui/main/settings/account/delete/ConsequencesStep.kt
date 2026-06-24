package ru.fromchat.ui.main.settings.account.delete

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pr0gramm3r101.components.Category
import com.pr0gramm3r101.components.ListItem
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.settings_delete_consequence_chat_history
import ru.fromchat.settings_delete_consequence_messages
import ru.fromchat.settings_delete_consequence_permanent
import ru.fromchat.settings_delete_consequence_username
import ru.fromchat.settings_delete_step_intro_body
import ru.fromchat.settings_delete_step_intro_title
import ru.fromchat.settings_next
import ru.fromchat.ui.components.ActionButton
import ru.fromchat.ui.components.ExpressiveHeroSpec
import ru.fromchat.ui.components.ExpressiveStepPage
import ru.fromchat.ui.components.ExpressiveStepPageHeader
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.main.settings.SettingsStepHorizontalPadding

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun consequencesStepPage(
    onContinue: suspend () -> Unit,
): ExpressiveStepPage {
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme

    return ExpressiveStepPage(
        hero = ExpressiveHeroSpec(
            icon = Icons.Filled.DeleteForever,
            polygon = MaterialShapes.Cookie4Sided.normalized(),
            containerColor = scheme.errorContainer,
            contentColor = scheme.onErrorContainer,
        ),
        content = {
            ExpressiveStepPageHeader(
                title = stringResource(Res.string.settings_delete_step_intro_title),
                body = stringResource(Res.string.settings_delete_step_intro_body),
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                Category(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    margin = PaddingValues(horizontal = SettingsStepHorizontalPadding),
                    containerColor = scheme.surfaceContainerLow,
                ) {
                    ListItem(
                        headline = stringResource(Res.string.settings_delete_consequence_chat_history),
                        leadingContent = {
                            Icon(Icons.Rounded.Delete, null)
                        },
                        divider = true,
                    )

                    ListItem(
                        headline = stringResource(Res.string.settings_delete_consequence_username),
                        leadingContent = {
                            Icon(Icons.Rounded.AlternateEmail, null)
                        },
                        divider = true,
                    )

                    ListItem(
                        headline = stringResource(Res.string.settings_delete_consequence_messages),
                        leadingContent = {
                            Icon(Icons.Rounded.PersonOff, null)
                        },
                        divider = true,
                    )

                    ListItem(
                        headline = stringResource(Res.string.settings_delete_consequence_permanent),
                        leadingContent = {
                            Icon(Icons.Rounded.Block, null)
                        },
                    )
                }
            }
        },
        button = {
            ActionButton(
                onClick = { scope.launch { onContinue() } },
                destructive = true,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.settings_next))
            }
        },
    )
}
