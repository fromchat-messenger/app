package ru.fromchat.ui.chat

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.db.store.ProfileCache
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.api.local.db.store.visibleDisplayName
import ru.fromchat.ui.profile.avatarLabelForInitials
import ru.fromchat.message_sender_you
import ru.fromchat.ui.profile.deletedUserDisplayNameForUi
import ru.fromchat.ui.profile.isDeletedAccount
import ru.fromchat.ui.profile.isDeletedAccountUsername
import ru.fromchat.ui.profile.peerIsDeleted

private val userIdUsernamePattern = Regex("^User (\\d+)$")

/**
 * Resolves [Message.username] for display: localized «Вы», deleted user label, or server-provided name.
 */
@Composable
fun messageDisplayUsername(message: Message, currentUserId: Int?): String {
    if (currentUserId != null && message.user_id == currentUserId) {
        return stringResource(Res.string.message_sender_you)
    }
    ProfileCache.get(message.user_id)?.let { profile ->
        if (profile.isDeletedAccount(currentUserId) || isDeletedAccountUsername(profile.username)) {
            return deletedUserDisplayNameForUi()
        }
    }
    if (isDeletedAccountUsername(message.username)) {
        return deletedUserDisplayNameForUi()
    }
    val cachedUsername = ProfileCache.get(message.user_id)?.visibleDisplayName(currentUserId)
    if (cachedUsername != null) return cachedUsername
    if (message.username.equals("deleted", ignoreCase = true)) {
        return deletedUserDisplayNameForUi()
    }
    val m = userIdUsernamePattern.matchEntire(message.username)
    if (m != null) {
        val id = m.groupValues[1].toIntOrNull()
        if (id != null) return deletedUserDisplayNameForUi()
    }
    return message.username
}

fun messageSenderProfilePicture(
    message: Message,
    currentUserId: Int? = ApiClient.user?.id,
): String? {
    if (ProfileCache.get(message.user_id)?.isDeletedAccount(currentUserId) == true) {
        return null
    }
    if (currentUserId != null && message.user_id == currentUserId) {
        return message.profile_picture?.takeIf { it.isNotBlank() }
            ?: ApiClient.user?.profile_picture?.takeIf { it.isNotBlank() }
    }
    return message.profile_picture?.takeIf { it.isNotBlank() }
        ?: ProfileCache.get(message.user_id)?.profilePicture?.takeIf { it.isNotBlank() }
}

fun messageSenderIsDeleted(message: Message, currentUserId: Int? = ApiClient.user?.id): Boolean =
    peerIsDeleted(
        userId = message.user_id,
        currentUserId = currentUserId,
        username = message.username,
    )

fun messageSenderAvatarLabel(
    message: Message,
    currentUserId: Int? = ApiClient.user?.id,
): String {
    if (messageSenderIsDeleted(message, currentUserId)) return ""
    ProfileCache.get(message.user_id)
        ?.avatarLabelForInitials(currentUserId)
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }
    if (currentUserId != null && message.user_id == currentUserId) {
        return ApiClient.user?.displayName?.trim()?.takeIf { it.isNotBlank() }.orEmpty()
    }
    return message.username.trim()
}
