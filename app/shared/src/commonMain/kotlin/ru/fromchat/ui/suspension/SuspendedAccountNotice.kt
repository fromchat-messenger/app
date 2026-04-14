package ru.fromchat.ui.suspension

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Button
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.ApiClient
import ru.fromchat.*

enum class SuspendedAccountBannerStyle {
    Tabs,
    ChatInput
}

@Composable
fun SuspendedAccountBanner(
    reason: String,
    title: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    style: SuspendedAccountBannerStyle = SuspendedAccountBannerStyle.ChatInput
) {
    when (style) {
        SuspendedAccountBannerStyle.Tabs -> {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(20.dp),
                modifier = modifier
                    .fillMaxWidth()
                    .clickable(onClick = onTap)
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "🚫",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3
                        )
                    }
                }
            }
        }

        SuspendedAccountBannerStyle.ChatInput -> {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .clickable { onTap() }
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SuspendedAccountSupportSheet(
    isVisible: Boolean,
    onDismissRequest: () -> Unit,
) {
    if (!isVisible) return

    val sheetData by ApiClient.suspensionState.collectAsState()
    val title = stringResource(Res.string.suspended_sheet_title)
    val description = stringResource(Res.string.suspended_sheet_desc)
    val reason = sheetData.reason?.ifBlank { null } ?: stringResource(Res.string.suspended_default_reason)
    val supportButtonLabel = stringResource(Res.string.suspended_sheet_action_contact_support)
    val closeLabel = stringResource(Res.string.cd_close)

    val uriHandler = LocalUriHandler.current
    val onContact = { uriHandler.openUri("https://t.me/fromchat_ch?direct") }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val closeSheet: () -> Unit = {
        scope.launch {
            runCatching { sheetState.hide() }
            onDismissRequest()
        }
    }

    ModalBottomSheet(
        onDismissRequest = closeSheet,
        sheetState = sheetState,
        dragHandle = null
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            BottomSheetDefaults.DragHandle()
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(12.dp),
                textAlign = TextAlign.Center
            )
            Button(
                onClick = {
                    scope.launch {
                        runCatching { sheetState.hide() }
                        onContact()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = supportButtonLabel)
            }
            OutlinedButton(
                onClick = closeSheet,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = closeLabel)
            }
        }
    }
}

@Composable
fun SuspendedAccountNoticeHost(
    isSuspended: Boolean,
    reason: String?,
    fallbackReason: String,
    bannerTitle: String,
    style: SuspendedAccountBannerStyle,
    modifier: Modifier = Modifier,
) {
    if (!isSuspended) return

    var showSuspensionSheet by remember { mutableStateOf(false) }
    val resolvedReason = reason?.takeIf { it.isNotBlank() } ?: fallbackReason

    SuspendedAccountBanner(
        title = bannerTitle,
        reason = resolvedReason,
        onTap = { showSuspensionSheet = true },
        modifier = modifier,
        style = style
    )
    SuspendedAccountSupportSheet(
        isVisible = showSuspensionSheet,
        onDismissRequest = { showSuspensionSheet = false }
    )
}
