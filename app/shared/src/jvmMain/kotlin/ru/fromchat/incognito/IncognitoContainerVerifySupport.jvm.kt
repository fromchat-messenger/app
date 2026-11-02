package ru.fromchat.incognito

/** Used by desktop `--verify-incognito` only. */
fun setIncognitoContainerMaxLogicalBytesForVerify(maxLogicalBytes: Long?) {
    IncognitoContainerLimits.overrideMaxLogicalBytes = maxLogicalBytes
}
