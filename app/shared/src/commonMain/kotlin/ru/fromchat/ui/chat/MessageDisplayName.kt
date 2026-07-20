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
import ru.fromchat.ui.profile.isDeletedAccountUsername
import ru.fromchat.ui.profile.isRedactedPeerAccount
import ru.fromchat.ui.profile.peerIsDeleted

/**
 * Resolves the sender label shown in message bubbles: localized «Вы», deleted user label,
 * cached/server display name, or login username only as a last resort.
 */
@Composable
fun messageDisplayUsername(message: Message, currentUserId: Int?): String {
    if (currentUserId != null && message.user_id == currentUserId) {
        return stringResource(Res.string.message_sender_you)
    }
    ProfileCache.get(message.user_id)?.let { profile ->
        if (profile.isRedactedPeerAccount(currentUserId)) {
            return deletedUserDisplayNameForUi()
        }
    }
    if (isDeletedAccountUsername(message.username)) {
        return deletedUserDisplayNameForUi()
    }
    ProfileCache.get(message.user_id)?.visibleDisplayName(currentUserId)
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }
    message.displayName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    if (message.username.equals("deleted", ignoreCase = true)) {
        return deletedUserDisplayNameForUi()
    }
    return message.username.trim()
}

fun messageSenderProfilePicture(
    message: Message,
    currentUserId: Int? = ApiClient.user?.id,
): String? {
    if (ProfileCache.get(message.user_id)?.isRedactedPeerAccount(currentUserId) == true) {
        return null
    }
    if (isDeletedAccountUsername(message.username)) {
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
    return message.displayName?.trim()?.takeIf { it.isNotEmpty() }.orEmpty()
}
