package ru.fromchat.ui.main.settings.server

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
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
import ru.fromchat.server_config_probe_pending
import ru.fromchat.server_config_snackbar_api_fail
import ru.fromchat.server_config_snackbar_timeout
import ru.fromchat.server_config_unsupported_no_instance_id
import ru.fromchat.ui.components.ExpressiveListItem
import ru.fromchat.ui.components.ExpressiveTints
import ru.fromchat.ui.main.settings.SettingsStepHorizontalPadding
import ru.fromchat.utils.components.Category

private val serverProbeCategoryMargin = PaddingValues(
    start = SettingsStepHorizontalPadding,
    end = SettingsStepHorizontalPadding,
    top = 0.dp,
    bottom = 12.dp,
)

@Composable
fun LazyItemScope.ServerInstanceProbeCard(
    probe: ServerProbeResult?,
    probing: Boolean,
    modifier: Modifier = Modifier,
) {
    Category(
        modifier = modifier,
        margin = serverProbeCategoryMargin,
    ) {
        AnimatedContent(
            targetState = probe to probing,
            transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(180))
            },
            label = "serverProbeInfo",
        ) { (result, busy) ->
            when {
                busy && result == null -> {
                    ExpressiveListItem(
                        icon = Icons.Rounded.FlashOn,
                        iconPreset = ExpressiveTints.Cyan,
                        headline = stringResource(Res.string.server_config_probe_pending),
                        supportingText = null,
                        enabled = false,
                    )
                }
                result is ServerProbeResult.Supported -> {
                    ExpressiveListItem(
                        icon = Icons.Rounded.Key,
                        iconPreset = ExpressiveTints.Blue,
                        headline = stringResource(Res.string.server_config_info_instance),
                        supportingText = result.instanceId,
                        divider = true,
                    )
                    ExpressiveListItem(
                        icon = Icons.Rounded.Link,
                        iconPreset = ExpressiveTints.Cyan,
                        headline = stringResource(Res.string.server_config_info_protocol),
                        supportingText = result.protocol,
                        divider = true,
                    )
                    val modeLabel = InstanceEncryptionMode.fromWire(result.encryptionMode)
                        ?.let { InstanceEncryptionMode.displayLabel(it) }
                        ?: result.encryptionMode
                    ExpressiveListItem(
                        icon = Icons.Rounded.Lock,
                        iconPreset = ExpressiveTints.Green,
                        headline = stringResource(Res.string.server_config_info_encryption),
                        supportingText = modeLabel,
                    )
                }
                result is ServerProbeResult.Timeout -> {
                    ProbeErrorListItem(stringResource(Res.string.server_config_snackbar_timeout))
                }
                result is ServerProbeResult.Unreachable -> {
                    ProbeErrorListItem(stringResource(Res.string.server_config_snackbar_api_fail))
                }
                result is ServerProbeResult.Unsupported -> {
                    ProbeErrorListItem(stringResource(Res.string.server_config_unsupported_no_instance_id))
                }
                else -> {
                    ExpressiveListItem(
                        icon = Icons.Rounded.FlashOn,
                        iconPreset = ExpressiveTints.Cyan,
                        headline = stringResource(Res.string.server_config_probe_pending),
                        supportingText = null,
                        enabled = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProbeErrorListItem(message: String) {
    ExpressiveListItem(
        icon = Icons.Rounded.ErrorOutline,
        iconPreset = ExpressiveTints.Rose,
        headline = message,
        supportingText = null,
    )
}
