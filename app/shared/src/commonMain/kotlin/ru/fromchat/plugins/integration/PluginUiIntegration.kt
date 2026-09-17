package ru.fromchat.plugins.integration

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pr0gramm3r101.components.ListItem
import ru.fromchat.plugins.MenuItemType
import ru.fromchat.plugins.host.FeatureGate
import ru.fromchat.plugins.host.PluginHookDispatcher
import ru.fromchat.plugins.host.ui.PluginOverlayStore
import ru.fromchat.ui.components.Text

@Composable
fun PluginOverlayBanner(
    slot: String,
    modifier: Modifier = Modifier,
) {
    val storeOverlays = PluginOverlayStore.overlays
    val overlays = storeOverlays.filter { it.slot == slot }
    if (overlays.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        overlays.forEach { overlay ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = overlay.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        text = overlay.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun PluginSettingsMenuItems(
    showDividerBeforeFirst: Boolean,
    showDividerAfterLast: Boolean,
) {
    val items = PluginHookDispatcher.menuItemEntriesFor(MenuItemType.SETTINGS_ROW)
    items.forEachIndexed { index, (_, item) ->
        ListItem(
            headline = item.text,
            supportingText = item.subtext,
            leadingContent = { androidx.compose.material3.Icon(Icons.Default.Extension, contentDescription = null) },
            onClick = { PluginHookDispatcher.dispatchMenuItemClick(item.onClickKey) },
            divider = when {
                index < items.lastIndex -> true
                showDividerAfterLast -> true
                else -> false
            }.let { divider ->
                divider || (index == 0 && showDividerBeforeFirst)
            },
        )
    }
}

fun pluginFeatureEnabled(featureId: String, default: Boolean = true): Boolean =
    FeatureGate.isEnabled(featureId, default)
