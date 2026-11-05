package ru.fromchat.ui.incognito

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.cancel
import ru.fromchat.incognito_connect_anyway
import ru.fromchat.public_http_warning_body
import ru.fromchat.public_http_warning_title
import ru.fromchat.incognito_mitm_certificate_warning_body
import ru.fromchat.incognito_mitm_certificate_warning_title
import ru.fromchat.incognito_tls_certificate_error_body
import ru.fromchat.incognito_tls_certificate_error_title
import ru.fromchat.Res
import ru.fromchat.ui.components.ExpressiveIconFrame
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.components.TextCta

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IncognitoTlsDialogHost() {
    val controller = IncognitoTlsDialogController

    if (controller.showTlsDialog) {
        AlertDialog(
            onDismissRequest = { controller.dismissTls() },
            icon = {
                ExpressiveIconFrame(
                    icon = Icons.Rounded.Shield,
                    containerSize = 56.dp,
                    iconSize = 28.dp,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    materialPolygon = MaterialShapes.SoftBurst,
                )
            },
            title = { Text(stringResource(Res.string.incognito_tls_certificate_error_title)) },
            text = { Text(stringResource(Res.string.incognito_tls_certificate_error_body)) },
            dismissButton = {
                TextCta(onClick = { controller.dismissTls() }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
            confirmButton = {
                TextCta(onClick = { controller.confirmBypass() }) {
                    Text(stringResource(Res.string.incognito_connect_anyway))
                }
            },
        )
    }

    if (controller.showPublicHttpDialog) {
        AlertDialog(
            onDismissRequest = { controller.dismissPublicHttp() },
            icon = {
                ExpressiveIconFrame(
                    icon = Icons.Rounded.Shield,
                    containerSize = 56.dp,
                    iconSize = 28.dp,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    materialPolygon = MaterialShapes.SoftBurst,
                )
            },
            title = { Text(stringResource(Res.string.public_http_warning_title)) },
            text = { Text(stringResource(Res.string.public_http_warning_body)) },
            dismissButton = {
                TextCta(onClick = { controller.dismissPublicHttp() }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
            confirmButton = {
                TextCta(onClick = { controller.confirmPublicHttpBypass() }) {
                    Text(stringResource(Res.string.incognito_connect_anyway))
                }
            },
        )
    }

    if (controller.showMitmDialog) {
        AlertDialog(
            onDismissRequest = { controller.dismissMitm() },
            icon = {
                ExpressiveIconFrame(
                    icon = Icons.Rounded.Shield,
                    containerSize = 56.dp,
                    iconSize = 28.dp,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    materialPolygon = MaterialShapes.SoftBurst,
                )
            },
            title = { Text(stringResource(Res.string.incognito_mitm_certificate_warning_title)) },
            text = {
                Text(
                    stringResource(
                        Res.string.incognito_mitm_certificate_warning_body,
                        controller.mitmMarker,
                    ),
                )
            },
            dismissButton = {
                TextCta(onClick = { controller.dismissMitm() }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
            confirmButton = {
                TextCta(onClick = { controller.confirmBypass() }) {
                    Text(stringResource(Res.string.incognito_connect_anyway))
                }
            },
        )
    }
}
