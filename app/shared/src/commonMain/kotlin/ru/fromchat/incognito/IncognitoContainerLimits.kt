package ru.fromchat.incognito

/** Maximum logical size of the incognito encrypted container (5 GiB). */
internal object IncognitoContainerLimits {
    private const val DEFAULT_MAX_LOGICAL_BYTES: Long = 5L * 1024 * 1024 * 1024

    /** Headless verification may lower the cap; production uses [DEFAULT_MAX_LOGICAL_BYTES]. */
    @Volatile
    var overrideMaxLogicalBytes: Long? = null

    val MAX_LOGICAL_BYTES: Long
        get() = overrideMaxLogicalBytes ?: DEFAULT_MAX_LOGICAL_BYTES

    /** Free this much logical space proactively before hitting the hard cap. */
    const val EVICTION_HEADROOM_BYTES: Long = 64L * 1024 * 1024
}

internal class IncognitoContainerCapacityException(
    message: String,
) : Exception(message)
