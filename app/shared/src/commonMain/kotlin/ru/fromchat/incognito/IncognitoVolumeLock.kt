package ru.fromchat.incognito

/** Guards [IncognitoVolumeFileSystem] across all targets (replaces JVM-only [synchronized]). */
internal expect fun <T> withIncognitoVolumeLock(block: () -> T): T
