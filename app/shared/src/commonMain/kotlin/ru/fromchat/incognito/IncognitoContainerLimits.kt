package ru.fromchat.incognito

/** Maximum logical size of the incognito encrypted container (5 GiB). */
internal object IncognitoContainerLimits {
    const val MAX_LOGICAL_BYTES: Long = 5L * 1024 * 1024 * 1024
}

internal class IncognitoContainerCapacityException(
    message: String,
) : Exception(message)
