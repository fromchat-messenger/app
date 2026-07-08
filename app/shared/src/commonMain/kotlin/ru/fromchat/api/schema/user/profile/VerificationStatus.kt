package ru.fromchat.api.schema.user.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class VerificationStatus {
    @SerialName("verified")
    Verified,

    @SerialName("warning")
    Warning,

    @SerialName("none")
    None,

    @SerialName("blocked")
    Blocked,
}

fun VerificationStatus?.orFromLegacyVerified(verified: Boolean?): VerificationStatus =
    this ?: when (verified) {
        true -> VerificationStatus.Verified
        false -> VerificationStatus.None
        null -> VerificationStatus.None
    }
