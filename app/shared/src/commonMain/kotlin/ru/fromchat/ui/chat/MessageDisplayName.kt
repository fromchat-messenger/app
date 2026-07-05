package ru.fromchat.ui.chat

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.db.store.ProfileCache
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.api.local.db.store.visibleUsername
import ru.fromchat.api.local.db.store.visibleDisplayName
import ru.fromchat.message_sender_you
import ru.fromchat.user_fallback

private val userIdUsernamePattern = Regex("^User (\\d+)$")

/**
 * Resolves [Message.username] for display: localized «Вы», «Пользователь N», or server-provided name.
 */
@Composable
fun messageDisplayUsername(message: Message, currentUserId: Int?): String {
    if (currentUserId != null && message.user_id == currentUserId) {
        return stringResource(Res.string.message_sender_you)
    }
    val cachedProfile = ProfileCache.get(message.user_id)
    val isCachedUserHidden = cachedProfile?.let {
        it.id != currentUserId && (it.deleted == true || it.suspended == true)
    } == true
    if (isCachedUserHidden) {
        return stringResource(Res.string.user_fallback, message.user_id)
    }
    val cachedUsername = cachedProfile?.visibleUsername(currentUserId)
    if (cachedUsername != null) return cachedUsername
    if (message.username.equals("deleted", ignoreCase = true)) {
        return stringResource(Res.string.user_fallback, message.user_id)
    }
    val m = userIdUsernamePattern.matchEntire(message.username)
    if (m != null) {
        val id = m.groupValues[1].toIntOrNull()
        if (id != null) return stringResource(Res.string.user_fallback, id)
    }
    return message.username
}

fun messageSenderProfilePicture(
    message: Message,
    currentUserId: Int? = ApiClient.user?.id,
): String? {
    if (currentUserId != null && message.user_id == currentUserId) {
        return message.profile_picture?.takeIf { it.isNotBlank() }
            ?: ApiClient.user?.profile_picture?.takeIf { it.isNotBlank() }
    }
    return message.profile_picture?.takeIf { it.isNotBlank() }
        ?: ProfileCache.get(message.user_id)?.profilePicture?.takeIf { it.isNotBlank() }
}

fun messageSenderAvatarLabel(
    message: Message,
    currentUserId: Int? = ApiClient.user?.id,
): String {
    if (currentUserId != null && message.user_id == currentUserId) {
        return ApiClient.user?.username?.takeIf { it.isNotBlank() }.orEmpty()
    }
    return message.username.trim().ifBlank {
        ProfileCache.get(message.user_id)?.visibleDisplayName(currentUserId).orEmpty()
    }
}
