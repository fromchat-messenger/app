package ru.fromchat.ui.profile

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.local.db.store.ProfileCache
import ru.fromchat.api.local.db.store.shouldHideUsername
import ru.fromchat.api.local.db.store.visibleDisplayName
import ru.fromchat.api.schema.user.profile.UserProfile
import ru.fromchat.deleted_account

fun UserProfile.isDeletedAccount(currentUserId: Int? = null): Boolean =
    deleted == true && id != currentUserId

fun UserProfile.isSuspendedAccount(currentUserId: Int? = null): Boolean =
    suspended == true && deleted != true && id != currentUserId

/** True when peers should see this profile as a deleted/hidden account. */
fun UserProfile.isRedactedPeerAccount(currentUserId: Int? = null): Boolean =
    id != currentUserId && (
        deleted == true ||
            isDeletedAccountUsername(username) ||
            (suspended == true && currentUserId != 1)
        )

fun isDeletedAccountUsername(username: String?): Boolean =
    username?.startsWith("#deleted") == true

/** True when [userId] should be shown as a deleted account to [currentUserId]. */
fun peerIsDeleted(
    userId: Int,
    currentUserId: Int? = null,
    deleted: Boolean? = null,
    username: String? = null,
    suspended: Boolean? = null,
): Boolean {
    if (userId <= 0 || userId == currentUserId) return false
    if (deleted == true) return true
    if (isDeletedAccountUsername(username)) return true
    // Suspended peers look deleted to everyone except admin (id 1).
    if (suspended == true && currentUserId != 1) return true
    ProfileCache.get(userId)?.let { profile ->
        if (profile.isRedactedPeerAccount(currentUserId)) return true
    }
    return false
}

suspend fun deletedUserDisplayName(): String =
    getString(Res.string.deleted_account)

@Composable
fun deletedUserDisplayNameForUi(): String =
    stringResource(Res.string.deleted_account)

suspend fun UserProfile.displayNameText(currentUserId: Int? = null): String {
    if (isRedactedPeerAccount(currentUserId)) {
        return deletedUserDisplayName()
    }
    return visibleDisplayName(currentUserId).orEmpty()
}

@Composable
fun UserProfile.displayNameForUi(currentUserId: Int? = null): String =
    if (isRedactedPeerAccount(currentUserId)) {
        deletedUserDisplayNameForUi()
    } else {
        visibleDisplayName(currentUserId).orEmpty()
    }

/** Display name for avatar initials/gradient only; never falls back to username. */
fun UserProfile.avatarLabelForInitials(currentUserId: Int? = null): String =
    if (isRedactedPeerAccount(currentUserId)) {
        ""
    } else {
        displayName?.trim().orEmpty()
    }
