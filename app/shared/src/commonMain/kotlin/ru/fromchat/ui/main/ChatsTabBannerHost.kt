package ru.fromchat.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class ChatsTabBannerCandidate(
    val id: String,
    val priority: Int,
    val title: String,
    val message: String,
    val icon: String = "ⓘ",
    val onTap: () -> Unit = {},
    val onDismiss: (() -> Unit)? = null,
)

@Composable
fun ChatsTabBannerHost(
    candidates: List<ChatsTabBannerCandidate>,
    modifier: Modifier = Modifier
) {
    val dismissedByUser = remember { mutableStateMapOf<String, Boolean>() }

    val sortedVisibleCandidates = remember(candidates, dismissedByUser.size) {
        candidates
            .filter { candidate ->
                val canDismiss = candidate.onDismiss != null
                !canDismiss || dismissedByUser[candidate.id] != true
            }
            .sortedByDescending { it.priority }
    }
    val activeCandidate = sortedVisibleCandidates.firstOrNull()

    AnimatedVisibility(activeCandidate != null) {
        activeCandidate?.let { candidate ->
            ChatsTabBanner(
                candidate = candidate,
                modifier = modifier.padding(
                    horizontal = 12.dp,
                    vertical = 8.dp
                ),
                onDismiss = {
                    if (candidate.onDismiss != null) {
                        dismissedByUser[candidate.id] = true
                        candidate.onDismiss.invoke()
                    }
                }
            )
        }
    }
}

@Composable
private fun ChatsTabBanner(
    candidate: ChatsTabBannerCandidate,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    val canDismiss = candidate.onDismiss != null

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = candidate.onTap)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = candidate.icon,
                style = MaterialTheme.typography.titleMedium
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = candidate.title,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = candidate.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3
                )
            }
            if (canDismiss) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss banner"
                    )
                }
            }
        }
    }
}
