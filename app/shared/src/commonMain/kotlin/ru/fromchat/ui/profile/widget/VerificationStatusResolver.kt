package ru.fromchat.ui.profile.widget

import ru.fromchat.api.local.db.store.ProfileCache
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.api.schema.user.User
import ru.fromchat.api.schema.user.profile.UserProfile
import ru.fromchat.api.schema.user.profile.VerificationStatus
import ru.fromchat.api.schema.user.profile.orFromLegacyVerified

fun UserProfile.effectiveVerificationStatus(): VerificationStatus =
    verificationStatus.orFromLegacyVerified(verified)

fun User.effectiveVerificationStatus(): VerificationStatus =
    verificationStatus.orFromLegacyVerified(verified)

fun resolveVerificationStatus(
    userId: Int,
    message: Message? = null,
    user: User? = null,
): VerificationStatus? {
    ProfileCache.get(userId)?.let { cached ->
        if (cached.verificationStatus != null || cached.verified != null) {
            return cached.effectiveVerificationStatus()
        }
    }

    user?.let { u ->
        if (u.verificationStatus != null || u.verified != null) {
            return u.effectiveVerificationStatus()
        }
    }

    message?.verificationStatus?.let { return it }
    message?.verified?.let { return if (it) VerificationStatus.Verified else null }

    return null
}
