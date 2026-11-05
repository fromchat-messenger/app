package ru.fromchat.ui.main.settings.server

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.instance.InstanceEncryptionMode
import ru.fromchat.api.instance.ServerProbeResult
import ru.fromchat.server_config_info_encryption
import ru.fromchat.server_config_info_instance
import ru.fromchat.server_config_info_protocol
import ru.fromchat.server_config_snackbar_timeout
import ru.fromchat.server_config_unsupported_no_instance_id
import ru.fromchat.server_config_info_instance
import ru.fromchat.server_config_info_protocol
import ru.fromchat.server_config_probe_pending
import ru.fromchat.server_config_snackbar_api_fail
import ru.fromchat.server_config_snackbar_timeout
import ru.fromchat.server_config_unsupported_no_instance_id
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.main.settings.SettingsStepHorizontalPadding

@Composable
fun ServerInstanceProbeCard(
    probe: ServerProbeResult?,
    probing: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SettingsStepHorizontalPadding),
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        AnimatedContent(
            targetState = probe to probing,
            transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(180))
            },
            label = "serverProbeInfo",
        ) { (result, busy) ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when {
                    busy && result == null -> {
                        Text(
                            stringResource(Res.string.server_config_probe_pending),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    result is ServerProbeResult.Supported -> {
                        InfoRow(
                            stringResource(Res.string.server_config_info_instance),
                            result.instanceId,
                        )
                        InfoRow(
                            stringResource(Res.string.server_config_info_protocol),
                            result.protocol,
                        )
                        val modeLabel = InstanceEncryptionMode.fromWire(result.encryptionMode)
                            ?.let { InstanceEncryptionMode.displayLabel(it) }
                            ?: result.encryptionMode
                        InfoRow(
                            stringResource(Res.string.server_config_info_encryption),
                            modeLabel,
                        )
                    }
                    result is ServerProbeResult.Timeout -> {
                        Text(
                            stringResource(Res.string.server_config_snackbar_timeout),
                            color = scheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    result is ServerProbeResult.Unreachable -> {
                        Text(
                            stringResource(Res.string.server_config_snackbar_api_fail),
                            color = scheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    result is ServerProbeResult.Unsupported -> {
                        Text(
                            stringResource(Res.string.server_config_unsupported_no_instance_id),
                            color = scheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    else -> {
                        Text(
                            stringResource(Res.string.server_config_probe_pending),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurface,
        )
    }
}
