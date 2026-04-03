package ru.fromchat.ui.profile

import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.Dispatchers
import ru.fromchat.Res
import ru.fromchat.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.fromchat.api.ApiClient

@Composable
fun VerifyButton(
    userId: Int,
    verified: Boolean,
    onVerificationChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (ApiClient.user?.id != 1) return

    var isVerifying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val labelVerify = stringResource(Res.string.verify)
    val labelUnverify = stringResource(Res.string.unverify)

    Button(
        onClick = {
            if (ApiClient.token == null || isVerifying) return@Button
            isVerifying = true
            scope.launch {
                val result = withContext(Dispatchers.Default) {
                    runCatching { ApiClient.verifyUser(userId) }.getOrNull()
                }
                isVerifying = false
                result?.verified?.let { onVerificationChange?.invoke(it) }
            }
        },
        modifier = modifier,
        enabled = !isVerifying
    ) {
        if (isVerifying) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(
                text = if (verified) labelUnverify else labelVerify
            )
        }
    }
}
