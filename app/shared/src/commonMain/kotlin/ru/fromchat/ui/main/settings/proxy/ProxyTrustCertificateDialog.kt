package ru.fromchat.ui.main.settings.proxy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.proxy_trust_cert_body
import ru.fromchat.proxy_trust_cert_title
import ru.fromchat.proxy_trust_once
import ru.fromchat.proxy_trust_yes
import ru.fromchat.ui.components.Text

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProxyTrustCertificateDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onTrustPermanent: () -> Unit,
    onTrustSession: () -> Unit,
) {
    if (!visible) return
    val scheme = MaterialTheme.colorScheme
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = scheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = scheme.primary,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.proxy_trust_cert_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.proxy_trust_cert_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onTrustPermanent,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.proxy_trust_yes))
                }
                TextButton(
                    onClick = onTrustSession,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.proxy_trust_once))
                }
            }
        }
    }
}
