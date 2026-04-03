package ru.fromchat.ui.chat

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.Message
import ru.fromchat.*

private val userIdUsernamePattern = Regex("^User (\\d+)$")

/**
 * Resolves [Message.username] for display: localized «Вы», «Пользователь N», or server-provided name.
 */
@Composable
fun messageDisplayUsername(message: Message, currentUserId: Int?): String {
    if (currentUserId != null && message.user_id == currentUserId) {
        return stringResource(Res.string.message_sender_you)
    }
    val m = userIdUsernamePattern.matchEntire(message.username)
    if (m != null) {
        val id = m.groupValues[1].toIntOrNull()
        if (id != null) return stringResource(Res.string.user_fallback, id)
    }
    return message.username
}
