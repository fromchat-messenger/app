package ru.fromchat.ui.main.settings.account.changeyandex

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.settings_done
import ru.fromchat.settings_yandex_step_done_body
import ru.fromchat.settings_yandex_step_done_title
import ru.fromchat.ui.components.ActionButton
import ru.fromchat.ui.components.ExpressiveHeroSpec
import ru.fromchat.ui.components.ExpressiveStepFlowScaffold
import ru.fromchat.ui.components.ExpressiveStepPage
import ru.fromchat.ui.components.ExpressiveStepPageHeader
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.components.rememberExpressiveStepFlow

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChangeYandexDoneScreen(onDone: () -> Unit) {
    val flowState = rememberExpressiveStepFlow(1)
    val snackbarHostState = remember { SnackbarHostState() }
    val title = stringResource(Res.string.settings_yandex_step_done_title)
    val body = stringResource(Res.string.settings_yandex_step_done_body)
    val done = stringResource(Res.string.settings_done)
    val colorScheme = MaterialTheme.colorScheme

    ExpressiveStepFlowScaffold(
        flowState = flowState,
        pages = listOf(
            ExpressiveStepPage(
                hero = ExpressiveHeroSpec(
                    icon = Icons.Filled.CheckCircle,
                    polygon = MaterialShapes.Cookie9Sided.normalized(),
                    containerColor = colorScheme.tertiaryContainer,
                    contentColor = colorScheme.onTertiaryContainer,
                ),
                content = {
                    ExpressiveStepPageHeader(title = title, body = body)
                },
                button = {
                    ActionButton(
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(done)
                    }
                },
            ),
        ),
        snackbarHostState = snackbarHostState,
        onBackAtFirstPage = onDone,
    )
}
