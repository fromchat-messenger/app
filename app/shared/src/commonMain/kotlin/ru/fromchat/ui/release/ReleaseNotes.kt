package ru.fromchat.ui.release

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.AppBuildInfo
import ru.fromchat.Res
import ru.fromchat.config.Settings
import ru.fromchat.legal.Markdown
import ru.fromchat.release_notes_dont_show_again
import ru.fromchat.release_notes_ok
import ru.fromchat.release_notes_title
import ru.fromchat.ui.components.ActionButton
import ru.fromchat.ui.components.ExpressiveIconFrame
import ru.fromchat.ui.components.Text

object ReleaseNotesPrompt {
    fun shouldShowAutomatically(): Boolean {
        if (Settings.releaseNotesPermanentlyHidden) return false
        if (AppBuildInfo.releaseNotesMarkdown.isBlank()) return false
        return Settings.lastAcknowledgedAppVersion != AppBuildInfo.version
    }

    fun applyDismissal(dontShowAgain: Boolean) {
        Settings.releaseNotesPermanentlyHidden = dontShowAgain
        Settings.lastAcknowledgedAppVersion = AppBuildInfo.version
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReleaseNotesDialog(
    onDismiss: () -> Unit,
) {
    val markdown = AppBuildInfo.releaseNotesMarkdown
    if (markdown.isBlank()) return

    var dontShowAgain by remember { mutableStateOf(Settings.releaseNotesPermanentlyHidden) }
    val contentPadding = 24.dp

    fun dismiss() {
        ReleaseNotesPrompt.applyDismissal(dontShowAgain)
        onDismiss()
    }

    Dialog(
        onDismissRequest = { dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .padding(contentPadding),
        ) {
            Column(Modifier.padding(contentPadding)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp),
                ) {
                    ExpressiveIconFrame(
                        icon = Icons.Outlined.NewReleases,
                        containerSize = 48.dp,
                        iconSize = 24.dp,
                        materialPolygon = MaterialShapes.SoftBurst,
                    )
                    Text(
                        text = stringResource(Res.string.release_notes_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }

                Markdown(
                    content = markdown,
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 16.dp),
                )

                val dontShowAgainInteraction = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .hoverable(dontShowAgainInteraction)
                        .clickable(
                            interactionSource = dontShowAgainInteraction,
                            indication = null,
                            onClick = { dontShowAgain = !dontShowAgain },
                        )
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = null,
                        interactionSource = dontShowAgainInteraction,
                    )
                    Text(
                        text = stringResource(Res.string.release_notes_dont_show_again),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    ActionButton(onClick = { dismiss() }) {
                        Text(stringResource(Res.string.release_notes_ok))
                    }
                }
            }
        }
    }
}
