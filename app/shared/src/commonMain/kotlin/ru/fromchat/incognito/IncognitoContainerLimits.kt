package ru.fromchat.incognito

/** Maximum logical size of the incognito encrypted container (5 GiB). */
internal object IncognitoContainerLimits {
    const val MAX_LOGICAL_BYTES: Long = 5L * 1024 * 1024 * 1024

    /** Free this much logical space proactively before hitting the hard cap. */
    const val EVICTION_HEADROOM_BYTES: Long = 64L * 1024 * 1024
}

internal class IncognitoContainerCapacityException(
    message: String,
) : Exception(message)
