package ru.fromchat.utils.settings

/** Durable app settings (survives incognito session). */
internal expect fun normalProfileSettings(): Settings

/** Ephemeral incognito settings volume (wiped when incognito ends). */
internal expect fun incognitoProfileSettings(): Settings

internal expect fun normalProfileSecureSettings(): Settings

internal expect fun incognitoProfileSecureSettings(): Settings