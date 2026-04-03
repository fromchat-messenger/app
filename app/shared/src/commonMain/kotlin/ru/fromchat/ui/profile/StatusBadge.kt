package ru.fromchat.ui.profile

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.*
import ru.fromchat.api.ApiClient

@Composable
fun StatusBadge(
    verified: Boolean?,
    userId: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    var isSimilarToVerified by remember(userId, verified) { mutableStateOf(false) }
    val cdVerified = stringResource(Res.string.cd_verified_account)
    val cdSimilar = stringResource(Res.string.cd_similar_verified)

    LaunchedEffect(verified, userId, ApiClient.token) {
        if (verified == true) {
            isSimilarToVerified = false
            return@LaunchedEffect
        }
        if (userId == null || ApiClient.token == null) {
            isSimilarToVerified = false
            return@LaunchedEffect
        }
        val result = withContext(Dispatchers.Default) {
            runCatching { ApiClient.checkSimilarity(userId) }.getOrNull()
        }
        isSimilarToVerified = result?.isSimilar == true
    }

    when {
        verified == true -> Icon(
            imageVector = Icons.Filled.Verified,
            contentDescription = cdVerified,
            modifier = modifier
                .size(size)
                .semantics { contentDescription = cdVerified },
            tint = MaterialTheme.colorScheme.primary
        )
        isSimilarToVerified -> Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = cdSimilar,
            modifier = modifier
                .size(size)
                .semantics { contentDescription = cdSimilar },
            tint = Color(0xFFFFA000)
        )
        else -> {}
    }
}
