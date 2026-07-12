package ru.fromchat.ui.profile

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.fromchat.api.schema.user.profile.VerificationStatus

@Composable
fun DisplayName(
    displayName: String,
    verificationStatus: VerificationStatus?,
    modifier: Modifier = Modifier,
    textStyle: TextStyle,
    badgeSize: Dp = 16.dp,
    maxLines: Int = 1,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = displayName,
            style = textStyle,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )

        if (verificationStatus != null && verificationStatus != VerificationStatus.None) {
            Spacer(Modifier.width(4.dp))
            StatusBadge(
                verificationStatus = verificationStatus,
                size = badgeSize,
            )
        }
    }
}

