package ru.fromchat.ui.profile

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.schema.user.profile.VerificationStatus
import ru.fromchat.cd_similar_verified
import ru.fromchat.cd_verified_account
import ru.fromchat.cd_account_blocked

@Composable
fun StatusBadge(
    verificationStatus: VerificationStatus?,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
) {
    when (verificationStatus) {
        VerificationStatus.Verified -> Icon(
            imageVector = Icons.Filled.Verified,
            contentDescription = stringResource(Res.string.cd_verified_account),
            modifier = modifier.size(size),
            tint = MaterialTheme.colorScheme.primary,
        )

        VerificationStatus.Warning -> Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = stringResource(Res.string.cd_similar_verified),
            modifier = modifier.size(size),
            tint = Color(0xFFFFA000),
        )

        VerificationStatus.Blocked -> Icon(
            imageVector = Icons.Rounded.Block,
            contentDescription = stringResource(Res.string.cd_account_blocked),
            modifier = modifier.size(size),
            tint = MaterialTheme.colorScheme.error,
        )

        VerificationStatus.None, null -> Unit
    }
}
